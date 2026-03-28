package com.wut.screenwebsx.Context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screencommonsx.Response.Traj.TrajCarStatisticData;
import com.wut.screencommonsx.Response.Traj.TrajDataResp;
import com.wut.screencommonsx.Response.Traj.TrajInfoData;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DataParamParseUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.MessagePrintUtil;
import com.wut.screendbmysqlsx.Model.Traj;
import com.wut.screendbmysqlsx.Util.DbModelTransformUtil;
import com.wut.screenwebsx.Model.TrajFrameModel;
import com.wut.screenwebsx.Model.TrajStateModel;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.wut.screencommonsx.Static.WebModuleStatic.*;

@Component
public class TrajFrameDataContext {
    @Qualifier("trajFrameDataReceiveTaskAsyncPool")
    private final Executor trajFrameDataReceiveTaskAsyncPool;
    @Qualifier("trajFrameDataSendTaskAsyncPool")
    private final Executor trajFrameDataSendTaskAsyncPool;
    private final TrajFrameTimeContext trajFrameTimeContext;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public record TrajDirectionRecord(List<Long> listToWH, List<Long> listToEZ, List<Long> originalTrajIds) {};

    // 记录接收端传来的历史轨迹号和最新时间
    @Getter
    private static final Map<Long, TrajStateModel> TRAJ_STATE_MAP = new ConcurrentHashMap<>();
    // 武汉到鄂州方向动态轨迹
    @Getter
    private static final Map<Long, TrajInfoData> TRAJ_MAP_TO_EZ = new ConcurrentHashMap<>();
    // 鄂州到武汉方向动态轨迹
    @Getter
    private static final Map<Long, TrajInfoData> TRAJ_MAP_TO_WH = new ConcurrentHashMap<>();

    @Autowired
    public TrajFrameDataContext(Executor trajFrameDataReceiveTaskAsyncPool, TrajFrameTimeContext trajFrameTimeContext, Executor trajFrameDataSendTaskAsyncPool) {
        this.trajFrameDataReceiveTaskAsyncPool = trajFrameDataReceiveTaskAsyncPool;
        this.trajFrameTimeContext = trajFrameTimeContext;
        this.trajFrameDataSendTaskAsyncPool = trajFrameDataSendTaskAsyncPool;
    }

    @PostConstruct
    public void initTrajFrameData() {
        TRAJ_STATE_MAP.clear();
        TRAJ_MAP_TO_EZ.clear();
        TRAJ_MAP_TO_WH.clear();
    }

    @KafkaListener(topics = "traj", groupId = "group-traj")
    public void trajFrameDataListener(List<ConsumerRecord> records, Acknowledgment ack){
        for (ConsumerRecord record : records) {
            String data = record.value().toString();
            storeTrajFrameData(data);
//            MessagePrintUtil.printListenerReceive(TOPIC_NAME_TRAJ, data);
        }
        ack.acknowledge();
    }

    public void storeTrajFrameData(String data) {
        try {
            TrajFrameModel trajFrameModel = objectMapper.readValue(data, TrajFrameModel.class);
            List<Traj> originalTrajList = trajFrameModel.getTrajList();
            // 计算筛选前的轨迹数量
            long trajToWHCount = originalTrajList.stream()
                    .filter(traj -> traj.getRoadDirect() == TRAJ_ROAD_DIRECT_TO_WH)
                    .count();
            long trajToEZCount = originalTrajList.stream()
                    .filter(traj -> traj.getRoadDirect() == TRAJ_ROAD_DIRECT_TO_EZ)
                    .count();
            // 筛选轨迹数据并更新到 trajFrameModel
            List<Traj> filteredTrajList = filterTrajList(originalTrajList);
            if (!CollectionEmptyUtil.forList(filteredTrajList)) {
                // 解析当前时间戳的轨迹时间戳,对每个轨迹异步进行,最终需要对任务进行同步
                recordTrajFrameData(filteredTrajList).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            }
            if (trajFrameTimeContext.recordTrajFrameRecordTime(trajFrameModel.getTimestamp())) {
                // 发送时间戳及数据的任务异步进行无需同步
                TrajDirectionRecord expireRecord = flushExpireTrajId(trajFrameModel);
                TrajDirectionRecord offlineRecord = flushOfflineTrajId();
                asyncSendTrajFrameData(
                        trajFrameModel,
                        trajToWHCount,
                        trajToEZCount,
                        expireRecord,
                        offlineRecord,
                        List.copyOf(TRAJ_MAP_TO_WH.values()),
                        List.copyOf(TRAJ_MAP_TO_EZ.values())
                ).thenRunAsync(() -> {});
                TRAJ_MAP_TO_EZ.clear();
                TRAJ_MAP_TO_WH.clear();
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "storeTrajFrameData"); }
    }

