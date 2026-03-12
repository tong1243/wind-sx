package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("posture")
// 对应表名:posture_{time}
public class Posture {
    @TableField("timeStampStart")
    private Long timestampStart;
    @TableField("timeStampEnd")
    private Long timestampEnd;
    @TableField("avgQwh")
    private Double avgQwh;
    @TableField("avgVwh")
    private Double avgVwh;
    @TableField("avgKwh")
    private Double avgKwh;
    @TableField("avgQez")
    private Double avgQez;
    @TableField("avgVez")
    private Double avgVez;
    @TableField("avgKez")
    private Double avgKez;
    private String comp;
}
