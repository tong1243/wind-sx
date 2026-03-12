package com.wut.screencommonsx.Response.Risk;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskLevelData {
    private int level;
    private int CarCount;
}