    private List<Traj> filterTrajList(List<Traj> trajList) {
        List<Traj> filteredTrajList = trajList.stream().filter(traj ->
//                        traj.getFrenetX() >= 1700 && traj.getFrenetX() <= 2000
//                        || (traj.getFrenetX() >= 1400 && traj.getFrenetX() <= 1450)
                          traj.getFrenetX() >= 1300 && traj.getFrenetX() <= 1500
//                                || (traj.getFrenetX() >= 2370 && traj.getFrenetX() <= 5300)
                                  || (traj.getFrenetX() >= 2300 && traj.getFrenetX() <= 3000)
                                  || (traj.getFrenetX() >= 4200 && traj.getFrenetX() <= 5300)
//                        || (traj.getFrenetX() >= 3000 && traj.getFrenetX() <= 5300)
        ).toList();
        return filteredTrajList;
    }

    private boolean isFilteredTraj(Traj traj) {
        if (
//                traj.getFrenetX() >= 1700 && traj.getFrenetX() <= 2000
//                || (traj.getFrenetX() >= 2370 && traj.getFrenetX() <= 2570)
//                || (traj.getFrenetX() >= 2650 && traj.getFrenetX() <= 3000)
//                || (traj.getFrenetX() >= 5000 && traj.getFrenetX() <= 5300)
                        traj.getFrenetX() >= 1300 && traj.getFrenetX() <= 1500
                        || (traj.getFrenetX() >= 2370 && traj.getFrenetX() <= 3000)
                        || (traj.getFrenetX() >= 4600 && traj.getFrenetX() <= 5300)
        ) ;
        return true;
    }
    private boolean isMainLine(Integer lane) {
        if (lane >= 1 && lane <= 3){
            return true;
        }
        return false;
    }

    public CompletableFuture<Void> recordTrajFrameData(List<Traj> trajList) {
        List<CompletableFuture<Void>> recordTrajTask = trajList.stream().map(traj -> {
            return switch (traj.getRoadDirect()) {
                case TRAJ_ROAD_DIRECT_TO_EZ -> CompletableFuture.runAsync(() -> recordTrajToInfoDataList(TRAJ_MAP_TO_EZ, traj), trajFrameDataReceiveTaskAsyncPool);
                case TRAJ_ROAD_DIRECT_TO_WH -> CompletableFuture.runAsync(() -> recordTrajToInfoDataList(TRAJ_MAP_TO_WH, traj), trajFrameDataReceiveTaskAsyncPool);
                default -> null;
            };
        }).filter(Objects::nonNull).toList();
        return CompletableFuture.allOf(recordTrajTask.toArray(CompletableFuture[]::new));
    }

