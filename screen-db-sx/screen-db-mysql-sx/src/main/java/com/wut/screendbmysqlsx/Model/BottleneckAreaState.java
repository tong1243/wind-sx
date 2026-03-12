package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BottleneckAreaState {
    @TableField("id")
    private int id;
    @TableField("state")
    private int state;
    @TableField("speed")
    private double speed;
    @TableField("stream")
    private double stream;
    @TableField("mainStream")
    private int mainStream;
    @TableField("rampStream")
    private int rampStream;
    @TableField("queueLength")
    private double queueLength;
    @TableField("queueDelayTime")
    private double queueDelayTime;
}
