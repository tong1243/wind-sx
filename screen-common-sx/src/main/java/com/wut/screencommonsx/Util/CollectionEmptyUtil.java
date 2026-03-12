package com.wut.screencommonsx.Util;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectionEmptyUtil {
    public static <T> Boolean forList(List<T> list) {
        return list == null || list.isEmpty();
    }

    public static <T> Boolean forList(List<T> list, int minimum) {
        return list == null || list.size() < minimum;
    }

    public static <T> Boolean forSet(Set<T> set) {
        return set == null || set.isEmpty();
    }

    public static <T,K> Boolean forMap(Map<T,K> map) {
        return map == null || map.isEmpty();
    }

}
