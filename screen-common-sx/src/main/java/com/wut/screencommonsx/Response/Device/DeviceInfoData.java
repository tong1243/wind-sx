package com.wut.screencommonsx.Response.Device;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceInfoData {
    private int rid;
    private int type;
    private String ip;
    private int direction;
    private String name;
    private String position;
    private int state;
}