    // 记录轨迹帧到当前时间区间对应方向的表中
    public void recordTrajToInfoDataList(Map<Long, TrajInfoData> trajInfoDataMap, Traj traj) {
        long trajId = traj.getTrajId();
        TrajInfoData trajInfoData = trajInfoDataMap.get(trajId);
            if (trajInfoData == null) {
                // 如果该时间段内该轨迹号没有被记录
                // -> 该轨迹号在之前的时间段内没有出现过,则置state为0,需要添加车辆实体
                // -> 该轨迹号在之前的时间段内已经出现过,则置state为1,并添加该轨迹号的记录,不需要添加车辆实体
                TrajStateModel trajStateModel = TRAJ_STATE_MAP.get(trajId);
                if (trajStateModel == null) {
                    trajInfoDataMap.put(trajId, DbModelTransformUtil.trajToInfoData(traj, TRAJ_FRAME_STATE_NEW));
                    TRAJ_STATE_MAP.put(trajId, new TrajStateModel(traj.getRoadDirect(), traj.getTimestamp(), TRAJ_FRAME_STATE_ONLINE));
                } else {
                    trajInfoDataMap.put(trajId, DbModelTransformUtil.trajToInfoData(traj, TRAJ_FRAME_STATE_ONLINE));
                    // 超时时间5min,差距过大,因此可以只记录每个时间区间第一次的时间戳简化运算
                    // 记录的时间戳用于判断该轨迹对应的车辆模型在客户端是否应该删除以释放资源
                    trajStateModel.setTimestamp(traj.getTimestamp());
                    // 每个时间区间的结尾会置所有标志位为离线状态,因此这里要重新修改为在线轨迹,防止被视为无效数据
                    trajStateModel.setState(TRAJ_FRAME_STATE_ONLINE);
                }
            } else {
                // 记录轨迹帧最新的状态供客户端读取
                trajInfoData.setTimestamp(traj.getTimestamp());
                trajInfoData.setLicense(traj.getCarId());
                trajInfoData.setPosition(DataParamParseUtil.getPositionStr(traj.getFrenetX()));
                trajInfoData.setSpeed(DataParamParseUtil.getRoundValue(traj.getSpeedX()));
                trajInfoData.getFrameList().add(DbModelTransformUtil.trajToFrameData(traj));
            }

    }

    // 每次发送轨迹数据前,检查是否有过长时间没有记录新的轨迹数据的轨迹号
    // 失效的轨迹号从记录表中删除,同时返回这些轨迹号的列表,通知前端处理
    public TrajDirectionRecord flushExpireTrajId(TrajFrameModel model) {
        List<Traj> originalTrajList = model.getTrajList();

        // 提取 originalTrajList 中的轨迹 ID
        List<Long> originalTrajIds = originalTrajList.stream()
                .map(Traj::getTrajId)
                .collect(Collectors.toList());

        // 初始化记录对象，包含 originalTrajIds
        TrajDirectionRecord record = new TrajDirectionRecord(
                new ArrayList<>(),
                new ArrayList<>(),
                originalTrajIds // 将 originalTrajIds 添加到记录中
        );

        // 处理过期轨迹
        List<Long> readyToRemoveList = TRAJ_STATE_MAP.entrySet().stream()
                .filter(entry -> entry.getValue().getTimestamp() <= (model.getTimestamp() - TRAJ_EXPIRE_TIMEOUT))
                .peek(entry -> {
                    switch (entry.getValue().getDirection()) {
                        case TRAJ_ROAD_DIRECT_TO_WH -> record.listToWH.add(entry.getKey());
                        case TRAJ_ROAD_DIRECT_TO_EZ -> record.listToEZ.add(entry.getKey());
                    }
                })
                .map(Map.Entry::getKey)
                .toList();

        // 从 TRAJ_STATE_MAP 中移除过期条目
        readyToRemoveList.forEach(TRAJ_STATE_MAP::remove);

        return record;
    }

