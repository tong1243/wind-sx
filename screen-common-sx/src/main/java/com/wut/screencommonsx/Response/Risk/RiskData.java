package com.wut.screencommonsx.Response.Risk;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskData {
    private int sid;
    private int riskLevel;
    private double density;
    private double speed;
}
