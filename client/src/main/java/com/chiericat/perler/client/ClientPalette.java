package com.chiericat.perler.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ClientPalette {
    static final int VERSION = 3;
    static final int FAMILIES = 16;
    static final String[] NAMES = {
            "白", "橙", "品红", "淡蓝", "黄", "黄绿", "粉", "灰",
            "淡灰", "青", "紫", "蓝", "棕", "绿", "红", "黑"
    };
    private static final String[] ALIASES = {
            "white ivory cream 白 米白", "orange amber 橙 琥珀", "magenta fuchsia 品红 洋红",
            "lightblue sky 淡蓝 天蓝", "yellow gold 黄 金", "lime chartreuse 黄绿 荧光绿",
            "pink rose 粉 玫瑰", "gray grey 灰", "lightgray silver 淡灰 银",
            "cyan teal 青 蓝绿", "purple violet 紫", "blue navy 蓝 海军蓝",
            "brown coffee 棕 咖啡", "green emerald 绿 翠绿", "red crimson 红 绯红", "black charcoal 黑 墨"
    };
    private static final int[] BASE = {
            0xF9FFFF, 0xF9801D, 0xC74EBD, 0x3AB3DA,
            0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA,
            0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21
    };
    private static final String[] CODES = ClientPalette291Data.codes();
    private static final int[] RGB = ClientPalette291Data.colors();
    static final int COLORS = RGB.length;
    private static final int[] FAMILY = buildFamilies();
    private static final int[][] BY_FAMILY = buildFamilyLists();

    private ClientPalette() {}

    static int family(int color) { check(color); return FAMILY[color]; }
    static int rgb(int color) { check(color); return RGB[color]; }
    static int argb(int color) { return 0xFF000000 | rgb(color); }
    static String code(int color) { check(color); return CODES[color]; }
    static String label(int color) { return code(color) + " · " + series(color) + " · " + NAMES[family(color)] + "耗材"; }
    static String hex(int color) { return String.format("#%06X", rgb(color)); }

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
        if (family < 0 || family >= FAMILIES) return new int[0];
        return BY_FAMILY[family].clone();
    }

    static int representative(int family) {
        int[] colors = colorsForFamily(family);
        if (colors.length == 0) return 0;
        int result = colors[0];
        long best = distance(RGB[result], BASE[family]);
        for (int color : colors) {
            long candidate = distance(RGB[color], BASE[family]);
            if (candidate < best) { best = candidate; result = color; }
        }
        return result;
    }

    static List<Integer> search(String raw) {
        String query = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        List<Integer> result = new ArrayList<>();
        for (int color = 0; color < COLORS; color++) {
            if (query.isEmpty() || searchable(color).contains(query)) result.add(color);
        }
        return result;
    }

    private static String searchable(int color) {
        int rgb = rgb(color);
        return (label(color) + " " + series(color) + " " + ALIASES[family(color)] + " " + hex(color)
                + " " + (color + 1) + " " + ((rgb >> 16) & 255) + "," + ((rgb >> 8) & 255)
                + "," + (rgb & 255)).toLowerCase(Locale.ROOT);
    }

    private static int[] buildFamilies() {
        int[] result = new int[COLORS];
        for (int color = 0; color < COLORS; color++) {
            long best = Long.MAX_VALUE;
            for (int family = 0; family < FAMILIES; family++) {
                long candidate = distance(RGB[color], BASE[family]);
                if (candidate < best) { best = candidate; result[color] = family; }
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

    private static long distance(int a, int b) {
        long dr = ((a >> 16) & 255) - ((b >> 16) & 255);
        long dg = ((a >> 8) & 255) - ((b >> 8) & 255);
        long db = (a & 255) - (b & 255);
        return 3 * dr * dr + 4 * dg * dg + 2 * db * db;
    }

    private static void check(int color) {
        if (color < 0 || color >= COLORS) throw new IllegalArgumentException("拼豆颜色索引越界");
    }
}
