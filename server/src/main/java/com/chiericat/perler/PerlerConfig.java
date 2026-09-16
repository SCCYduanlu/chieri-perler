package com.chiericat.perler;

import java.util.Arrays;

final class PerlerConfig {
    int beadsPerBatch = 128;
    int maxProjectsPerTable = 18;
    int maxReservoirPerFamily = 1_000_000;
    int[] boardSizes = {16, 32, 64, 128};

    void validate() {
        if (beadsPerBatch < 1 || beadsPerBatch > 4096)
            throw new IllegalArgumentException("beadsPerBatch 必须在 1–4096。 ");
        if (maxProjectsPerTable < 1 || maxProjectsPerTable > 36)
            throw new IllegalArgumentException("maxProjectsPerTable 必须在 1–36。 ");
        if (maxReservoirPerFamily < beadsPerBatch || maxReservoirPerFamily > 10_000_000)
            throw new IllegalArgumentException("maxReservoirPerFamily 无效。 ");
        if (boardSizes == null || boardSizes.length == 0 || boardSizes.length > 8)
            throw new IllegalArgumentException("必须配置 1–8 种拼豆板尺寸。 ");
        boardSizes = Arrays.stream(boardSizes).distinct().sorted().toArray();
        for (int size : boardSizes) {
            if (size < 8 || size > 128 || 128 % size != 0)
                throw new IllegalArgumentException("拼豆板尺寸必须是 8–128 且能整除 128。 ");
        }
    }

    boolean allowsSize(int size) {
        return Arrays.stream(boardSizes).anyMatch(value -> value == size);
    }
}
