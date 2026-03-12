package com.wut.screencommonsx.Static;

import java.util.List;

public class DbModuleStatic {
    public static final List<String> DYNAMIC_TABLE_NAMES = List.of(
            "carevent",
            "section",
            "traj_near_real",
            "posture",
            "fibermetric",
            "radarmetric",
            "fibersecmetric",
            "radarsecmetric",
            "radarallsecmetric",
            "parameters",
            "bottleneck_area_state",
            "tunnel_risk",
            "risk_event"
    );
    public static final String TABLE_SUFFIX_KEY = "timestamp";
    public static final String TABLE_SUFFIX_SEPARATOR  = "_";
    public static final int TABLE_POSTURE_LIMIT = 15;
    public static final int TABLE_SECTION_LIMIT = 15;
    public static final int TABLE_SECTION_LIST_SIZE = 6;
    public static final int TABLE_DEVICE_LIMIT = 16;
    public static final long TABLE_TARGET_TIME_OFFSET = 59000;
}
