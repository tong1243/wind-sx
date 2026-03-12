package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.Posture.*;
import com.wut.screencommonsx.Response.TimeRecordData;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DataParamParseUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screendbmysqlsx.Model.*;
import com.wut.screendbmysqlsx.Service.*;
import com.wut.screenwebsx.Config.DockingInterfaceConfig.Docking;
import com.wut.screenwebsx.Service.DataPreSubService.PostureDataPreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static com.wut.screencommonsx.Static.WebModuleStatic.*;

@Component
public class PostureWebService {
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
        String tableStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<Parameters> parametersList = parametersService.getListByDate(tableStr);
        PostureStatisticData statisticData = ModelTransformUtil.getPostureStatisticInstance();
        List<PostureFlowTypeData> flowTypeList = postureDataPreService.initFlowTypeDataList();
        if (parametersList != null) {
            recordParametersToStatisticData(statisticData, parametersList);
//            recordPostureToFlowType(flowTypeList, DataParamParseUtil.parsePostureComp(posture.getComp()));
        }
        return new PostureRealTimeDataResp(statisticData, flowTypeList);
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
        statisticData.setFlowToEZ(0.0);
        statisticData.setFlowToWH(DataParamParseUtil.getRoundValue(getAvgStream(parametersList)));
        statisticData.setSpeedToEZ(0.0);
        statisticData.setSpeedToWH(DataParamParseUtil.getRoundValue(getAvgSpeed(parametersList)));
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
