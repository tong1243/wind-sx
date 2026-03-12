package com.wut.screencommonsx.Util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;

import static com.wut.screencommonsx.Static.WebModuleStatic.CAR_TYPE_COUNT;

public class DataParamParseUtil {
    public static String getPositionStr(double frenetx) {
        // 将小数部分截断，只保留整数部分
        int pos = (int) Math.floor(frenetx);
        // 计算公里数和米数
        int km = pos / 1000;
        int meters = pos % 1000;
        // 格式化米数为3位，不足3位前面补零
        return String.format("K%d+%03d", km, meters);
    }
    public static List<Integer> parsePostureComp(String comp) {
        String readyToSplitStr = comp.substring(1, comp.length() - 1);
        Pattern pattern = Pattern.compile("\\D+");
        return pattern.splitAsStream(readyToSplitStr).mapToInt(Integer::parseInt).boxed().toList();
    }

    public static double getRoundValue(double value) {
        BigDecimal decimal = new BigDecimal(value).setScale(0, RoundingMode.HALF_UP);
        return decimal.intValue();
    }
    public static double getRoundValue2(double value) {
        BigDecimal decimal = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        return decimal.doubleValue();
    }

}
