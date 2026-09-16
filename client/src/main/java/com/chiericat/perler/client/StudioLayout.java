package com.chiericat.perler.client;

record StudioLayout(
        boolean compact,
        int headerHeight,
        int top,
        int margin,
        int leftX,
        int leftWidth,
        int paletteX,
        int paletteWidth,
        int boardX,
        int boardWidth,
        int boardHeight,
        int palettePadding,
        int paletteGridTop,
        int paletteSwatch,
        int paletteGap,
        int paletteColumns,
        int paletteRowsVisible
) {
    static StudioLayout of(int width, int height) {
        boolean compact = width < 720 || height < 400;
        int headerHeight = compact ? 34 : 46;
        int top = compact ? 40 : 56;
        int margin = compact ? 6 : 12;
        int leftX = margin;
        int leftWidth = compact ? 96 : 146;
        int paletteWidth = compact ? clamp(width / 3, 118, 142) : 234;
        int paletteX = width - margin - paletteWidth;
        int sectionGap = compact ? 6 : 8;
        int boardX = leftX + leftWidth + sectionGap;
        int boardWidth = Math.max(80, paletteX - boardX - sectionGap);
        int boardHeight = Math.max(100, height - top - margin);
        int palettePadding = compact ? 8 : 12;
        int paletteGridTop = compact ? 84 : 111;
        int paletteSwatch = compact ? 18 : 27;
        int paletteGap = compact ? 3 : 5;
        int innerWidth = paletteWidth - palettePadding * 2;
        int paletteColumns = Math.max(3,
                (innerWidth + paletteGap) / (paletteSwatch + paletteGap));
        int footerHeight = compact ? 26 : 70;
        int paletteRowsVisible = Math.max(1,
                (height - paletteGridTop - footerHeight + paletteGap)
                        / (paletteSwatch + paletteGap));
        return new StudioLayout(compact, headerHeight, top, margin, leftX, leftWidth,
                paletteX, paletteWidth, boardX, boardWidth, boardHeight, palettePadding,
                paletteGridTop, paletteSwatch, paletteGap, paletteColumns, paletteRowsVisible);
    }

    int panelHeight() {
        return boardHeight;
    }

    int searchY() {
        return compact ? top + 20 : 78;
    }

    int searchHeight() {
        return compact ? 18 : 22;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
