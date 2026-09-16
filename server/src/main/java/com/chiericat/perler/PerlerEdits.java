package com.chiericat.perler;

final class PerlerEdits {
    private PerlerEdits() {}

    static Result apply(int[] sourceColors, int[] sourceReservoirs, int[] pixels, int[] selectedColors) {
        int[] nextColors = sourceColors.clone();
        int[] nextReservoirs = sourceReservoirs.clone();
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int selected = selectedColors[i];
            if (pixel < 0 || pixel >= nextColors.length) throw new IllegalArgumentException("像素位置无效。 ");
            if (selected < -1 || selected >= PerlerPalette.COLORS) throw new IllegalArgumentException("颜色无效。 ");
            int old = nextColors[pixel];
            if (old == selected) continue;
            if (old >= 0) nextReservoirs[PerlerPalette.family(old)]++;
            if (selected >= 0) {
                int family = PerlerPalette.family(selected);
                if (nextReservoirs[family] <= 0)
                    throw new IllegalArgumentException(PerlerPalette.NAMES[family] + "系耗材不足，请先补充。 ");
                nextReservoirs[family]--;
            }
            nextColors[pixel] = selected;
        }
        return new Result(nextColors, nextReservoirs);
    }

    record Result(int[] colors, int[] reservoirs) {}
}
