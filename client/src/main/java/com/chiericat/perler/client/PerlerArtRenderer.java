package com.chiericat.perler.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Supplies exact RGB artwork textures in place of Minecraft's restricted map palette. */
public final class PerlerArtRenderer {
    private static final int SIZE = 128;
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();

    private PerlerArtRenderer() {}

    public static void apply(ItemStack stack, MapRenderState state) {
        ClientArtData art = ClientArtData.read(stack).orElse(null);
        if (art == null) return;
        state.texture = texture(art);
        state.decorations.clear();
    }

    static Identifier texture(ClientArtData art) {
        int hash = 31 * Arrays.hashCode(art.colors()) + Arrays.hashCode(art.filled());
        String key = art.size() + "_" + Integer.toUnsignedString(hash, 16);
        Identifier cached = TEXTURES.get(key);
        if (cached != null) return cached;
        Identifier id = Identifier.fromNamespaceAndPath("chieri_perler_client", "art/" + key);
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        for (int y = 0; y < SIZE; y++) {
            int sourceY = y * art.size() / SIZE;
            for (int x = 0; x < SIZE; x++) {
                int sourceX = x * art.size() / SIZE;
                int pixel = sourceY * art.size() + sourceX;
                image.setPixel(x, y, art.isFilled(pixel) ? ClientPalette.argb(art.color(pixel)) : 0);
            }
        }
        Minecraft.getInstance().getTextureManager().register(id,
                new DynamicTexture(() -> "Chieri Perler art " + key, image));
        TEXTURES.put(key, id);
        return id;
    }
}
