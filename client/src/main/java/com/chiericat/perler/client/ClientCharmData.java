package com.chiericat.perler.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

public record ClientCharmData(String projectId, int mapId, int size, byte[] colors, byte[] filled) {
    private static final String MARKER = "chieri_perler_charm";
    private static final String PROJECT_ID = "chieri_perler_project";
    private static final String MAP_ID = "chieri_perler_map_id";
    private static final String PALETTE_VERSION = "chieri_perler_palette_version";
    private static final String SIZE = "chieri_perler_charm_size";
    private static final String COLORS = "chieri_perler_charm_colors";
    private static final String FILLED = "chieri_perler_charm_filled";

    public static Optional<ClientCharmData> read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return Optional.empty();
        CompoundTag tag = custom.copyTag();
        if (!tag.getBooleanOr(MARKER, false)
                || tag.getIntOr(PALETTE_VERSION, 0) != ClientPalette.VERSION) return Optional.empty();
        int size = tag.getIntOr(SIZE, 0);
        byte[] colors = tag.getByteArray(COLORS).orElseGet(() -> new byte[0]);
        byte[] filled = tag.getByteArray(FILLED).orElseGet(() -> new byte[0]);
        int pixels = size * size;
        if (size < 1 || size > 128 || colors.length != pixels * 2 || filled.length != (pixels + 7) / 8) {
            size = 0;
            colors = new byte[0];
            filled = new byte[0];
        }
        for (int pixel = 0; pixel < pixels; pixel++) {
            if ((filled[pixel >> 3] & (1 << (pixel & 7))) != 0
                    && unpack(colors, pixel) >= ClientPalette.COLORS) {
                size = 0;
                colors = new byte[0];
                filled = new byte[0];
                break;
            }
        }
        return Optional.of(new ClientCharmData(tag.getStringOr(PROJECT_ID, ""),
                tag.getIntOr(MAP_ID, -1), size, colors, filled));
    }

    public boolean hasPixels() {
        return size > 0;
    }

    public boolean isFilled(int pixel) {
        return pixel >= 0 && pixel < size * size
                && (filled[pixel >> 3] & (1 << (pixel & 7))) != 0;
    }

    public int color(int pixel) {
        return unpack(colors, pixel);
    }

    private static int unpack(byte[] colors, int pixel) {
        return (Byte.toUnsignedInt(colors[pixel * 2]) << 8)
                | Byte.toUnsignedInt(colors[pixel * 2 + 1]);
    }
}
