package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.Posture.*;
import com.wut.screencommonsx.Response.Traj.TrajInfoData;
import com.wut.screencommonsx.Response.TimeRecordData;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DataParamParseUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screendbmysqlsx.Model.*;
import com.wut.screendbmysqlsx.Service.*;
import com.wut.screenwebsx.Config.DockingInterfaceConfig.Docking;
import com.wut.screenwebsx.Context.TrajFrameDataContext;
import com.wut.screenwebsx.Service.DataPreSubService.PostureDataPreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.wut.screencommonsx.Static.WebModuleStatic.*;

@Component
public class PostureWebService {
    private static final int DIRECTION_TO_WH = 1;
    private static final int DIRECTION_TO_EZ = 2;
    private final PostureDataPreService postureDataPreService;
    private final TrajService trajService;
    private final BottleneckAreaStateService bottleneckAreaStateService;
    private final ParametersService parametersService;
    private final SecInfoService secInfoService;
    private final OriginalDataService originalDataService;
    private final CarEventService carEventService;
    private static final double KR = 3200;   // 调节参数，单位：辆每小时
    private static final double O = 0.3;    // 下游期望占有率
    private static final int T = 60;        // 匝道控制周期，单位秒
    private static final int S = 800;       // 入口匝道饱和流率
    private static int rkPrev = 650;        // 前一周期匝道调节率，初始值在 MATLAB 中为 650
    private static LimitSpeedResp limitSpeedResp = null;

    @Autowired
    public PostureWebService(PostureDataPreService postureDataPreService, TrajService trajService, BottleneckAreaStateService bottleneckAreaStateService, ParametersService parametersService, SecInfoService secInfoService, OriginalDataService originalDataService, CarEventService carEventService) {
        this.postureDataPreService = postureDataPreService;
        this.trajService = trajService;
        this.bottleneckAreaStateService = bottleneckAreaStateService;
        this.parametersService = parametersService;
        this.secInfoService = secInfoService;
        this.originalDataService = originalDataService;
        this.carEventService = carEventService;
    }

    @Docking
    public PostureRealTimeDataResp collectPostureRealTimeData(long timestamp) {
        PostureStatisticData statisticData = ModelTransformUtil.getPostureStatisticInstance();
        List<PostureFlowTypeData> flowTypeList = postureDataPreService.initFlowTypeDataList();
        recordInTransitTrajectoryToStatisticData(timestamp, statisticData);
//            recordPostureToFlowType(flowTypeList, DataParamParseUtil.parsePostureComp(posture.getComp()));
        return new PostureRealTimeDataResp(statisticData, flowTypeList);
    }

    private void recordInTransitTrajectoryToStatisticData(long timestamp, PostureStatisticData statisticData) {
        Map<Integer, Set<Long>> activeTrajByDirection = buildActiveTrajByDirection(timestamp);
        Set<Long> allActiveTrajIdSet = new HashSet<>();
        allActiveTrajIdSet.addAll(activeTrajByDirection.getOrDefault(DIRECTION_TO_WH, Collections.emptySet()));
        allActiveTrajIdSet.addAll(activeTrajByDirection.getOrDefault(DIRECTION_TO_EZ, Collections.emptySet()));

        Map<Long, Double> latestSpeedByTrajId = loadLatestSpeedByTrajId(allActiveTrajIdSet, timestamp);
        mergeSpeedFromLiveFrame(latestSpeedByTrajId, TrajFrameDataContext.getTRAJ_MAP_TO_WH());
        mergeSpeedFromLiveFrame(latestSpeedByTrajId, TrajFrameDataContext.getTRAJ_MAP_TO_EZ());

        double flowToWH = activeTrajByDirection.getOrDefault(DIRECTION_TO_WH, Collections.emptySet()).size();
        double flowToEZ = activeTrajByDirection.getOrDefault(DIRECTION_TO_EZ, Collections.emptySet()).size();
        Double speedToWH = calculateDirectionAverageSpeed(activeTrajByDirection.get(DIRECTION_TO_WH), latestSpeedByTrajId);
        Double speedToEZ = calculateDirectionAverageSpeed(activeTrajByDirection.get(DIRECTION_TO_EZ), latestSpeedByTrajId);

        statisticData.setFlowToEZ(DataParamParseUtil.getRoundValue(flowToEZ));
        statisticData.setFlowToWH(DataParamParseUtil.getRoundValue(flowToWH));
        statisticData.setSpeedToEZ(speedToEZ == null ? 0D : DataParamParseUtil.getRoundValue(speedToEZ));
        statisticData.setSpeedToWH(speedToWH == null ? 0D : DataParamParseUtil.getRoundValue(speedToWH));
        statisticData.setCongestionToEZ(null);
        statisticData.setCongestionToWH(null);
    }

