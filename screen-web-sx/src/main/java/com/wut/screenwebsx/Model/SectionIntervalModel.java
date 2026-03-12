package com.wut.screenwebsx.Model;

import com.google.common.collect.Range;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SectionIntervalModel {
    private int sid;
    private String xsecName;
    private double xsecValue;
    private Range<Double> interval;
}
