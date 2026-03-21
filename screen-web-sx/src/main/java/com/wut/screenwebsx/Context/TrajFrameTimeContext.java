package com.wut.screenwebsx.Context;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import static com.wut.screencommonsx.Static.WebModuleStatic.TRAJ_RECORD_COND;

@Component
public class TrajFrameTimeContext {
    private static Long TRAJ_FRAME_RECORD_TIME = 0L;
    private static Boolean TRAJ_FRAME_RECORD_TIME_INIT_FLAG = false;

    @PostConstruct
    public void initTrajFrameTimeParams() {
        TRAJ_FRAME_RECORD_TIME = 0L;
        TRAJ_FRAME_RECORD_TIME_INIT_FLAG = false;
    }

    // 预设实时轨迹帧的消息队列分片为1,生产者(接收端)数据在发送端同步接收过程中也保证有序
    // 每次接收时间戳时判断是否满足发送条件即可
    public boolean recordTrajFrameRecordTime(long timestamp) {
        if (!TRAJ_FRAME_RECORD_TIME_INIT_FLAG) {
            TRAJ_FRAME_RECORD_TIME = timestamp;
            TRAJ_FRAME_RECORD_TIME_INIT_FLAG = true;
            return false;
        }
        if (timestamp - TRAJ_FRAME_RECORD_TIME >= TRAJ_RECORD_COND) {
            TRAJ_FRAME_RECORD_TIME = timestamp;
            return true;
        }
        return false;
    }

}
