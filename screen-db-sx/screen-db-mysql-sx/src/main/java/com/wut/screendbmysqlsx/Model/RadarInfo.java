package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("radarinfo")
public class RadarInfo {
    @TableId(type = IdType.INPUT)
    private Integer rid;
    private Integer type;
    private String ip;
    @TableField("roadDirect")
    private Integer roadDirect;
    private Integer state;
    private Double alpha;
    @TableField("xdistance")
    private Double xDistance;
    @TableField("ydistance")
    private Double yDistance;
    private Double longitude;
    private Double latitude;
    @TableField("deflectionAngle")
    private Double deflectionAngle;
    @TableField("detectStart")
    private Double detectStart;
    @TableField("detectEnd")
    private Double detectEnd;
    private String remark;
    private String agreement;
    private String image;
}
