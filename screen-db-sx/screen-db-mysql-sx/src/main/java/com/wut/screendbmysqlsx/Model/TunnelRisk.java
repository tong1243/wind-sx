package com.wut.screendbmysqlsx.Model;


import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TunnelRisk {
    @TableField("sId")
    private int sId;
    @TableField("riskLevel")
    private int riskLevel;
    @TableField("maxRiskLevel")
    private int maxRiskLevel;
    @TableField("timestamp")
    private Long timestamp;
    @TableField("stream")
    private Double stream;
    @TableField("density")
    private Double density;
    @TableField("speed")
    private Double speed;
    @TableField("TSC")
    private Double TSC;
    @TableField("riskCount")
    private int riskCount;
}