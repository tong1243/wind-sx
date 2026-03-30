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
public class TrajFrameDataContext1 {
    @Qualifier("trajFrameDataReceiveTaskAsyncPool")
    private final Executor trajFrameDataReceiveTaskAsyncPool;
    @Qualifier("trajFrameDataSendTaskAsyncPool")
    private final Executor trajFrameDataSendTaskAsyncPool;
    private final TrajFrameTimeContext trajFrameTimeContext;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public record TrajDirectionRecord(List<Long> listToWH, List<Long> listToEZ, List<Long> originalTrajIds) {};

    // ��¼���ն˴�������ʷ�켣�ź�����ʱ��
    @Getter
    private static final Map<Long, TrajStateModel> TRAJ_STATE_MAP = new ConcurrentHashMap<>();
    // �人�����ݷ���̬�켣
    @Getter
    private static final Map<Long, TrajInfoData> TRAJ_MAP_TO_EZ = new ConcurrentHashMap<>();
    // ���ݵ��人����̬�켣
    @Getter
    private static final Map<Long, TrajInfoData> TRAJ_MAP_TO_WH = new ConcurrentHashMap<>();

    @Autowired
    public TrajFrameDataContext1(Executor trajFrameDataReceiveTaskAsyncPool, TrajFrameTimeContext trajFrameTimeContext, Executor trajFrameDataSendTaskAsyncPool) {
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
            // ����ɸѡǰ�Ĺ켣����
            long trajToWHCount = originalTrajList.stream()
                    .filter(traj -> traj.getRoadDirect() == TRAJ_ROAD_DIRECT_TO_WH)
                    .count();
            long trajToEZCount = originalTrajList.stream()
                    .filter(traj -> traj.getRoadDirect() == TRAJ_ROAD_DIRECT_TO_EZ)
                    .count();
            // ɸѡ�켣���ݲ����µ� trajFrameModel
            List<Traj> filteredTrajList = filterTrajList(originalTrajList);
            if (!CollectionEmptyUtil.forList(filteredTrajList)) {
                // ������ǰʱ����Ĺ켣ʱ���,��ÿ���켣�첽����,������Ҫ���������ͬ��
                recordTrajFrameData(filteredTrajList).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            }
            if (trajFrameTimeContext.recordTrajFrameRecordTime(trajFrameModel.getTimestamp())) {
                // ����ʱ��������ݵ������첽��������ͬ��
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

    // ��¼�켣֡����ǰʱ�������Ӧ����ı���
    public void recordTrajToInfoDataList(Map<Long, TrajInfoData> trajInfoDataMap, Traj traj) {
        long trajId = traj.getTrajId();
        TrajInfoData trajInfoData = trajInfoDataMap.get(trajId);
            if (trajInfoData == null) {
                // �����ʱ����ڸù켣��û�б���¼
                // -> �ù켣����֮ǰ��ʱ�����û�г��ֹ�,����stateΪ0,��Ҫ��ӳ���ʵ��
                // -> �ù켣����֮ǰ��ʱ������Ѿ����ֹ�,����stateΪ1,����Ӹù켣�ŵļ�¼,����Ҫ��ӳ���ʵ��
                TrajStateModel trajStateModel = TRAJ_STATE_MAP.get(trajId);
                if (trajStateModel == null) {
                    trajInfoDataMap.put(trajId, DbModelTransformUtil.trajToInfoData(traj, TRAJ_FRAME_STATE_NEW));
                    TRAJ_STATE_MAP.put(trajId, new TrajStateModel(traj.getRoadDirect(), traj.getTimestamp(), TRAJ_FRAME_STATE_ONLINE));
                } else {
                    trajInfoDataMap.put(trajId, DbModelTransformUtil.trajToInfoData(traj, TRAJ_FRAME_STATE_ONLINE));
                    // ��ʱʱ��5min,������,��˿���ֻ��¼ÿ��ʱ�������һ�ε�ʱ���������
                    // ��¼��ʱ��������жϸù켣��Ӧ�ĳ���ģ���ڿͻ����Ƿ�Ӧ��ɾ�����ͷ���Դ
                    trajStateModel.setTimestamp(traj.getTimestamp());
                    // ÿ��ʱ������Ľ�β�������б�־λΪ����״̬,�������Ҫ�����޸�Ϊ���߹켣,��ֹ����Ϊ��Ч����
                    trajStateModel.setState(TRAJ_FRAME_STATE_ONLINE);
                }
            } else {
                // ��¼�켣֡���µ�״̬���ͻ��˶�ȡ
                trajInfoData.setTimestamp(traj.getTimestamp());
                trajInfoData.setLicense(traj.getCarId());
                trajInfoData.setPosition(DataParamParseUtil.getPositionStr(traj.getFrenetX()));
                trajInfoData.setSpeed(DataParamParseUtil.getRoundValue(traj.getSpeedX()));
                trajInfoData.getFrameList().add(DbModelTransformUtil.trajToFrameData(traj));
            }

    }

    // ÿ�η��͹켣����ǰ,����Ƿ��й���ʱ��û�м�¼�µĹ켣���ݵĹ켣��
    // ʧЧ�Ĺ켣�ŴӼ�¼����ɾ��,ͬʱ������Щ�켣�ŵ��б�,֪ͨǰ�˴���
    public TrajDirectionRecord flushExpireTrajId(TrajFrameModel model) {
        List<Traj> originalTrajList = model.getTrajList();

        // ��ȡ originalTrajList �еĹ켣 ID
        List<Long> originalTrajIds = originalTrajList.stream()
                .map(Traj::getTrajId)
                .collect(Collectors.toList());

        // ��ʼ����¼���󣬰��� originalTrajIds
        TrajDirectionRecord record = new TrajDirectionRecord(
                new ArrayList<>(),
                new ArrayList<>(),
                originalTrajIds // �� originalTrajIds ��ӵ���¼��
        );

        // ������ڹ켣
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

        // �� TRAJ_STATE_MAP ���Ƴ�������Ŀ
        readyToRemoveList.forEach(TRAJ_STATE_MAP::remove);

        return record;
    }

    // ÿ�η��͹켣����ǰ,����Ƿ��иü�¼ʱ�����û���κμ�¼�����߹켣
    // �����з����߹켣��״̬λ����Ϊ���߹켣;�����߹켣��״̬λ�޸�Ϊ�����ѷ���,����¼��Щ�켣���͸��ͻ���
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

    // ��ʱ�������ﵽ�涨��ˢ��ʱ��ʱ,����Ƿ�����ͻ��˽�������,��ͻ�����������
// �첽����ѹ�����չ켣����֡��ʱ��
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
                // ��ȡ���������ӵĻỰ
                ConcurrentHashMap<String, WebSocketSession> sessions = WebSocketSessionContext.getAllSessions();
                long sendFrameToWHCount = trajListToWH.stream()
                        .mapToLong(item -> CollectionEmptyUtil.forList(item.getFrameList()) ? 0 : item.getFrameList().size())
                        .sum();
                long sendFrameToEZCount = trajListToEZ.stream()
                        .mapToLong(item -> CollectionEmptyUtil.forList(item.getFrameList()) ? 0 : item.getFrameList().size())
                        .sum();
                long newTrajToWHCount = trajListToWH.stream().filter(item -> item.getState() == TRAJ_FRAME_STATE_NEW).count();
                long newTrajToEZCount = trajListToEZ.stream().filter(item -> item.getState() == TRAJ_FRAME_STATE_NEW).count();
                long onlineTrajToWHCount = trajListToWH.size() - newTrajToWHCount;
                long onlineTrajToEZCount = trajListToEZ.size() - newTrajToEZCount;
                MessagePrintUtil.printTrajSendSummary(
                        trajFrameModel.getTimestamp(),
                        sessions.size(),
                        originalTrajToWHCount,
                        originalTrajToEZCount,
                        trajListToWH.size(),
                        trajListToEZ.size(),
                        newTrajToWHCount,
                        newTrajToEZCount,
                        onlineTrajToWHCount,
                        onlineTrajToEZCount,
                        sendFrameToWHCount,
                        sendFrameToEZCount,
                        expireRecord.listToWH.size(),
                        expireRecord.listToEZ.size(),
                        offlineRecord.listToWH.size(),
                        offlineRecord.listToEZ.size()
                );
                for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
                    WebSocketSession session = entry.getValue();
                    // ����δ����/�Ѿ��ر�ʱ,��ʾ��Ϣ�޷�����
                    if (session == null || !session.isOpen()) {
                        MessagePrintUtil.printErrorSendMessage(trajFrameModel.getTimestamp());
                    } else {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
                        MessagePrintUtil.printSuccessSendMessage(trajFrameModel.getTimestamp());
                    }
                }
                // ������־���,�Ǳ�Ҫ�����Ӧ��ע��
                // MessagePrintUtil.printTrajCarList(timestamp, resp);
            } catch (Exception e) { MessagePrintUtil.printException(e, "sendTrajFrameData"); }
        }, trajFrameDataSendTaskAsyncPool);
    }

}
