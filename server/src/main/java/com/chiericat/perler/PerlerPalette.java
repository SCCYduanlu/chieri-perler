package com.chiericat.perler;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;

final class PerlerPalette {
    static final int VERSION = 3;
    static final int FAMILIES = 16;
    static final String[] NAMES = {
            "白", "橙", "品红", "淡蓝", "黄", "黄绿", "粉", "灰",
            "淡灰", "青", "紫", "蓝", "棕", "绿", "红", "黑"
    };
    static final Item[] DYES = {
            Items.DYE.white(), Items.DYE.orange(), Items.DYE.magenta(), Items.DYE.lightBlue(),
            Items.DYE.yellow(), Items.DYE.lime(), Items.DYE.pink(), Items.DYE.gray(),
            Items.DYE.lightGray(), Items.DYE.cyan(), Items.DYE.purple(), Items.DYE.blue(),
            Items.DYE.brown(), Items.DYE.green(), Items.DYE.red(), Items.DYE.black()
    };
    private static final int[] BASE = {
            0xF9FFFF, 0xF9801D, 0xC74EBD, 0x3AB3DA,
            0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA,
            0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21
    };
    private static final String[] CODES = Palette291Data.codes();
    private static final int[] RGB = Palette291Data.colors();
    private static final int[] OLD_216 = Palette216Data.colors();
    static final int COLORS = RGB.length;
    private static final int[] FAMILY = buildFamilies();
    private static final int[][] BY_FAMILY = buildFamilyLists();
    private static final byte[] MAP = buildMapColors();
    private static final int[] LEGACY = buildLegacyColors();

    private PerlerPalette() {}

    static int family(int color) {
        check(color);
        return FAMILY[color];
    }

    static int rgb(int color) {
        return color >= 0 && color < COLORS ? RGB[color] : 0xD0D0D0;
    }

    static byte mapByte(int color) {
        return color >= 0 && color < COLORS ? MAP[color] : 0;
    }

    static String code(int color) {
        check(color);
        return CODES[color];
    }

    static String label(int color) {
        return code(color) + " · " + series(color) + " · " + NAMES[family(color)] + "耗材";
    }

    static String series(int color) {
        String code = code(color);
        if (code.startsWith("ZG")) return "莫兰迪色";
        return switch (code.charAt(0)) {
            case 'A' -> "黄色系";
            case 'B' -> "绿色系";
            case 'C' -> "蓝色系";
            case 'D' -> "紫色系";
            case 'E' -> "粉色系";
            case 'F' -> "红色系";
            case 'G' -> "棕色系";
            case 'H' -> "黑白色系";
            case 'M' -> "大地色系";
            case 'P' -> "柔和色系";
            case 'Q' -> "特殊色系";
            case 'R' -> "鲜艳色系";
            case 'T' -> "纯白色系";
            case 'Y' -> "荧光色系";
            default -> "标准色";
        };
    }

    static int[] colorsForFamily(int family) {
        if (family < 0 || family >= FAMILIES) throw new IllegalArgumentException("拼豆色系越界。 ");
        return BY_FAMILY[family].clone();
    }

    static int representative(int family) {
        int[] colors = colorsForFamily(family);
        if (colors.length == 0) return 0;
        int result = colors[0];
        long best = distance(RGB[result], BASE[family]);
        for (int color : colors) {
            long candidate = distance(RGB[color], BASE[family]);
            if (candidate < best) {
                best = candidate;
                result = color;
            }
        }
        return result;
    }

    static int migrateLegacy(int oldColor) {
        if (oldColor < 0) return -1;
        return nearest(oldColor < LEGACY.length ? LEGACY[oldColor] : LEGACY[0]);
    }

    static int migrate216(int oldColor) {
        if (oldColor < 0) return -1;
        return nearest(oldColor < OLD_216.length ? OLD_216[oldColor] : OLD_216[0]);
    }

    static int nearest(int wanted) {
        long best = Long.MAX_VALUE;
        int result = 0;
        for (int color = 0; color < COLORS; color++) {
            long candidate = distance(wanted, RGB[color]);
            if (candidate < best) {
                best = candidate;
                result = color;
            }
        }
        return result;
    }

    private static void check(int color) {
        if (color < 0 || color >= COLORS) throw new IllegalArgumentException("拼豆颜色索引越界。 ");
    }

    private static int[] buildFamilies() {
        int[] result = new int[COLORS];
        for (int color = 0; color < COLORS; color++) {
            long best = Long.MAX_VALUE;
            for (int family = 0; family < FAMILIES; family++) {
                long candidate = distance(RGB[color], BASE[family]);
                if (candidate < best) {
                    best = candidate;
                    result[color] = family;
                }
            }
        }
        return result;
    }

    private static int[][] buildFamilyLists() {
        List<List<Integer>> groups = new ArrayList<>();
        for (int family = 0; family < FAMILIES; family++) groups.add(new ArrayList<>());
        for (int color = 0; color < COLORS; color++) groups.get(FAMILY[color]).add(color);
        int[][] result = new int[FAMILIES][];
        for (int family = 0; family < FAMILIES; family++)
            result[family] = groups.get(family).stream().mapToInt(Integer::intValue).toArray();
        return result;
    }

    private static byte[] buildMapColors() {
        byte[] result = new byte[COLORS];
        for (int color = 0; color < COLORS; color++) {
            long best = Long.MAX_VALUE;
            int bestId = 4;
            for (int packed = 4; packed < 256; packed++) {
                int actual;
                try { actual = MapColor.getColorFromPackedId(packed); }
                catch (RuntimeException ignored) { continue; }
                if (((actual >>> 24) & 0xFF) == 0) continue;
                long candidate = distance(RGB[color], actual & 0xFFFFFF);
                if (candidate < best) { best = candidate; bestId = packed; }
            }
            result[color] = (byte) bestId;
        }
        return result;
    }

    private static int[] buildLegacyColors() {
        int[] result = new int[256];
        for (int family = 0; family < FAMILIES; family++) {
            int base = BASE[family];
            for (int shade = 0; shade < 16; shade++) {
                double light = shade < 8 ? 0.34 + shade * 0.082 : 1.0;
                double white = shade < 8 ? 0.0 : (shade - 7) * 0.075;
                int r = legacyChannel(base >> 16, light, white);
                int g = legacyChannel(base >> 8, light, white);
                int b = legacyChannel(base, light, white);
                result[family * 16 + shade] = (r << 16) | (g << 8) | b;
            }
        }
        return result;
    }

    private static int legacyChannel(int source, double light, double white) {
        int value = source & 0xFF;
        value = (int) Math.round(value * light);
        value = (int) Math.round(value + (255 - value) * white);
        return Math.max(0, Math.min(255, value));
    }

    private static long distance(int a, int b) {
        long dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        long dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        long db = (a & 0xFF) - (b & 0xFF);
        return 3 * dr * dr + 4 * dg * dg + 2 * db * db;
    }
}