    private Map<Integer, Set<Long>> buildActiveTrajByDirection(long timestamp) {
        Map<Integer, Set<Long>> activeTrajByDirection = new HashMap<>();
        activeTrajByDirection.put(DIRECTION_TO_WH, new HashSet<>());
        activeTrajByDirection.put(DIRECTION_TO_EZ, new HashSet<>());

        long lowerBound = Math.max(0L, timestamp - TRAJ_EXPIRE_TIMEOUT);
        Map<Long, com.wut.screenwebsx.Model.TrajStateModel> stateMap = TrajFrameDataContext.getTRAJ_STATE_MAP();
        for (Map.Entry<Long, com.wut.screenwebsx.Model.TrajStateModel> entry : stateMap.entrySet()) {
            Long trajId = entry.getKey();
            com.wut.screenwebsx.Model.TrajStateModel stateModel = entry.getValue();
            if (trajId == null || stateModel == null || stateModel.getTimestamp() < lowerBound) {
                continue;
            }
            int direction = stateModel.getDirection();
            if (direction == DIRECTION_TO_WH || direction == DIRECTION_TO_EZ) {
                activeTrajByDirection.get(direction).add(trajId);
            }
        }

        // 部分场景下状态表与实时帧存在短暂不同步，补充当前帧可见轨迹避免漏计。
        activeTrajByDirection.get(DIRECTION_TO_WH).addAll(TrajFrameDataContext.getTRAJ_MAP_TO_WH().keySet());
        activeTrajByDirection.get(DIRECTION_TO_EZ).addAll(TrajFrameDataContext.getTRAJ_MAP_TO_EZ().keySet());
        return activeTrajByDirection;
    }

    private Map<Long, Double> loadLatestSpeedByTrajId(Set<Long> activeTrajIdSet, long timestamp) {
        Map<Long, Double> latestSpeedByTrajId = new HashMap<>();
        if (activeTrajIdSet == null || activeTrajIdSet.isEmpty()) {
            return latestSpeedByTrajId;
        }

        long startTimestamp = Math.max(0L, timestamp - TRAJ_EXPIRE_TIMEOUT);
        List<String> suffixList = new ArrayList<>();
        suffixList.add(DateParamParseUtil.getDateTableStr(timestamp));
        String startSuffix = DateParamParseUtil.getDateTableStr(startTimestamp);
        if (!suffixList.contains(startSuffix)) {
            suffixList.add(startSuffix);
        }

        for (String suffix : suffixList) {
            List<Traj> rows;
            try {
                rows = trajService.getListByTimestampRange(suffix, startTimestamp, timestamp);
            } catch (Exception ignored) {
                continue;
            }
            if (CollectionEmptyUtil.forList(rows)) {
                continue;
            }

            Map<Long, Traj> latestPointByTrajId = new HashMap<>();
            for (Traj row : rows) {
                Long trajId = row.getTrajId();
                if (trajId == null || !activeTrajIdSet.contains(trajId)) {
                    continue;
                }
                Traj latest = latestPointByTrajId.get(trajId);
                if (latest == null || toLong(row.getTimestamp()) >= toLong(latest.getTimestamp())) {
                    latestPointByTrajId.put(trajId, row);
                }
            }

            for (Map.Entry<Long, Traj> entry : latestPointByTrajId.entrySet()) {
                Double speed = calcSpeedKmh(entry.getValue());
                if (speed != null) {
                    latestSpeedByTrajId.put(entry.getKey(), speed);
                }
            }
        }

        return latestSpeedByTrajId;
    }

