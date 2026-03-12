package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 车辆轨迹信息
// 对应表名:traj_near_real_{time}
@TableName("traj_near_real")
public class Traj {
    @TableField("trajId")
    @JsonProperty("trajId")
    private Long trajId;
    @TableField("timeStamp")
    private Long timestamp;
    @TableField("frenetx")
    @JsonProperty("frenetX")
    private Double frenetX;
    @TableField("frenety")
    @JsonProperty("frenetY")
    private Double frenetY;
    @TableField("speedx")
    @JsonProperty("speedX")
    private Double speedX;
    @TableField("speedy")
    @JsonProperty("speedY")
    private Double speedY;
    @TableField("headingAngle")
    @JsonProperty("headingAngle")
    private Double headingAngle;
    private Double longitude;
    private Double latitude;
    @TableField("mercatorx")
    @JsonProperty("mercatorX")
    private Double mercatorX;
    @TableField("mercatory")
    @JsonProperty("mercatorY")
    private Double mercatorY;
    private Double accx;
    @TableField("RoadDirect")
    @JsonProperty("roadDirect")
    private Integer roadDirect;
    @TableField("CarId")
    @JsonProperty("carId")
    private String carId;
    @TableField("licenseColor")
    @JsonProperty("licenseColor")
    private Integer licenseColor;
    private Integer lane;
    private String type;
    @TableField("carType")
    @JsonProperty("carType")
    private Integer carType;
    @TableField("rawId")
    @JsonProperty("rawId")
    private Integer rawId;
    @TableField("height")
    @JsonProperty("height")
    private Double height;
}
