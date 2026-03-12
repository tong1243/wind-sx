package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskEvent {
    @TableField("timestamp")
    private Long timestamp;
    @TableField("carId")
    private String carId;
    @TableField("position")
    private String position;
    @TableField("speed")
    private Double speed;
    @TableField("inteSpeed")
    private Double inteSpeed;
    @TableField("distanceFrontCar")
    private Double distanceFrontCar;
    @TableField("riskType")
    private int riskType;
}