    private void mergeSpeedFromLiveFrame(Map<Long, Double> latestSpeedByTrajId, Map<Long, TrajInfoData> liveFrameTrajMap) {
        if (liveFrameTrajMap == null || liveFrameTrajMap.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, TrajInfoData> entry : liveFrameTrajMap.entrySet()) {
            Long trajId = entry.getKey();
            TrajInfoData data = entry.getValue();
            if (trajId == null || data == null) {
                continue;
            }
            double speed = data.getSpeed();
            if (Double.isFinite(speed) && speed >= 0D && speed <= 220D) {
                latestSpeedByTrajId.put(trajId, speed);
            }
        }
    }

    private Double calculateDirectionAverageSpeed(Set<Long> trajIdSet, Map<Long, Double> latestSpeedByTrajId) {
        if (trajIdSet == null || trajIdSet.isEmpty() || latestSpeedByTrajId == null || latestSpeedByTrajId.isEmpty()) {
            return null;
        }
        double sum = 0D;
        int count = 0;
        for (Long trajId : trajIdSet) {
            Double speed = latestSpeedByTrajId.get(trajId);
            if (speed == null || speed < 0D || speed > 220D) {
                continue;
            }
            sum += speed;
            count++;
        }
        if (count == 0) {
            return null;
        }
        return sum / count;
    }

    private Double calcSpeedKmh(Traj traj) {
        if (traj == null) {
            return null;
        }
        Double speedX = traj.getSpeedX();
        Double speedY = traj.getSpeedY();
        if (speedX == null && speedY == null) {
            return null;
        }
        double vx = speedX == null ? 0D : speedX;
        double vy = speedY == null ? 0D : speedY;
        double vectorSpeed = Math.sqrt(vx * vx + vy * vy);
        double longitudinalSpeed = Math.abs(vx);
        double speed = vectorSpeed;
        if (vectorSpeed > 220D && longitudinalSpeed <= 220D) {
            speed = longitudinalSpeed;
        }
        if (!Double.isFinite(speed) || speed < 0D || speed > 220D) {
            return null;
        }
        return speed;
    }

