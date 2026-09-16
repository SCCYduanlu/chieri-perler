package com.chiericat.perler;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

final class MinecraftTestBootstrap {
    private static boolean ready;

    private MinecraftTestBootstrap() {}

    static synchronized void start() {
        if (ready) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ready = true;
    }
}
