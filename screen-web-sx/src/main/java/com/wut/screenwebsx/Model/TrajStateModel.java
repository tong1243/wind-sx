package com.wut.screenwebsx.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrajStateModel {
    private int direction;      // 轨迹数据方向
    private long timestamp;     // 轨迹数据最新时间戳
    private int state;          // 状态位
                                // -> 状态位为0时,说明该轨迹在记录时间区间(2s)内新加入
                                // -> 状态位为1时,说明有最新的轨迹记录
                                // -> 状态位为2时,说明没有最新的轨迹记录
                                // -> 状态位为3时,说明没有最新的轨迹记录,且消息已经发送给客户端
}