    private long toLong(Object raw) {
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @Docking
    public PosturePeriodDataResp collectPosturePeriodData(long timestamp) {
        List<TimeRecordData> congestionRecordList = new ArrayList<>();
        congestionRecordList.add(new TimeRecordData("", timestamp, 0.0, 0.0));
        return new PosturePeriodDataResp(collectAvgStream(timestamp), collectAvgSpeed(timestamp), congestionRecordList);
    }

    @Docking
    public PosturePeriodDataResp collectPostureTargetData(TargetDataReq req) {
        TargetTimeModel targetTime = DateParamParseUtil.getTargetDataTime(req);
        List<TimeRecordData> congestionRecordList = new ArrayList<>();
        return new PosturePeriodDataResp(collectAvgStream(req.getTimestamp()), collectAvgSpeed(req.getTimestamp()), congestionRecordList);
    }

//    public void recordPostureToTimeRecordList( List<TimeRecordData> flowRecordList, List<TimeRecordData> speedRecordList) {
//        flowRecordList.addAll(flowRecordList);
//        speedRecordList.addAll(speedRecordList);
//    }

    public void recordPostureToStatisticData(PostureStatisticData statisticData, Posture posture) {
        statisticData.setFlowToEZ(DataParamParseUtil.getRoundValue(posture.getAvgQez()));
        statisticData.setFlowToWH(DataParamParseUtil.getRoundValue(posture.getAvgQwh()));
        statisticData.setSpeedToEZ(DataParamParseUtil.getRoundValue(posture.getAvgVez() * 3.6));
        statisticData.setSpeedToWH(DataParamParseUtil.getRoundValue(posture.getAvgVwh() * 3.6));
        statisticData.setCongestionToEZ(DataParamParseUtil.getRoundValue(posture.getAvgKez()));
        statisticData.setCongestionToWH(DataParamParseUtil.getRoundValue(posture.getAvgKwh()));
    }

    public void recordParametersToStatisticData(PostureStatisticData statisticData, List<Parameters> parametersList) {
        if (CollectionEmptyUtil.forList(parametersList)) {
            statisticData.setFlowToEZ(null);
            statisticData.setFlowToWH(null);
            statisticData.setSpeedToEZ(null);
            statisticData.setSpeedToWH(null);
            statisticData.setCongestionToEZ(null);
            statisticData.setCongestionToWH(null);
            return;
        }
        double avgStream = getAvgStream(parametersList);
        double avgSpeed = getAvgSpeed(parametersList);
        double avgUpSpeed = parametersList.stream().mapToDouble(Parameters::getUpSpeed).average().orElse(0D);
        double avgDownSpeed = parametersList.stream().mapToDouble(Parameters::getDownSpeed).average().orElse(0D);

        // 历史数据存在只填 speed 的场景，up/down 方向速度为空（0）时回退到总平均速度，避免方向值被固定为 0。
        double speedToWh = avgUpSpeed > 0D ? avgUpSpeed : avgSpeed;
        double speedToEz = avgDownSpeed > 0D ? avgDownSpeed : avgSpeed;

        statisticData.setFlowToEZ(DataParamParseUtil.getRoundValue(avgStream));
        statisticData.setFlowToWH(DataParamParseUtil.getRoundValue(avgStream));
        statisticData.setSpeedToEZ(DataParamParseUtil.getRoundValue(speedToEz));
        statisticData.setSpeedToWH(DataParamParseUtil.getRoundValue(speedToWh));
        statisticData.setCongestionToEZ(null);
        statisticData.setCongestionToWH(null);
//        statisticData.setCongestionToEZ(DataParamParseUtil.getRoundValue(posture.getAvgKez()));
//        statisticData.setCongestionToWH(DataParamParseUtil.getRoundValue(posture.getAvgKwh()));
    }

    public void recordPostureToFlowType(List<PostureFlowTypeData> flowTypeList, List<Integer> compList) {
        flowTypeList.stream().forEach(flowType -> {
            int indexToWH = flowType.getType() / 10 - 1;
            int indexToEZ = flowType.getType() / 10 - 1 + CAR_TYPE_COUNT;
            flowType.setCarNumToEZ(compList.get(indexToEZ));
            flowType.setCarNumToWH(compList.get(indexToWH));
            flowType.setFlowToEZ(compList.get(indexToEZ) * 60);
            flowType.setFlowToWH(compList.get(indexToWH) * 60);
        });
    }

    public void recordParametersToFlowType(List<PostureFlowTypeData> flowTypeList, List<Integer> compList) {
        flowTypeList.stream().forEach(flowType -> {
            int indexToWH = flowType.getType() / 10 - 1;
            int indexToEZ = flowType.getType() / 10 - 1 + CAR_TYPE_COUNT;
            flowType.setCarNumToEZ(compList.get(indexToEZ));
            flowType.setCarNumToWH(compList.get(indexToWH));
            flowType.setFlowToEZ(compList.get(indexToEZ) * 60);
            flowType.setFlowToWH(compList.get(indexToWH) * 60);
        });
    }

    public BottleneckAreaStateResp colletBottleneckAreaState(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        BottleneckAreaState latestOne = bottleneckAreaStateService.getLatestOne(tableDateStr);
        if (latestOne == null) {
            return null;
        }
        return new BottleneckAreaStateResp(latestOne.getId(), latestOne.getState(), DataParamParseUtil.getRoundValue(latestOne.getSpeed()), DataParamParseUtil.getRoundValue(latestOne.getStream()), latestOne.getMainStream(), latestOne.getRampStream(), DataParamParseUtil.getRoundValue(latestOne.getQueueLength()), DataParamParseUtil.getRoundValue(latestOne.getQueueDelayTime()));
    }

    public List<ParametersResp> collectParameters(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<Parameters> parametersRespList = parametersService.getListByDate(tableDateStr);
        if (parametersRespList == null || parametersRespList.size() == 0) {
            return parametersRespList.stream().map(parameters -> new ParametersResp(parameters.getTime(), parameters.getRoadId(), DataParamParseUtil.getRoundValue(parameters.getStream()), DataParamParseUtil.getRoundValue(parameters.getDensity()), DataParamParseUtil.getRoundValue(parameters.getSpeed()), DataParamParseUtil.getRoundValue(parameters.getTravelTime()), DataParamParseUtil.getRoundValue(parameters.getDelay()),
                    parameters.getState(), parameters.getTimeStamp(), DataParamParseUtil.getRoundValue(parameters.getUpSpeed()), DataParamParseUtil.getRoundValue(parameters.getDownSpeed()), DataParamParseUtil.getRoundValue(parameters.getUpDensity()), DataParamParseUtil.getRoundValue(parameters.getDownDensity()), DataParamParseUtil.getRoundValue(parameters.getRampStream()), parameters.getCarCount()
                    , 0, 0, 0, 0, 0, 0)).toList();
        }
        OriginalData originalData = originalDataService.getOneById(parametersRespList.get(0).getTime());
        return parametersRespList.stream().map(parameters -> new ParametersResp(parameters.getTime(), parameters.getRoadId(), DataParamParseUtil.getRoundValue(parameters.getStream()), DataParamParseUtil.getRoundValue(parameters.getDensity()), DataParamParseUtil.getRoundValue(parameters.getSpeed()), DataParamParseUtil.getRoundValue(parameters.getTravelTime()), DataParamParseUtil.getRoundValue(parameters.getDelay()),
                parameters.getState(), parameters.getTimeStamp(), DataParamParseUtil.getRoundValue(parameters.getUpSpeed()), DataParamParseUtil.getRoundValue(parameters.getDownSpeed()), DataParamParseUtil.getRoundValue(parameters.getUpDensity()), DataParamParseUtil.getRoundValue(parameters.getDownDensity()), DataParamParseUtil.getRoundValue(parameters.getRampStream()), parameters.getCarCount()
                , DataParamParseUtil.getRoundValue2(originalData.getBotAreaTravelTime()), DataParamParseUtil.getRoundValue2(originalData.getAccAreaTravelTime()), DataParamParseUtil.getRoundValue2(originalData.getBotAvgSpeed()), DataParamParseUtil.getRoundValue2(originalData.getAccAvgSpeed()), DataParamParseUtil.getRoundValue2(originalData.getBotAvgDelay()), DataParamParseUtil.getRoundValue2(originalData.getAccAvgDelay()))).toList();
    }

    public RoadNetworkResp collectRoadNetwork(long timestamp) {
        List<TunnelSecInfo> tunnelSecInfoList = secInfoService.getAllTunnelSecInfo();
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<Parameters> parametersRespList = parametersService.getListByDate(tableDateStr);

        // 构建一个映射，方便快速根据roadId获取对应的TunnelSecInfo
        Map<Integer, TunnelSecInfo> tunnelSecInfoMap = tunnelSecInfoList.stream()
                .collect(Collectors.toMap(TunnelSecInfo::getSid, tunnelSecInfo -> tunnelSecInfo));

        // 获取所有状态为3的Parameters对应的road
        String road = parametersRespList.stream()
                .filter(parameters -> parameters.getState() == 3)
                .map(Parameters::getRoadId)
                .map(tunnelSecInfoMap::get)
                .filter(Objects::nonNull)
                .map(TunnelSecInfo::getRoad)
                .findFirst() // 这里取第一个匹配的道路，如果需要全部可以改为collect(Collectors.joining(","))
                .orElse(null);

        // 获取所有状态为3的Parameters对应的拥堵道路
        String congestionRoad = parametersRespList.stream()
                .filter(parameters -> parameters.getState() == 3)
                .map(Parameters::getRoadId)
                .map(tunnelSecInfoMap::get)
                .filter(Objects::nonNull)
                .map(TunnelSecInfo::getRoad)
                .distinct()
                .collect(Collectors.joining(","));

        // 求平均速度
        double avgSpeed = parametersRespList.stream().mapToDouble(Parameters::getSpeed).average().orElse(0);
        // 求平均流量
        double avgStream = parametersRespList.stream().mapToDouble(Parameters::getStream).average().orElse(0);
        // 求平均排队长度
//        double avgQueueLength = parametersRespList.stream().mapToDouble(Parameters::getQueueLength).average().orElse(0);
        // 求平均延误
        double avgDelay = parametersRespList.stream().mapToDouble(Parameters::getDelay).average().orElse(0);
        // 求平均密度
        double avgDensity = parametersRespList.stream().mapToDouble(Parameters::getDensity).average().orElse(0);

        return new RoadNetworkResp(timestamp, road, DataParamParseUtil.getRoundValue(avgSpeed), DataParamParseUtil.getRoundValue(avgStream), 0, DataParamParseUtil.getRoundValue(avgDelay), DataParamParseUtil.getRoundValue(avgDensity), congestionRoad);
    }

    public LimitSpeedResp collectDefaultLimitSpeed() {
        List<LimitSpeedData> limitSpeedDataList = new ArrayList<>();
        List<TunnelSecInfo> tunnelSecInfoList = secInfoService.getAllTunnelSecInfo();
        for (int i = 0; i < tunnelSecInfoList.size(); i++) {
            LimitSpeedData limitSpeedData = new LimitSpeedData(tunnelSecInfoList.get(i).getSid(), 60);
            limitSpeedDataList.add(limitSpeedData);
        }
        LimitSpeedResp defaultLimitSpeedResp = new LimitSpeedResp(60, 60, limitSpeedDataList);
        return defaultLimitSpeedResp;
    }

    public LimitSpeedResp collectLimitSpeedPlan(long timestamp) {
        if (limitSpeedResp == null) {
            return collectDefaultLimitSpeed();
        }
        // 右侧限速方案五分钟检测一次拥堵
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<BottleneckAreaState> bottleneckAreaStateList = bottleneckAreaStateService.getListByDate(tableDateStr);
        // 计算平均速度
        double avgSpeed = bottleneckAreaStateList.stream().mapToDouble(BottleneckAreaState::getSpeed).average().orElse(0);
        // 计算平均密度
        double avgDensity = bottleneckAreaStateList.stream().mapToDouble(BottleneckAreaState::getStream).average().orElse(0);
        // 计算平均占有率
        double avgOccupancyRate = avgDensity * 4.3 / 1000;
        // 更新右侧限速方案
        updateTrafficControlLogic(avgSpeed, avgOccupancyRate, limitSpeedResp);
        return limitSpeedResp;
    }

    public LimitSpeedResp collectAccLimitSpeedPlan(long timestamp) {
        if (limitSpeedResp == null) {
            return collectDefaultLimitSpeed();
        }
        Parameters parameters = null;
        List<Parameters> parametersList = parametersService.getListByDate(DateParamParseUtil.getDateTableStr(timestamp));
        for (Parameters parameter : parametersList) {
            if (parameter.getRoadId() == 7) {
                parameters = parameter;
            }
        }
        if (parameters == null) {
            return limitSpeedResp;
        }
        if (EventWebService.collectEventInfoData(timestamp) != null) {
            updateAccidentTrafficControlLogic(parameters.getSpeed(), limitSpeedResp);
        }
        return limitSpeedResp;
    }

    private void updateTrafficControlLogic(double avgSpeed, double avgOccupancyRate, LimitSpeedResp limitSpeedResp) {
        int vsl1;
        int vsl2;
        int greenTime;
        if (avgSpeed >= 40) {   // 路段畅通
            vsl1 = 60;
            vsl2 = 60;
            greenTime = 60;
        } else if (avgSpeed >= 30 && avgSpeed < 40) {  // 路段缓行
            vsl1 = 60;
            vsl2 = 50;
            greenTime = 60;
        } else if (avgSpeed >= 20 && avgSpeed < 30) {  // 路段轻度拥堵
            vsl1 = 50;
            vsl2 = 40;
            // ALINEA 算法计算匝道绿灯配时
            rkPrev = rkPrev + (int) (KR * (O - avgOccupancyRate));      // 匝道调节率更新
            greenTime = (int) ((rkPrev * T) / S);                // 绿灯时长计算
            // 绿灯时长限制
            if (greenTime > 50) greenTime = 50;
            else if (greenTime < 20) greenTime = 20;
        } else {   // 路段重度拥堵
            vsl1 = 40;
            vsl2 = 30;
            // ALINEA 算法计算匝道绿灯配时
            rkPrev = rkPrev + (int) (KR * (O - avgOccupancyRate));      // 匝道调节率更新
            greenTime = (int) ((rkPrev * T) / S);                // 绿灯时长计算
            // 绿灯时长限制
            if (greenTime > 50) greenTime = 50;
            else if (greenTime < 20) greenTime = 20;
        }
        limitSpeedResp.getData().get(0).setSpeed(vsl1);
        limitSpeedResp.getData().get(1).setSpeed(vsl2);
        limitSpeedResp.setRampGreenLightTiming(greenTime);
    }

    private void updateAccidentTrafficControlLogic(double avgSpeed, LimitSpeedResp limitSpeedResp) {
        int vsl1;
        int vsl2;
        if (avgSpeed >= 40) {   // 路段畅通
            vsl1 = 60;
            vsl2 = 60;
        } else if (avgSpeed >= 30 && avgSpeed < 40) {  // 路段缓行
            vsl1 = 60;
            vsl2 = 50;
        } else if (avgSpeed >= 20 && avgSpeed < 30) {  // 路段轻度拥堵
            vsl1 = 50;
            vsl2 = 40;

        } else {   // 路段重度拥堵
            vsl1 = 40;
            vsl2 = 30;
        }
        limitSpeedResp.getData().get(4).setSpeed(vsl1);
        limitSpeedResp.getData().get(5).setSpeed(vsl2);
    }

    public LimitSpeedResp collectLimitSpeed() {
        if (limitSpeedResp != null) {
            return limitSpeedResp;
        } else {
            limitSpeedResp = collectDefaultLimitSpeed();
            return limitSpeedResp;
        }
    }

    public List<TimeRecordData> collectAvgSpeed(long timestamp) {
        List<TimeRecordData> timeRecordDataList = new ArrayList<>();
        List<Parameters> parametersList = parametersService.getAllListByDate(DateParamParseUtil.getDateTableStr(timestamp)); // 获取所有数据
        if (!CollectionEmptyUtil.forList(parametersList)) {
            // 按时间戳分组
            Map<Long, List<Parameters>> groupedByTimestamp = parametersList.stream()
                    .collect(Collectors.groupingBy(Parameters::getTimeStamp));
            for (Map.Entry<Long, List<Parameters>> entry : groupedByTimestamp.entrySet()) {
                long currentTimestamp = entry.getKey();
                List<Parameters> currentParametersList = entry.getValue();
                double avgSpeed = getAvgSpeed(currentParametersList);
                timeRecordDataList.add(new TimeRecordData(
                        DateParamParseUtil.timestampToHourMinute(currentTimestamp),
                        currentTimestamp,
                        0,
                        DataParamParseUtil.getRoundValue(avgSpeed)
                ));
            }
        }
        timeRecordDataList.sort(Comparator.comparingLong(TimeRecordData::getTimestamp));
        return timeRecordDataList;
    }

    public List<TimeRecordData> collectAvgStream(long timestamp) {
        List<TimeRecordData> timeRecordDataList = new ArrayList<>();
        List<Parameters> parametersList = parametersService.getAllListByDate(DateParamParseUtil.getDateTableStr(timestamp)); // 获取所有数据
        if (!CollectionEmptyUtil.forList(parametersList)) {
            // 按时间戳分组
            Map<Long, List<Parameters>> groupedByTimestamp = parametersList.stream()
                    .collect(Collectors.groupingBy(Parameters::getTimeStamp));
            for (Map.Entry<Long, List<Parameters>> entry : groupedByTimestamp.entrySet()) {
                long currentTimestamp = entry.getKey();
                List<Parameters> currentParametersList = entry.getValue();
                double avgStream = getAvgStream(currentParametersList);
                timeRecordDataList.add(new TimeRecordData(
                        DateParamParseUtil.timestampToHourMinute(currentTimestamp),
                        currentTimestamp,
                        0,
                        DataParamParseUtil.getRoundValue(avgStream)
                ));
            }
        }
        timeRecordDataList.sort(Comparator.comparingLong(TimeRecordData::getTimestamp));
        return timeRecordDataList;
    }

    public List<TimeRecordData> getCongestionData(long timestamp) {
        List<TimeRecordData> timeRecordDataList = new ArrayList<>();
        List<Parameters> parametersList = parametersService.getAllListByDate(DateParamParseUtil.getDateTableStr(timestamp)); // 获取所有数据
        if (!CollectionEmptyUtil.forList(parametersList)) {
            // 按时间戳分组
            Map<Long, List<Parameters>> groupedByTimestamp = parametersList.stream()
                    .collect(Collectors.groupingBy(Parameters::getTimeStamp));
            for (Map.Entry<Long, List<Parameters>> entry : groupedByTimestamp.entrySet()) {
                long currentTimestamp = entry.getKey();
                List<Parameters> currentParametersList = entry.getValue();
                double avgSpeed = getAvgSpeed(currentParametersList);
                timeRecordDataList.add(new TimeRecordData(
                        DateParamParseUtil.timestampToHourMinute(currentTimestamp),
                        currentTimestamp,
                        0,
                        DataParamParseUtil.getRoundValue2(avgSpeed / 60)
                ));
            }
        }
        timeRecordDataList.sort(Comparator.comparingLong(TimeRecordData::getTimestamp));
        return timeRecordDataList;
    }
    public List<TimeRecordData> getCongestionData(int sid,long timestamp) {
        List<TimeRecordData> timeRecordDataList = new ArrayList<>();
        List<Parameters> parametersList = parametersService.getAllListByDate(DateParamParseUtil.getDateTableStr(timestamp)); // 获取所有数据
        if (!CollectionEmptyUtil.forList(parametersList)) {
            // 按时间戳分组
            Map<Long, List<Parameters>> groupedByTimestamp = parametersList.stream()
                    .collect(Collectors.groupingBy(Parameters::getTimeStamp));
            for (Map.Entry<Long, List<Parameters>> entry : groupedByTimestamp.entrySet()) {
                long currentTimestamp = entry.getKey();
                List<Parameters> currentParametersList = entry.getValue();
                for (Parameters parameters : currentParametersList){
                    if (parameters.getRoadId() == sid){
                        double avgSpeed = parameters.getSpeed();
                        timeRecordDataList.add(new TimeRecordData(
                                DateParamParseUtil.timestampToHourMinute(currentTimestamp),
                                currentTimestamp,
                                0,
                                DataParamParseUtil.getRoundValue2(avgSpeed / 60)
                        ));
                    }
                }

            }
        }
        timeRecordDataList.sort(Comparator.comparingLong(TimeRecordData::getTimestamp));
        return timeRecordDataList;
    }

    public double getAvgSpeed(List<Parameters> parametersList) {
        return parametersList.stream().mapToDouble(Parameters::getSpeed).average().orElse(0);
    }

    public double getAvgStream(List<Parameters> parametersList) {
        return parametersList.stream().mapToDouble(Parameters::getStream).average().orElse(0);
    }

    public Parameters getSecStream(long timestamp, int sId) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        return parametersService.getSecStream(tableDateStr, sId);
    }

    public OriginalDataResp collectOriginalData(long timestamp) {
        OriginalData originalData = originalDataService.getOneByTime(timestamp);
        OriginalDataResp originalDataResp = new OriginalDataResp(
                originalData.getTimestamp(),
                DataParamParseUtil.getRoundValue2(originalData.getBotAreaTravelTime()),
                DataParamParseUtil.getRoundValue2(originalData.getAccAreaTravelTime()),
                DataParamParseUtil.getRoundValue2(originalData.getBotAvgSpeed()),
                DataParamParseUtil.getRoundValue2(originalData.getAccAvgSpeed()),
                DataParamParseUtil.getRoundValue2(originalData.getBotAvgDelay()),
                DataParamParseUtil.getRoundValue2(originalData.getAccAvgDelay())
        );
        return originalDataResp;
    }

    public CongestionDataResp collectCongestionData(long timestamp) {
        List<Parameters> parametersList = parametersService.getListByDate(DateParamParseUtil.getDateTableStr(timestamp));
        CongestionDataResp congestionDataResp = new CongestionDataResp(
                0,
                DataParamParseUtil.getRoundValue2(getAvgSpeed(parametersList) / 60),
                getCongestionData(timestamp)
        );
        return congestionDataResp;
    }

}
