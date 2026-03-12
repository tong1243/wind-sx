package com.wut.screencommonsx.Static;

import com.google.common.collect.Range;
import com.wut.screencommonsx.Model.CarTypeModel;

public class WebModuleStatic {
    public static final String CORS_MAPPING = "/**";
    public static final String CORS_HEADERS = "*";
    public static final String CORS_METHODS = "*";
    public static final String CORS_ORIGIN_PATTERNS = "*";
    public static final int CORS_MAX_AGE = 3600;
    public static final String FRONT_TRAJ_SESSION_KEY = "front_traj";
    public static final long TRAJ_RECORD_COND = 4000;
    public static final int TRAJ_ROAD_DIRECT_TO_WH = 1;
    public static final int TRAJ_ROAD_DIRECT_TO_EZ = 2;
    public static final String DATE_STR_SEPARATOR = "-";
    public static final String TIME_STR_SEPARATOR = ":";
    public static final String DATETIME_STR_SEPARATOR = " ";
    public static final String EVENT_DATE_SEPARATOR = "/";
    public static final int EVENT_TYPE_AGAINST = 1;
    public static final int EVENT_TYPE_PARKING = 2;
    public static final int EVENT_TYPE_SLOW = 3;
    public static final int EVENT_TYPE_FAST = 4;
    public static final int EVENT_TYPE_OCCUPY = 5;
    public static final int EVENT_STATUS_PENDING = 0;
    public static final int EVENT_STATUS_FINISHED = 2;
    public static final int ASYNC_SERVICE_TIMEOUT = 30000;
    public static final int WEB_RESP_CODE_SUCCESS = 200;
    public static final int WEB_RESP_CODE_FAILURE = 500;
    public static final int CAR_TYPE_COUNT = 3;
    public static final long EVENT_TRAJ_TIME_OFFSET = 10000;
    public static final double SECTION_ROAD_START = 3400;
    public static final double SECTION_ROAD_END = 12750;
    public static final String DEFAULT_DATETIME_TARGET = "NONE";
    public static final String DEFAULT_POSITION_TARGET = "NONE";
    public static final int DEVICE_STATE_DISABLED = -1;
    public static final int DEVICE_STATE_OFFLINE = 0;
    public static final int DEVICE_STATE_ONLINE = 1;
    public static final int DEVICE_STATE_HIGH_TIMEOUT = 2;
    public static final long TRAJ_EXPIRE_TIMEOUT = 302000;
    public static final long RADAR_REAL_TIME_SPILT_TIMEOUT = 1800000;
    public static final long DATA_SYNC_TIMEOUT = 200;
    public static final int TRAJ_FRAME_STATE_NEW = 0;
    public static final int TRAJ_FRAME_STATE_ONLINE = 1;
    public static final int TRAJ_FRAME_STATE_OFFLINE = 2;
    public static final int TRAJ_FRAME_STATE_MESSAGE = 3;

    public static final int RADAR_TYPE_LASER = 2;
    public static final int RADAR_TYPE_WAVE = 1;
    public static final int RADAR_TYPE_FIBER_AS_DEVICE = 3;

    // 激光雷达对应的设备断面
    public static final Integer DEVICE_LASER_SID = 2;

    // 激光雷达对应的设备号
    public static final Range<Integer> DEVICE_LASER_RANGE = Range.closed(15, 16);

    public static final CarTypeModel CAR_TYPE_COMPACT = new CarTypeModel("光纤", 10);
    public static final CarTypeModel CAR_TYPE_TRUCK = new CarTypeModel("激光雷达", 20);
    public static final CarTypeModel CAR_TYPE_BUS = new CarTypeModel("微波雷达", 30);
}
