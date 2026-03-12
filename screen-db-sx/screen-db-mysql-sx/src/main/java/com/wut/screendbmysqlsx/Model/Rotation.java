package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("rotation")
public class Rotation {
    private String ip;
    @TableField("centerx")
    private Double centerX;
    @TableField("centery")
    private Double centerY;
    private Double angle;
    @TableField("offsetx")
    private Double offsetX;
    @TableField("offsety")
    private Double offsetY;
    @TableField("roaddirection")
    private Integer roadDirection;
    @TableField("devicedirection")
    private Integer deviceDirection;
    private Integer status;
    private Integer sid;
    @TableField("minx")
    private Double minX;
    @TableField("maxx")
    private Double maxX;
}
