package com.chiericat.perler;

import java.util.ArrayList;
import java.util.List;

final class PerlerRequirements {
    private PerlerRequirements() {}

    static String missingFuseMaterials(int coal, int maps) {
        List<String> missing = new ArrayList<>(2);
        if (coal < 1) missing.add("煤炭 ×" + (1 - coal));
        if (maps < 1) missing.add("空地图 ×" + (1 - maps));
        if (missing.isEmpty()) return "";
        return "无法烫豆，背包缺少：" + String.join("、", missing) + "。请准备后重试。 ";
    }
}
