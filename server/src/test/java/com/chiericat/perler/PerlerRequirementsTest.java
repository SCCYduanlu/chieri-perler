package com.chiericat.perler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PerlerRequirementsTest {
    @Test
    void reportsEveryMissingFuseMaterialAtOnce() {
        assertEquals("无法烫豆，背包缺少：煤炭 ×1、空地图 ×1。请准备后重试。 ",
                PerlerRequirements.missingFuseMaterials(0, 0));
        assertEquals("无法烫豆，背包缺少：空地图 ×1。请准备后重试。 ",
                PerlerRequirements.missingFuseMaterials(3, 0));
        assertEquals("", PerlerRequirements.missingFuseMaterials(1, 1));
    }
}
