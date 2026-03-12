package com.wut.screencommonsx.Util;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Request.TargetDataReq;
import org.apache.logging.log4j.message.Message;
import org.springframework.util.function.SupplierUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_TARGET_TIME_OFFSET;
import static com.wut.screencommonsx.Static.WebModuleStatic.*;

public class DateParamParseUtil {
    public static String getAlignmentNumStr(int num) {
        return num < 10 ? ("0" + num) : Integer.toString(num);
    }

    public static String getDateTimePickerStr(long timestamp) {
        LocalDateTime datetime = new Date(timestamp).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return datetime.getYear() + DATE_STR_SEPARATOR +
                getAlignmentNumStr(datetime.getMonthValue()) + DATE_STR_SEPARATOR +
                getAlignmentNumStr(datetime.getDayOfMonth()) + DATETIME_STR_SEPARATOR +
                getAlignmentNumStr(datetime.getHour()) + TIME_STR_SEPARATOR +
                getAlignmentNumStr(datetime.getMinute()) + TIME_STR_SEPARATOR +
                getAlignmentNumStr(datetime.getSecond());
    }

    public static String getDateTableStr(long timestamp) {
        LocalDateTime datetime = new Date(timestamp).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return datetime.getYear() +
                getAlignmentNumStr(datetime.getMonthValue()) +
                getAlignmentNumStr(datetime.getDayOfMonth());
    }

    public static String getDateTableStrByDate(String dateStr) {
        return dateStr.replaceAll(DATE_STR_SEPARATOR, "");
    }

    public static String getDateByDateTableStr(String tableStr) {
        return tableStr.substring(0, 4) + DATE_STR_SEPARATOR +
                tableStr.substring(4, 6) + DATE_STR_SEPARATOR +
                tableStr.substring(6, 8);
    }

    public static String getTimeDataStr(long timestamp) {
        LocalDateTime datetime = new Date(timestamp).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return getAlignmentNumStr(datetime.getHour()) + TIME_STR_SEPARATOR +
                getAlignmentNumStr(datetime.getMinute()) + TIME_STR_SEPARATOR +
                getAlignmentNumStr(datetime.getSecond());
    }

    public static String timestampToHourMinute(long timestamp) {
        // 将时间戳转换为 LocalDateTime
        LocalDateTime datetime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        );

        // 格式化为 HH:mm
        return DateTimeFormatter.ofPattern("HH:mm").format(datetime);
    }

    public static String getEventDateTimeDataStr(long timestamp) {
        LocalDateTime datetime = new Date(timestamp).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return getAlignmentNumStr(datetime.getYear() % 100) + EVENT_DATE_SEPARATOR +
                getAlignmentNumStr(datetime.getMonthValue()) + EVENT_DATE_SEPARATOR +
                getAlignmentNumStr(datetime.getDayOfMonth()) + DATETIME_STR_SEPARATOR + getTimeDataStr(timestamp);
    }

    public static long getDateTimestamp(String tableDate, String date, String time) {
        if (Objects.equals(time, DEFAULT_DATETIME_TARGET)) { return 0L; }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date dateTarget = Objects.equals(date, DEFAULT_DATETIME_TARGET)
                    ? dateFormat.parse(getDateByDateTableStr(tableDate) + DATETIME_STR_SEPARATOR + time)
                    : dateFormat.parse(date + DATETIME_STR_SEPARATOR + time);
            return dateTarget.getTime() + TABLE_TARGET_TIME_OFFSET;
        } catch (Exception e) { MessagePrintUtil.printException(e, "getDateTimestamp"); }
        return 0L;
    }

    public static TargetTimeModel getTargetDataTime(TargetDataReq req) {
        String tableDateStr = Objects.equals(req.getDateTarget(), DEFAULT_DATETIME_TARGET)
                ? getDateTableStr(req.getTimestamp())
                : getDateTableStrByDate(req.getDateTarget());
        return new TargetTimeModel(
                tableDateStr,
                getDateTimestamp(tableDateStr, req.getDateTarget(), req.getTimeStartTarget()),
                getDateTimestamp(tableDateStr, req.getDateTarget(), req.getTimeEndTarget())
        );
    }

    public static TargetTimeModel getRadarRealTimeTargetDataTime(TargetDataReq req) {
        String tableDateStr = Objects.equals(req.getDateTarget(), DEFAULT_DATETIME_TARGET)
                ? getDateTableStr(req.getTimestamp())
                : getDateTableStrByDate(req.getDateTarget());
        long timestampRight = getDateTimestamp(tableDateStr, req.getDateTarget(), req.getTimeStartTarget());
        long timestampLeft = timestampRight - RADAR_REAL_TIME_SPILT_TIMEOUT;
        return new TargetTimeModel(tableDateStr, timestampLeft, timestampRight);
    }

}
