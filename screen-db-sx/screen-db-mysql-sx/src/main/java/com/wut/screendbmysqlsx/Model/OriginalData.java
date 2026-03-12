package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OriginalData {
    @TableField("id")
    private int id;
    @TableField("timestamp")
    private long timestamp;
    @TableField("botAreaTravelTime")
    private double botAreaTravelTime;
    @TableField("accAreaTravelTime")
    private double accAreaTravelTime;
    @TableField("botAvgSpeed")
    private double botAvgSpeed;
    @TableField("accAvgSpeed")
    private double accAvgSpeed;
    @TableField("botAvgDelay")
    private double botAvgDelay;
    @TableField("accAvgDelay")
    private double accAvgDelay;
}
