package com.wut.screencommonsx.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DefaultMsgResp {
    private int code;
    private boolean flag;
    private String info;
    private String msg;
}
