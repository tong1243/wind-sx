package com.wut.screencommonsx.Util;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.logging.Logger;

import static com.wut.screencommonsx.Static.WebModuleStatic.TRAJ_RECORD_COND;

public class MessagePrintUtil {
    private static final Logger LOGGER = Logger.getLogger("ROOT");

    public static void printListenerReceive(String key, String data) {
        LOGGER.info("[SX LISTENER " + key.toUpperCase() + " RECEIVED FROM RX] " + data);
    }

    public static void printException(Exception e, String info) {
        LOGGER.warning("[CATCH EXCEPTION IN FUNCTION ---" + info + "--- ] " + ExceptionUtils.getStackTrace(e));
    }

    public static void printSuccessSendMessage(long timestamp) {
        LOGGER.info("[SUCCESS SEND TRAJ FRAME DATA TO CLIENT] " + timestamp);
    }

    public static void printErrorSendMessage(long timestamp) {
        LOGGER.warning("[TRY SEND TRAJ FRAME DATA BUT CLIENT NOT CONNECTED] " + timestamp);
    }

    public static void printTrajSendSummary(
            long timestamp,
            int sessionCount,
            long originalTrajToWHCount,
            long originalTrajToEZCount,
            int sendTrajToWHCount,
            int sendTrajToEZCount,
            long sendFrameToWHCount,
            long sendFrameToEZCount,
            int expireToWHCount,
            int expireToEZCount,
            int offlineToWHCount,
            int offlineToEZCount
    ) {
        LOGGER.info("[TRAJ SEND SUMMARY] ts=" + timestamp
                + ", session=" + sessionCount
                + ", originalTraj(WH/EZ)=" + originalTrajToWHCount + "/" + originalTrajToEZCount
                + ", sendTraj(WH/EZ)=" + sendTrajToWHCount + "/" + sendTrajToEZCount
                + ", sendFrame(WH/EZ)=" + sendFrameToWHCount + "/" + sendFrameToEZCount
                + ", expire(WH/EZ)=" + expireToWHCount + "/" + expireToEZCount
                + ", offline(WH/EZ)=" + offlineToWHCount + "/" + offlineToEZCount);
    }

    // Reserved debug log for full payload.
    public static void printTrajCarList(long timestamp, String resp) {
        LOGGER.info("[TRAJ FRAME DATA RECORD] (" + (timestamp - TRAJ_RECORD_COND) + "->" + timestamp + ") " + resp);
    }
}