    // 每次发送轨迹数据前,检查是否有该记录时间段内没有任何记录的离线轨迹
    // 将所有非离线轨迹的状态位重置为离线轨迹;将离线轨迹的状态位修改为离线已发送,并记录这些轨迹发送给客户端
    public TrajDirectionRecord flushOfflineTrajId() {
        List<Long> originalTrajIds = List.of();
        TrajDirectionRecord record = new TrajDirectionRecord(new ArrayList<>(), new ArrayList<>(), originalTrajIds);
        TRAJ_STATE_MAP.entrySet().stream().forEach(entry -> {
            TrajStateModel trajStateModel = entry.getValue();
            if (trajStateModel.getState() == TRAJ_FRAME_STATE_OFFLINE) {
                switch (trajStateModel.getDirection()) {
                    case TRAJ_ROAD_DIRECT_TO_WH -> record.listToWH.add(entry.getKey());
                    case TRAJ_ROAD_DIRECT_TO_EZ -> record.listToEZ.add(entry.getKey());
                }
                trajStateModel.setState(TRAJ_FRAME_STATE_MESSAGE);
            } else if (trajStateModel.getState() == TRAJ_FRAME_STATE_ONLINE) {
                trajStateModel.setState(TRAJ_FRAME_STATE_OFFLINE);
            }
        });
        return record;
    }

    // 当时间戳间隔达到规定的刷新时间时,检查是否已与客户端建立连接,向客户端推送数据
// 异步操作压缩接收轨迹数据帧的时间
    public CompletableFuture<Void> asyncSendTrajFrameData(TrajFrameModel trajFrameModel, long originalTrajToWHCount, long originalTrajToEZCount, TrajDirectionRecord expireRecord, TrajDirectionRecord offlineRecord, List<TrajInfoData> trajListToWH, List<TrajInfoData> trajListToEZ) {
        return CompletableFuture.runAsync(() -> {
            TrajCarStatisticData statisticData = new TrajCarStatisticData(
                    trajFrameModel.getCarToWH(),
                    trajFrameModel.getCarToEZ(),
                    (int)originalTrajToWHCount,
                    (int)originalTrajToEZCount
            );
            TrajDataResp data = new TrajDataResp(
                    trajFrameModel.getTimestamp(),
                    DateParamParseUtil.getDateTimePickerStr(trajFrameModel.getTimestamp()),
                    statisticData,
                    trajListToWH,
                    trajListToEZ,
                    expireRecord.listToWH,
                    expireRecord.listToEZ,
                    offlineRecord.listToWH,
                    offlineRecord.listToEZ
            );
            try {
                // 获取所有已连接的会话
                ConcurrentHashMap<String, WebSocketSession> sessions = WebSocketSessionContext.getAllSessions();
                long sendFrameToWHCount = trajListToWH.stream()
                        .mapToLong(item -> CollectionEmptyUtil.forList(item.getFrameList()) ? 0 : item.getFrameList().size())
                        .sum();
                long sendFrameToEZCount = trajListToEZ.stream()
                        .mapToLong(item -> CollectionEmptyUtil.forList(item.getFrameList()) ? 0 : item.getFrameList().size())
                        .sum();
                MessagePrintUtil.printTrajSendSummary(
                        trajFrameModel.getTimestamp(),
                        sessions.size(),
                        originalTrajToWHCount,
                        originalTrajToEZCount,
                        trajListToWH.size(),
                        trajListToEZ.size(),
                        sendFrameToWHCount,
                        sendFrameToEZCount,
                        expireRecord.listToWH.size(),
                        expireRecord.listToEZ.size(),
                        offlineRecord.listToWH.size(),
                        offlineRecord.listToEZ.size()
                );
                for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
                    WebSocketSession session = entry.getValue();
                    // 连接未建立/已经关闭时,提示消息无法发送
                    if (session == null || !session.isOpen()) {
                        MessagePrintUtil.printErrorSendMessage(trajFrameModel.getTimestamp());
                    } else {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
                        MessagePrintUtil.printSuccessSendMessage(trajFrameModel.getTimestamp());
                    }
                }
                // 测试日志输出,非必要情况下应当注释
                // MessagePrintUtil.printTrajCarList(timestamp, resp);
            } catch (Exception e) { MessagePrintUtil.printException(e, "sendTrajFrameData"); }
        }, trajFrameDataSendTaskAsyncPool);
    }

}
