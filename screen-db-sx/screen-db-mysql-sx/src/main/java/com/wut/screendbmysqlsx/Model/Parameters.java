package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Parameters {
    @TableField("time")
    private int time;
    @TableField("road_id")
    private int roadId;
    @TableField("stream")
    private double stream;
    @TableField("density")
    private double density;
    @TableField("speed")
    private double speed;
    @TableField("travel_time")
    private double travelTime;
    @TableField("delay")
    private double delay;
    @TableField("state")
    private int state;
    @TableField("timeStamp")
    private long timeStamp;
    @TableField("upSpeed")
    private double upSpeed;
    @TableField("downSpeed")
    private double downSpeed;
    @TableField("upDensity")
    private double upDensity;
    @TableField("downDensity")
    private double downDensity;
    @TableField("rampStream")
    private double rampStream;
    @TableField("carCount")
    private int carCount;

}
