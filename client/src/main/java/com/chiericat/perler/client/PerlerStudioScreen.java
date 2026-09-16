package com.chiericat.perler.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PerlerStudioScreen extends Screen {
    private static final int BG = 0xFF0C0F16;
    private static final int PANEL = 0xFF151A24;
    private static final int PANEL_2 = 0xFF1D2430;
    private static final int BOARD = 0xFFE2D9C5;
    private static final int BOARD_DARK = 0xFFC6BCA8;
    private static final int TEXT = 0xFFF5F7FF;
    private static final int MUTED = 0xFF929BAD;
    private static final int ACCENT = 0xFFFF5FA2;
    private static final int ACCENT_2 = 0xFF9E7BFF;
    private static final int GOOD = 0xFF55E6A5;
    private static final int BAD = 0xFFFF6B75;

    private enum View { PROJECTS, MATERIALS, EDITOR }
    private enum Tool { PENCIL, ERASER, PICKER }
    private enum PendingKind { NORMAL, UNDO, REDO }

    private ClientPackets.StationPayload station;
    private View view = View.PROJECTS;
    private Tool tool = Tool.PENCIL;
    private EditBox colorSearch;
    private List<Integer> filteredColors = ClientPalette.search("");
    private int paletteScroll;
    private int libraryScroll;

    private String projectId = "";
    private String projectName = "";
    private String projectOwner = "";
    private int boardSize;
    private boolean fused;
    private long revision;
    private int[] pixels = new int[0];
    private int[] reservoirs = new int[16];
    private int selectedColor;

    private int cellSize;
    private double boardOriginX;
    private double boardOriginY;
    private boolean fitPending = true;
    private boolean painting;
    private boolean panning;
    private double panStartX;
    private double panStartY;
    private double panOriginX;
    private double panOriginY;
    private double panTravel;
    private int lastStrokePixel = -1;
    private final LinkedHashMap<Integer, Integer> strokeBefore = new LinkedHashMap<>();

    private final Deque<Edit> undo = new ArrayDeque<>();
    private final Deque<Edit> redo = new ArrayDeque<>();
    private Edit pendingEdit;
    private PendingKind pendingKind;
    private boolean syncing;
    private String toast = "";
    private boolean toastError;
    private int toastTicks;

    PerlerStudioScreen(ClientPackets.StationPayload station) {
        super(Component.literal("Chieri 拼豆工作室"));
        this.station = station;
        this.reservoirs = Arrays.copyOf(station.reservoirs(), 16);
    }

    String stationId() { return station.stationId(); }

    void receiveStation(ClientPackets.StationPayload next) {
        station = next;
        reservoirs = Arrays.copyOf(next.reservoirs(), 16);
        if (view != View.EDITOR) toast("材料与工程列表已同步", false);
    }

    void receiveProject(ClientPackets.ProjectPayload next) {
        boolean changedProject = !projectId.equals(next.projectId());
        projectId = next.projectId();
        projectName = next.name();
        projectOwner = next.ownerName();
        boardSize = next.size();
        fused = next.fused();
        revision = next.updatedAt();
        pixels = next.colors().clone();
        reservoirs = Arrays.copyOf(next.reservoirs(), 16);
        view = View.EDITOR;
        syncing = false;
        pendingEdit = null;
        strokeBefore.clear();
        painting = false;
        if (changedProject) {
            undo.clear();
            redo.clear();
            selectedColor = firstAvailableColor();
        }
        fitPending = true;
        rebuildWidgets();
        fitBoardIfPossible();
        toast(fused ? "成品工程 · 只读预览" : "工程已同步，可直接拖动画豆", false);
    }

    void receiveStatus(ClientPackets.StatusPayload status) {
        if (!status.projectId().isEmpty() && status.projectId().equals(projectId)) {
            revision = status.revision();
            reservoirs = Arrays.copyOf(status.reservoirs(), 16);
            syncing = false;
            if (!status.error() && pendingEdit != null) finishPendingHistory();
            if (status.error()) {
                pendingEdit = null;
                toast(status.message(), true);
            } else {
                updateSummary(status.filled(), status.revision());
                toast(status.message(), false);
            }
        } else {
            syncing = false;
            toast(status.message(), status.error());
        }
    }

    @Override
    protected void init() {
        if (view == View.EDITOR) {
            String previous = colorSearch == null ? "" : colorSearch.getValue();
            StudioLayout layout = layout();
            colorSearch = new EditBox(font, paletteX() + layout.palettePadding(), layout.searchY(),
                    paletteWidth() - layout.palettePadding() * 2, layout.searchHeight(),
                    Component.literal("搜索颜色"));
            colorSearch.setMaxLength(32);
            colorSearch.setHint(Component.literal(layout.compact()
                    ? "名称 / #HEX / RGB" : "搜索颜色 / #HEX / RGB / 编号"));
            colorSearch.setResponder(value -> {
                filteredColors = ClientPalette.search(value);
                paletteScroll = 0;
            });
            colorSearch.setValue(previous);
            addRenderableWidget(colorSearch);
        } else {
            colorSearch = null;
        }
        fitBoardIfPossible();
    }

    @Override
    public void tick() {
        if (toastTicks > 0) toastTicks--;
    }

    @Override
    public void removed() {
        PerlerClient.clear(this);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (painting) endStroke();
        super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xFF131827, BG);
        drawHeader(graphics);
        switch (view) {
            case PROJECTS -> drawProjects(graphics, mouseX, mouseY);
            case MATERIALS -> drawMaterials(graphics, mouseX, mouseY);
            case EDITOR -> drawEditor(graphics, mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawToast(graphics);
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        StudioLayout layout = layout();
        g.fill(0, 0, width, layout.headerHeight(), 0xEE111621);
        g.fill(0, layout.headerHeight() - 1, width, layout.headerHeight(), ACCENT);
        int titleX = layout.compact() ? 10 : 18;
        int titleY = layout.compact() ? 6 : 10;
        g.text(font, "CHIERI", titleX, titleY, ACCENT, true);
        g.text(font, "PERLER STUDIO", titleX + 44, titleY, TEXT, true);
        g.text(font, view == View.EDITOR ? projectName + " · " + boardSize + "×" + boardSize
                : "拼豆台 " + station.stationId(), titleX, layout.compact() ? 20 : 27, MUTED, false);
        g.text(font, layout.compact() ? "2.4.1" : "客户端工作室 2.4.1",
                width - (layout.compact() ? 34 : 118), layout.compact() ? 11 : 18, MUTED, false);
    }

    private void drawNavigation(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        StudioLayout layout = layout();
        if (layout.compact()) {
            int x = layout.leftX();
            panel(g, x, layout.top(), layout.leftWidth(), layout.panelHeight(), PANEL);
            g.text(font, "工作区", x + 6, layout.top() + 6, MUTED, false);
            navButton(g, x + 6, layout.top() + 20, layout.leftWidth() - 12, 22,
                    "作品库", view == View.PROJECTS, mouseX, mouseY);
            navButton(g, x + 6, layout.top() + 46, layout.leftWidth() - 12, 22,
                    "材料仓", view == View.MATERIALS, mouseX, mouseY);
            g.text(font, "台主", x + 6, height - layout.margin() - 48, MUTED, false);
            g.text(font, station.ownerName(), x + 6, height - layout.margin() - 35, TEXT, false);
            g.text(font, station.projects().size() + "/" + station.maxProjects() + " 工程",
                    x + 6, height - layout.margin() - 20, MUTED, false);
            return;
        }
        panel(g, 12, 56, 154, height - 68, PANEL);
        g.text(font, "工作区", 26, 70, MUTED, false);
        navButton(g, 22, 91, 134, 30, "作品库", view == View.PROJECTS, mouseX, mouseY);
        navButton(g, 22, 127, 134, 30, "材料仓", view == View.MATERIALS, mouseX, mouseY);
        g.text(font, "台主", 26, height - 75, MUTED, false);
        g.text(font, station.ownerName(), 26, height - 58, TEXT, false);
        g.text(font, station.projects().size() + "/" + station.maxProjects() + " 个工程", 26,
                height - 40, MUTED, false);
    }

    private void drawProjects(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        drawNavigation(g, mouseX, mouseY);
        if (layout().compact()) {
            drawCompactProjects(g, mouseX, mouseY);
            return;
        }
        int contentX = 180;
        int contentW = width - contentX - 16;
        g.text(font, "新建拼豆板", contentX, 62, TEXT, true);
        int sizeY = 80;
        int cardGap = 8;
        int count = Math.max(1, station.boardSizes().length);
        int cardW = Math.max(76, Math.min(132, (contentW - cardGap * (count - 1)) / count));
        for (int i = 0; i < station.boardSizes().length; i++) {
            int x = contentX + i * (cardW + cardGap);
            int size = station.boardSizes()[i];
            card(g, x, sizeY, cardW, 50, mouseX, mouseY, false);
            g.text(font, size + " × " + size, x + 12, sizeY + 11, TEXT, true);
            g.text(font, size * size + " 颗", x + 12, sizeY + 29, MUTED, false);
        }

        g.text(font, "我的工程", contentX, 148, TEXT, true);
        g.text(font, "右键台子后随时继续，所有修改由服务器保存", contentX + 70, 148, MUTED, false);
        int columns = Math.max(1, Math.min(3, contentW / 210));
        int projectW = (contentW - (columns - 1) * 10) / columns;
        int baseY = 170 - libraryScroll;
        for (int i = 0; i < station.projects().size(); i++) {
            ClientPackets.ProjectSummary project = station.projects().get(i);
            int x = contentX + (i % columns) * (projectW + 10);
            int y = baseY + (i / columns) * 74;
            if (y + 64 < 160 || y > height - 18) continue;
            card(g, x, y, projectW, 64, mouseX, mouseY, project.fused());
            g.fill(x + 10, y + 10, x + 14, y + 53, project.fused() ? GOOD : ACCENT_2);
            g.text(font, project.name(), x + 24, y + 10, TEXT, true);
            g.text(font, project.size() + "×" + project.size() + " · " + project.filled()
                    + "/" + (project.size() * project.size()), x + 24, y + 28, MUTED, false);
            g.text(font, project.fused() ? "已烫豆 · 点击预览" : "进行中 · 点击继续",
                    x + 24, y + 45, project.fused() ? GOOD : 0xFFC8B8FF, false);
        }
        if (station.projects().isEmpty()) {
            panel(g, contentX, 170, contentW, 96, PANEL);
            g.centeredText(font, "还没有工程，先从上方选择一块拼豆板", contentX + contentW / 2, 205, MUTED);
        }
    }

    private void drawCompactProjects(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        StudioLayout layout = layout();
        int contentX = layout.boardX();
        int contentW = width - contentX - layout.margin();
        int cardGap = 6;
        int columns = 2;
        int cardW = (contentW - cardGap) / columns;
        int sizeTop = layout.top() + 20;
        int sizeH = 34;
        g.text(font, "新建拼豆板", contentX, layout.top() + 4, TEXT, true);
        for (int i = 0; i < station.boardSizes().length; i++) {
            int x = contentX + (i % columns) * (cardW + cardGap);
            int y = sizeTop + (i / columns) * (sizeH + cardGap);
            int size = station.boardSizes()[i];
            card(g, x, y, cardW, sizeH, mouseX, mouseY, false);
            g.text(font, size + "×" + size, x + 8, y + 7, TEXT, true);
            g.text(font, size * size + " 颗", x + 8, y + 20, MUTED, false);
        }
        int sizeRows = Math.max(1, (station.boardSizes().length + columns - 1) / columns);
        int projectsTitleY = sizeTop + sizeRows * (sizeH + cardGap) + 2;
        g.text(font, "我的工程", contentX, projectsTitleY, TEXT, true);
        int projectColumns = contentW >= 300 ? 2 : 1;
        int projectGap = 6;
        int projectW = (contentW - (projectColumns - 1) * projectGap) / projectColumns;
        int baseY = projectsTitleY + 16 - libraryScroll;
        for (int i = 0; i < station.projects().size(); i++) {
            ClientPackets.ProjectSummary project = station.projects().get(i);
            int x = contentX + (i % projectColumns) * (projectW + projectGap);
            int y = baseY + (i / projectColumns) * 58;
            if (y + 52 < projectsTitleY + 12 || y > height - layout.margin()) continue;
            card(g, x, y, projectW, 52, mouseX, mouseY, project.fused());
            g.fill(x + 6, y + 7, x + 9, y + 44, project.fused() ? GOOD : ACCENT_2);
            g.text(font, project.name(), x + 15, y + 7, TEXT, true);
            g.text(font, project.size() + "×" + project.size() + " · " + project.filled()
                    + "/" + (project.size() * project.size()), x + 15, y + 22, MUTED, false);
            g.text(font, project.fused() ? "已完成 · 预览" : "进行中 · 继续",
                    x + 15, y + 37, project.fused() ? GOOD : 0xFFC8B8FF, false);
        }
        if (station.projects().isEmpty()) {
            panel(g, contentX, baseY, contentW, 52, PANEL);
            g.centeredText(font, "选择上方尺寸创建第一个工程", contentX + contentW / 2,
                    baseY + 21, MUTED);
        }
    }

    private void drawMaterials(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        drawNavigation(g, mouseX, mouseY);
        if (layout().compact()) {
            drawCompactMaterials(g, mouseX, mouseY);
            return;
        }
        int contentX = 180;
        int contentW = width - contentX - 16;
        g.text(font, "材料仓 · 16 个基础色系", contentX, 62, TEXT, true);
        g.text(font, "每次投入 1 染料 + 1 蜜脾，补充 " + station.beadsPerBatch() + " 颗", contentX, 80, MUTED, false);
        int columns = Math.max(2, Math.min(4, contentW / 160));
        int gap = 10;
        int cardW = (contentW - gap * (columns - 1)) / columns;
        int cardH = Math.max(62, Math.min(88, (height - 126 - gap * 3) / 4));
        for (int family = 0; family < 16; family++) {
            int x = contentX + (family % columns) * (cardW + gap);
            int y = 108 + (family / columns) * (cardH + gap);
            card(g, x, y, cardW, cardH, mouseX, mouseY, false);
            int rgb = ClientPalette.rgb(ClientPalette.representative(family));
            g.fill(x + 12, y + 12, x + 42, y + 42, 0xFF000000 | rgb);
            g.outline(x + 11, y + 11, 32, 32, 0x66FFFFFF);
            g.text(font, ClientPalette.NAMES[family] + "系", x + 54, y + 13, TEXT, true);
            g.text(font, reservoirs[family] + " 颗", x + 54, y + 31,
                    reservoirs[family] > 0 ? GOOD : BAD, false);
            g.text(font, "点击补充", x + 12, y + cardH - 16, MUTED, false);
        }
    }

    private void drawCompactMaterials(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        StudioLayout layout = layout();
        int contentX = layout.boardX();
        int contentW = width - contentX - layout.margin();
        g.text(font, "材料仓 · 16 色系", contentX, layout.top() + 4, TEXT, true);
        g.text(font, "染料 + 蜜脾 = " + station.beadsPerBatch() + " 颗", contentX,
                layout.top() + 17, MUTED, false);
        int columns = Math.max(2, Math.min(4, contentW / 80));
        int rows = (16 + columns - 1) / columns;
        int gap = 4;
        int startY = layout.top() + 32;
        int cardW = (contentW - gap * (columns - 1)) / columns;
        int cardH = Math.max(34,
                (height - layout.margin() - startY - gap * (rows - 1)) / rows);
        for (int family = 0; family < 16; family++) {
            int x = contentX + (family % columns) * (cardW + gap);
            int y = startY + (family / columns) * (cardH + gap);
            card(g, x, y, cardW, cardH, mouseX, mouseY, false);
            int rgb = ClientPalette.rgb(ClientPalette.representative(family));
            g.fill(x + 5, y + 7, x + 19, y + 21, 0xFF000000 | rgb);
            g.outline(x + 4, y + 6, 16, 16, 0x66FFFFFF);
            g.text(font, ClientPalette.NAMES[family], x + 24, y + 6, TEXT, true);
            g.text(font, reservoirs[family] + " 颗", x + 24, y + 19,
                    reservoirs[family] > 0 ? GOOD : BAD, false);
        }
    }

    private void drawEditor(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        fitBoardIfPossible();
        drawEditorTools(g, mouseX, mouseY);
        drawPalette(g, mouseX, mouseY);
        drawBoard(g, mouseX, mouseY);
    }

    private void drawEditorTools(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        StudioLayout layout = layout();
        if (layout.compact()) {
            drawCompactEditorTools(g, mouseX, mouseY, layout);
            return;
        }
        panel(g, 12, 56, 146, height - 68, PANEL);
        smallButton(g, 22, 66, 126, 26, "← 返回工程", false, mouseX, mouseY);
        g.text(font, "绘制工具", 24, 108, MUTED, false);
        toolButton(g, 22, 126, "画笔  P", Tool.PENCIL, mouseX, mouseY);
        toolButton(g, 22, 158, "橡皮  E", Tool.ERASER, mouseX, mouseY);
        toolButton(g, 22, 190, "吸管  I", Tool.PICKER, mouseX, mouseY);
        g.text(font, "历史", 24, 232, MUTED, false);
        smallButton(g, 22, 248, 60, 25, "撤销", undo.isEmpty() || syncing, mouseX, mouseY);
        smallButton(g, 88, 248, 60, 25, "重做", redo.isEmpty() || syncing, mouseX, mouseY);
        g.text(font, "视图", 24, 291, MUTED, false);
        smallButton(g, 22, 307, 38, 25, "−", cellSize <= 2, mouseX, mouseY);
        smallButton(g, 66, 307, 38, 25, "+", cellSize >= 24, mouseX, mouseY);
        smallButton(g, 110, 307, 38, 25, "适配", false, mouseX, mouseY);
        int family = ClientPalette.family(selectedColor);
        g.text(font, "当前颜色", 24, 354, MUTED, false);
        drawBead(g, 24, 373, 30, selectedColor);
        g.text(font, ClientPalette.label(selectedColor), 62, 374, TEXT, true);
        g.text(font, ClientPalette.hex(selectedColor), 62, 391, MUTED, false);
        g.text(font, "库存 " + reservoirs[family], 62, 408,
                reservoirs[family] > 0 ? GOOD : BAD, false);
        int actionY = height - 83;
        if (fused) {
            smallButton(g, 22, actionY - 36, 126, 30, "安装挂饰到主手",
                    syncing, mouseX, mouseY);
            smallButton(g, 22, actionY, 126, 30, "制作成品副本",
                    syncing, mouseX, mouseY);
        } else {
            smallButton(g, 22, actionY, 126, 30, "烫豆并锁定",
                    syncing || filled() == 0, mouseX, mouseY);
        }
        g.text(font, syncing ? "正在与服务器同步…" : "拖动画豆 · 右键擦除 · 滚轮缩放",
                22, height - 42, syncing ? 0xFFFFD166 : MUTED, false);
    }

    private void drawCompactEditorTools(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                        StudioLayout layout) {
        int x = layout.leftX();
        panel(g, x, layout.top(), layout.leftWidth(), layout.panelHeight(), PANEL);
        smallButton(g, x + 6, layout.top() + 6, layout.leftWidth() - 12, 18,
                "← 工程", false, mouseX, mouseY);

        int toolY = layout.top() + 34;
        int toolW = 26;
        compactToolButton(g, x + 6, toolY, toolW, "笔", Tool.PENCIL, mouseX, mouseY);
        compactToolButton(g, x + 35, toolY, toolW, "擦", Tool.ERASER, mouseX, mouseY);
        compactToolButton(g, x + 64, toolY, toolW, "取", Tool.PICKER, mouseX, mouseY);

        int historyY = layout.top() + 64;
        smallButton(g, x + 6, historyY, 40, 19, "撤销", undo.isEmpty() || syncing, mouseX, mouseY);
        smallButton(g, x + 50, historyY, 40, 19, "重做", redo.isEmpty() || syncing, mouseX, mouseY);

        int zoomY = layout.top() + 89;
        smallButton(g, x + 6, zoomY, 25, 19, "−", cellSize <= 2, mouseX, mouseY);
        smallButton(g, x + 35, zoomY, 25, 19, "+", cellSize >= 24, mouseX, mouseY);
        smallButton(g, x + 64, zoomY, 26, 19, "合", false, mouseX, mouseY);

        int colorY = layout.top() + 116;
        int family = ClientPalette.family(selectedColor);
        g.text(font, "当前", x + 6, colorY, MUTED, false);
        drawBead(g, x + 6, colorY + 13, 20, selectedColor);
        g.text(font, ClientPalette.hex(selectedColor), x + 32, colorY + 14, TEXT, true);
        g.text(font, "库存 " + reservoirs[family], x + 32, colorY + 27,
                reservoirs[family] > 0 ? GOOD : BAD, false);

        int actionY = height - layout.margin() - 44;
        if (fused) {
            smallButton(g, x + 6, actionY - 24, layout.leftWidth() - 12, 20,
                    "安装到主手", syncing, mouseX, mouseY);
            smallButton(g, x + 6, actionY, layout.leftWidth() - 12, 20,
                    "制作副本", syncing, mouseX, mouseY);
        } else {
            smallButton(g, x + 6, actionY, layout.leftWidth() - 12, 20,
                    "烫豆锁定", syncing || filled() == 0, mouseX, mouseY);
        }
        g.centeredText(font, syncing ? "同步中…" : "右键擦 · 滚轮缩放",
                x + layout.leftWidth() / 2, height - layout.margin() - 16,
                syncing ? 0xFFFFD166 : MUTED);
    }

    private void drawPalette(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        StudioLayout layout = layout();
        int x = paletteX();
        panel(g, x, layout.top(), paletteWidth(), layout.panelHeight(), PANEL);
        if (layout.compact()) {
            g.text(font, "调色盘", x + layout.palettePadding(), layout.top() + 6, TEXT, true);
            String count = Integer.toString(filteredColors.size());
            g.text(font, count, x + paletteWidth() - layout.palettePadding() - font.width(count),
                    layout.top() + 6, MUTED, false);
        } else {
            g.text(font, "Mard 291 色调色盘", x + 12, 62, TEXT, true);
            g.text(font, filteredColors.size() + " 个结果", x + paletteWidth() - 70, 62, MUTED, false);
        }
        int gridTop = layout.paletteGridTop();
        int swatch = layout.paletteSwatch();
        int gap = layout.paletteGap();
        int columns = layout.paletteColumns();
        int rowsVisible = layout.paletteRowsVisible();
        int maxScroll = Math.max(0, (filteredColors.size() + columns - 1) / columns - rowsVisible);
        paletteScroll = Math.max(0, Math.min(maxScroll, paletteScroll));
        for (int slot = 0; slot < rowsVisible * columns; slot++) {
            int index = (paletteScroll * columns) + slot;
            if (index >= filteredColors.size()) break;
            int color = filteredColors.get(index);
            int sx = x + layout.palettePadding() + (slot % columns) * (swatch + gap);
            int sy = gridTop + (slot / columns) * (swatch + gap);
            drawBead(g, sx, sy, swatch, color);
            if (color == selectedColor) g.outline(sx - 2, sy - 2, swatch + 4, swatch + 4, ACCENT);
            if (inside(mouseX, mouseY, sx, sy, swatch, swatch)) {
                int family = ClientPalette.family(color);
                g.outline(sx - 1, sy - 1, swatch + 2, swatch + 2, 0xFFFFFFFF);
                g.setTooltipForNextFrame(Component.literal(ClientPalette.label(color) + "  "
                        + ClientPalette.hex(color) + "\n该色系剩余 " + reservoirs[family] + " 颗"), mouseX, mouseY);
            }
        }
        int selectedY = height - (layout.compact() ? 20 : 48);
        g.text(font, layout.compact() ? "滚轮浏览 · 点击选色"
                        : "滚轮浏览 · 点击选色 · 支持中英文/色值搜索",
                x + layout.palettePadding(), selectedY, MUTED, false);
    }

    private void drawBoard(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int vx = boardX();
        int vy = boardY();
        int vw = boardWidth();
        int vh = boardHeight();
        panel(g, vx, vy, vw, vh, 0xFF090C12);
        g.enableScissor(vx + 1, vy + 1, vx + vw - 1, vy + vh - 1);
        int total = boardSize * cellSize;
        int ox = (int) Math.round(boardOriginX);
        int oy = (int) Math.round(boardOriginY);
        g.fill(ox - 5, oy - 5, ox + total + 5, oy + total + 5, 0xFF6E685C);
        g.fill(ox, oy, ox + total, oy + total, BOARD);

        int startX = Math.max(0, (vx - ox) / Math.max(1, cellSize) - 1);
        int startY = Math.max(0, (vy - oy) / Math.max(1, cellSize) - 1);
        int endX = Math.min(boardSize, (vx + vw - ox) / Math.max(1, cellSize) + 2);
        int endY = Math.min(boardSize, (vy + vh - oy) / Math.max(1, cellSize) + 2);
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int px = ox + x * cellSize;
                int py = oy + y * cellSize;
                int color = pixels[y * boardSize + x];
                if (color >= 0) drawBead(g, px, py, cellSize, color);
                else if (cellSize >= 5) {
                    int dot = Math.max(1, cellSize / 5);
                    int cx = px + (cellSize - dot) / 2;
                    int cy = py + (cellSize - dot) / 2;
                    g.fill(cx, cy, cx + dot, cy + dot, BOARD_DARK);
                }
            }
        }
        if (cellSize >= 4) {
            for (int line = 0; line <= boardSize; line += 8) {
                int color = line % 16 == 0 ? 0x66554F46 : 0x334F4A42;
                g.verticalLine(ox + line * cellSize, oy, oy + total, color);
                g.horizontalLine(ox, ox + total, oy + line * cellSize, color);
            }
        }
        int hover = pixelAt(mouseX, mouseY);
        if (hover >= 0) {
            int hx = ox + (hover % boardSize) * cellSize;
            int hy = oy + (hover / boardSize) * cellSize;
            g.outline(hx, hy, cellSize, cellSize, 0xFFFFFFFF);
        }
        g.disableScissor();
        String boardStatus = layout().compact()
                ? cellSize + "× · " + filled() + "/" + pixels.length + " · " + (fused ? "锁定" : "自动保存")
                : "缩放 " + cellSize + "×  ·  " + filled() + "/" + pixels.length + " 颗  ·  "
                    + (fused ? "已锁定" : "自动保存");
        g.text(font, boardStatus, vx + 8, vy + vh - 16, fused ? GOOD : MUTED, false);
        if (hover >= 0) {
            int color = pixels[hover];
            String info = layout().compact()
                    ? (hover % boardSize) + "," + (hover / boardSize)
                        + (color < 0 ? " · 空白" : " · " + ClientPalette.hex(color))
                    : "坐标 " + (hover % boardSize) + ", " + (hover / boardSize)
                        + (color < 0 ? " · 空白" : " · " + ClientPalette.label(color) + " " + ClientPalette.hex(color));
            g.text(font, info, vx + 8, vy + 7, TEXT, false);
        }
    }

    private void drawToast(GuiGraphicsExtractor g) {
        if (toastTicks <= 0 || toast.isEmpty()) return;
        int w = Math.min(width - 40, Math.max(180, font.width(toast) + 30));
        int x = (width - w) / 2;
        int y = height - 36;
        g.fill(x, y, x + w, y + 25, toastError ? 0xEE5A1F2B : 0xEE173C34);
        g.outline(x, y, w, 25, toastError ? BAD : GOOD);
        g.centeredText(font, toast, width / 2, y + 8, TEXT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        int mx = (int) event.x();
        int my = (int) event.y();
        if (view != View.EDITOR) return clickLibrary(mx, my, event.button());
        if (clickEditorButtons(mx, my, event.button())) return true;
        int paletteColor = paletteColorAt(mx, my);
        if (paletteColor >= 0 && event.button() == 0) {
            selectedColor = paletteColor;
            tool = Tool.PENCIL;
            return true;
        }
        int pixel = pixelAt(mx, my);
        if (pixel < 0) return false;
        if (event.button() == 2) {
            panning = true;
            panStartX = event.x();
            panStartY = event.y();
            panOriginX = boardOriginX;
            panOriginY = boardOriginY;
            panTravel = 0;
            return true;
        }
        if (fused || syncing || (event.button() != 0 && event.button() != 1)) return true;
        if (tool == Tool.PICKER && event.button() == 0) {
            pick(pixel);
            return true;
        }
        painting = true;
        strokeBefore.clear();
        lastStrokePixel = -1;
        int target = event.button() == 1 || tool == Tool.ERASER ? -1 : selectedColor;
        strokeLine(pixel, target);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (panning) {
            boardOriginX = panOriginX + event.x() - panStartX;
            boardOriginY = panOriginY + event.y() - panStartY;
            panTravel += Math.abs(dragX) + Math.abs(dragY);
            return true;
        }
        if (painting) {
            int pixel = pixelAt(event.x(), event.y());
            if (pixel >= 0) {
                int target = event.button() == 1 || tool == Tool.ERASER ? -1 : selectedColor;
                strokeLine(pixel, target);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (panning && event.button() == 2) {
            panning = false;
            if (panTravel < 3) {
                int pixel = pixelAt(event.x(), event.y());
                if (pixel >= 0) pick(pixel);
            }
            return true;
        }
        if (painting) {
            painting = false;
            endStroke();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (view == View.EDITOR && inside(mouseX, mouseY, paletteX(), layout().top(),
                paletteWidth(), layout().panelHeight())) {
            paletteScroll -= (int) Math.signum(vertical);
            paletteScroll = Math.max(0, paletteScroll);
            return true;
        }
        if (view == View.EDITOR && inside(mouseX, mouseY, boardX(), boardY(), boardWidth(), boardHeight())) {
            zoomAt(mouseX, mouseY, cellSize + (vertical > 0 ? 1 : -1));
            return true;
        }
        if (view == View.PROJECTS) {
            libraryScroll = Math.max(0, libraryScroll - (int) (vertical * 28));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (view == View.EDITOR && (colorSearch == null || !colorSearch.isFocused())) {
            if (event.key() == InputConstants.KEY_P) { tool = Tool.PENCIL; return true; }
            if (event.key() == InputConstants.KEY_E) { tool = Tool.ERASER; return true; }
            if (event.key() == InputConstants.KEY_I) { tool = Tool.PICKER; return true; }
            if (event.hasControlDown() && event.key() == InputConstants.KEY_Z) {
                if (event.hasShiftDown()) redo(); else undo();
                return true;
            }
            if (event.hasControlDown() && event.key() == InputConstants.KEY_Y) { redo(); return true; }
            if (event.key() == InputConstants.KEY_F) { fitBoard(); return true; }
        }
        return super.keyPressed(event);
    }

    private boolean clickLibrary(int mx, int my, int button) {
        if (button != 0) return false;
        if (layout().compact()) return clickCompactLibrary(mx, my);
        if (inside(mx, my, 22, 91, 134, 30)) { view = View.PROJECTS; rebuildWidgets(); return true; }
        if (inside(mx, my, 22, 127, 134, 30)) { view = View.MATERIALS; rebuildWidgets(); return true; }
        int contentX = 180;
        int contentW = width - contentX - 16;
        if (view == View.PROJECTS) {
            int count = Math.max(1, station.boardSizes().length);
            int cardW = Math.max(76, Math.min(132, (contentW - 8 * (count - 1)) / count));
            for (int i = 0; i < station.boardSizes().length; i++) {
                int x = contentX + i * (cardW + 8);
                if (inside(mx, my, x, 80, cardW, 50)) {
                    PerlerClient.send(ClientPackets.CREATE_PROJECT, station.stationId(), "",
                            station.boardSizes()[i], -1, null, null);
                    toast("正在创建 " + station.boardSizes()[i] + "×" + station.boardSizes()[i] + " 工程…", false);
                    return true;
                }
            }
            int columns = Math.max(1, Math.min(3, contentW / 210));
            int projectW = (contentW - (columns - 1) * 10) / columns;
            int baseY = 170 - libraryScroll;
            for (int i = 0; i < station.projects().size(); i++) {
                int x = contentX + (i % columns) * (projectW + 10);
                int y = baseY + (i / columns) * 74;
                if (inside(mx, my, x, y, projectW, 64)) {
                    PerlerClient.send(ClientPackets.OPEN_PROJECT, station.stationId(),
                            station.projects().get(i).id(), 0, -1, null, null);
                    toast("正在打开工程…", false);
                    return true;
                }
            }
        } else {
            int columns = Math.max(2, Math.min(4, contentW / 160));
            int gap = 10;
            int cardW = (contentW - gap * (columns - 1)) / columns;
            int cardH = Math.max(62, Math.min(88, (height - 126 - gap * 3) / 4));
            for (int family = 0; family < 16; family++) {
                int x = contentX + (family % columns) * (cardW + gap);
                int y = 108 + (family / columns) * (cardH + gap);
                if (inside(mx, my, x, y, cardW, cardH)) {
                    PerlerClient.send(ClientPackets.REFILL_FAMILY, station.stationId(), "",
                            family, -1, null, null);
                    toast("正在补充" + ClientPalette.NAMES[family] + "系…", false);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean clickCompactLibrary(int mx, int my) {
        StudioLayout layout = layout();
        int navX = layout.leftX() + 6;
        int navW = layout.leftWidth() - 12;
        if (inside(mx, my, navX, layout.top() + 20, navW, 22)) {
            view = View.PROJECTS;
            rebuildWidgets();
            return true;
        }
        if (inside(mx, my, navX, layout.top() + 46, navW, 22)) {
            view = View.MATERIALS;
            rebuildWidgets();
            return true;
        }
        int contentX = layout.boardX();
        int contentW = width - contentX - layout.margin();
        if (view == View.PROJECTS) {
            int gap = 6;
            int columns = 2;
            int cardW = (contentW - gap) / columns;
            int sizeTop = layout.top() + 20;
            int sizeH = 34;
            for (int i = 0; i < station.boardSizes().length; i++) {
                int x = contentX + (i % columns) * (cardW + gap);
                int y = sizeTop + (i / columns) * (sizeH + gap);
                if (inside(mx, my, x, y, cardW, sizeH)) {
                    PerlerClient.send(ClientPackets.CREATE_PROJECT, station.stationId(), "",
                            station.boardSizes()[i], -1, null, null);
                    toast("正在创建 " + station.boardSizes()[i] + "×" + station.boardSizes()[i] + " 工程…", false);
                    return true;
                }
            }
            int sizeRows = Math.max(1, (station.boardSizes().length + columns - 1) / columns);
            int projectsTitleY = sizeTop + sizeRows * (sizeH + gap) + 2;
            int projectColumns = contentW >= 300 ? 2 : 1;
            int projectGap = 6;
            int projectW = (contentW - (projectColumns - 1) * projectGap) / projectColumns;
            int baseY = projectsTitleY + 16 - libraryScroll;
            for (int i = 0; i < station.projects().size(); i++) {
                int x = contentX + (i % projectColumns) * (projectW + projectGap);
                int y = baseY + (i / projectColumns) * 58;
                if (inside(mx, my, x, y, projectW, 52)) {
                    PerlerClient.send(ClientPackets.OPEN_PROJECT, station.stationId(),
                            station.projects().get(i).id(), 0, -1, null, null);
                    toast("正在打开工程…", false);
                    return true;
                }
            }
        } else {
            int columns = Math.max(2, Math.min(4, contentW / 80));
            int rows = (16 + columns - 1) / columns;
            int gap = 4;
            int startY = layout.top() + 32;
            int cardW = (contentW - gap * (columns - 1)) / columns;
            int cardH = Math.max(34,
                    (height - layout.margin() - startY - gap * (rows - 1)) / rows);
            for (int family = 0; family < 16; family++) {
                int x = contentX + (family % columns) * (cardW + gap);
                int y = startY + (family / columns) * (cardH + gap);
                if (inside(mx, my, x, y, cardW, cardH)) {
                    PerlerClient.send(ClientPackets.REFILL_FAMILY, station.stationId(), "",
                            family, -1, null, null);
                    toast("正在补充" + ClientPalette.NAMES[family] + "系…", false);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean clickEditorButtons(int mx, int my, int button) {
        if (button != 0) return false;
        StudioLayout layout = layout();
        if (layout.compact()) return clickCompactEditorButtons(mx, my, layout);
        if (inside(mx, my, 22, 66, 126, 26)) {
            view = View.PROJECTS;
            rebuildWidgets();
            return true;
        }
        if (inside(mx, my, 22, 126, 126, 26)) { tool = Tool.PENCIL; return true; }
        if (inside(mx, my, 22, 158, 126, 26)) { tool = Tool.ERASER; return true; }
        if (inside(mx, my, 22, 190, 126, 26)) { tool = Tool.PICKER; return true; }
        if (inside(mx, my, 22, 248, 60, 25)) { undo(); return true; }
        if (inside(mx, my, 88, 248, 60, 25)) { redo(); return true; }
        if (inside(mx, my, 22, 307, 38, 25)) { zoomAt(boardCenterX(), boardCenterY(), cellSize - 1); return true; }
        if (inside(mx, my, 66, 307, 38, 25)) { zoomAt(boardCenterX(), boardCenterY(), cellSize + 1); return true; }
        if (inside(mx, my, 110, 307, 38, 25)) { fitBoard(); return true; }
        int actionY = height - 83;
        if (fused && inside(mx, my, 22, actionY - 36, 126, 30) && !syncing) {
            runAttachAction();
            return true;
        }
        if (inside(mx, my, 22, actionY, 126, 30) && !syncing) {
            runFuseAction();
            return true;
        }
        return false;
    }

    private boolean clickCompactEditorButtons(int mx, int my, StudioLayout layout) {
        int x = layout.leftX();
        if (inside(mx, my, x + 6, layout.top() + 6, layout.leftWidth() - 12, 18)) {
            view = View.PROJECTS;
            rebuildWidgets();
            return true;
        }
        int toolY = layout.top() + 34;
        if (inside(mx, my, x + 6, toolY, 26, 22)) { tool = Tool.PENCIL; return true; }
        if (inside(mx, my, x + 35, toolY, 26, 22)) { tool = Tool.ERASER; return true; }
        if (inside(mx, my, x + 64, toolY, 26, 22)) { tool = Tool.PICKER; return true; }
        int historyY = layout.top() + 64;
        if (inside(mx, my, x + 6, historyY, 40, 19)) { undo(); return true; }
        if (inside(mx, my, x + 50, historyY, 40, 19)) { redo(); return true; }
        int zoomY = layout.top() + 89;
        if (inside(mx, my, x + 6, zoomY, 25, 19)) {
            zoomAt(boardCenterX(), boardCenterY(), cellSize - 1); return true;
        }
        if (inside(mx, my, x + 35, zoomY, 25, 19)) {
            zoomAt(boardCenterX(), boardCenterY(), cellSize + 1); return true;
        }
        if (inside(mx, my, x + 64, zoomY, 26, 19)) { fitBoard(); return true; }
        int actionY = height - layout.margin() - 44;
        if (fused && inside(mx, my, x + 6, actionY - 24, layout.leftWidth() - 12, 20)
                && !syncing) {
            runAttachAction();
            return true;
        }
        if (inside(mx, my, x + 6, actionY, layout.leftWidth() - 12, 20) && !syncing) {
            runFuseAction();
            return true;
        }
        return false;
    }

    private void runFuseAction() {
        if (fused) {
            runReprintAction();
        } else if (filled() > 0) {
            PerlerClient.send(ClientPackets.FUSE_PROJECT, station.stationId(), projectId, 0,
                    revision, null, null);
            syncing = true;
            toast("正在烫豆并生成地图…", false);
        }
    }

    private void runAttachAction() {
        PerlerClient.send(ClientPackets.ATTACH_PROJECT, station.stationId(), projectId, 0,
                revision, null, null);
        syncing = true;
        toast("正在把挂饰安装到主手装备…", false);
    }

    private void runReprintAction() {
        PerlerClient.send(ClientPackets.REPRINT_PROJECT, station.stationId(), projectId, 0,
                revision, null, null);
        syncing = true;
        toast("正在制作副本…", false);
    }

    private void strokeLine(int pixel, int target) {
        if (lastStrokePixel < 0) {
            paintLocal(pixel, target, strokeBefore);
            lastStrokePixel = pixel;
            return;
        }
        int x0 = lastStrokePixel % boardSize;
        int y0 = lastStrokePixel / boardSize;
        int x1 = pixel % boardSize;
        int y1 = pixel / boardSize;
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            paintLocal(y0 * boardSize + x0, target, strokeBefore);
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
        lastStrokePixel = pixel;
    }

    private void paintLocal(int pixel, int target, Map<Integer, Integer> before) {
        if (pixel < 0 || pixel >= pixels.length || target < -1 || target >= ClientPalette.COLORS) return;
        int old = pixels[pixel];
        if (old == target) return;
        if (target >= 0 && reservoirs[ClientPalette.family(target)] <= 0) {
            toast(ClientPalette.NAMES[ClientPalette.family(target)] + "系耗材不足", true);
            return;
        }
        before.putIfAbsent(pixel, old);
        if (old >= 0) reservoirs[ClientPalette.family(old)]++;
        if (target >= 0) reservoirs[ClientPalette.family(target)]--;
        pixels[pixel] = target;
    }

    private void endStroke() {
        lastStrokePixel = -1;
        if (strokeBefore.isEmpty()) return;
        List<Integer> indices = new ArrayList<>();
        List<Integer> before = new ArrayList<>();
        List<Integer> after = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : strokeBefore.entrySet()) {
            if (pixels[entry.getKey()] == entry.getValue()) continue;
            indices.add(entry.getKey());
            before.add(entry.getValue());
            after.add(pixels[entry.getKey()]);
        }
        strokeBefore.clear();
        if (indices.isEmpty()) return;
        Edit edit = new Edit(ints(indices), ints(before), ints(after));
        submit(edit, PendingKind.NORMAL, edit.after());
    }

    private void undo() {
        if (syncing || undo.isEmpty() || fused) return;
        Edit edit = undo.peekLast();
        if (applyTargets(edit.indices(), edit.before())) submit(edit, PendingKind.UNDO, edit.before());
    }

    private void redo() {
        if (syncing || redo.isEmpty() || fused) return;
        Edit edit = redo.peekLast();
        if (applyTargets(edit.indices(), edit.after())) submit(edit, PendingKind.REDO, edit.after());
    }

    private boolean applyTargets(int[] indices, int[] targets) {
        int[] oldPixels = pixels.clone();
        int[] oldReservoirs = reservoirs.clone();
        for (int i = 0; i < indices.length; i++) {
            int old = pixels[indices[i]];
            int target = targets[i];
            if (old == target) continue;
            if (old >= 0) reservoirs[ClientPalette.family(old)]++;
            if (target >= 0) {
                int family = ClientPalette.family(target);
                if (reservoirs[family] <= 0) {
                    pixels = oldPixels;
                    reservoirs = oldReservoirs;
                    toast("材料库存不足，无法完成历史操作", true);
                    return false;
                }
                reservoirs[family]--;
            }
            pixels[indices[i]] = target;
        }
        return true;
    }

    private void submit(Edit edit, PendingKind kind, int[] targets) {
        pendingEdit = edit;
        pendingKind = kind;
        syncing = true;
        PerlerClient.send(ClientPackets.PAINT_BATCH, station.stationId(), projectId, 0,
                revision, edit.indices(), targets);
    }

    private void finishPendingHistory() {
        switch (pendingKind) {
            case NORMAL -> {
                undo.addLast(pendingEdit);
                while (undo.size() > 50) undo.removeFirst();
                redo.clear();
            }
            case UNDO -> {
                if (!undo.isEmpty()) undo.removeLast();
                redo.addLast(pendingEdit);
            }
            case REDO -> {
                if (!redo.isEmpty()) redo.removeLast();
                undo.addLast(pendingEdit);
            }
        }
        pendingEdit = null;
    }

    private void pick(int pixel) {
        int color = pixel < 0 ? -1 : pixels[pixel];
        if (color >= 0) {
            selectedColor = color;
            tool = Tool.PENCIL;
            toast("已吸取 " + ClientPalette.label(color), false);
        }
    }

    private int paletteColorAt(double mouseX, double mouseY) {
        StudioLayout layout = layout();
        int gridTop = layout.paletteGridTop();
        int swatch = layout.paletteSwatch();
        int gap = layout.paletteGap();
        int columns = layout.paletteColumns();
        int localX = (int) mouseX - (paletteX() + layout.palettePadding());
        int localY = (int) mouseY - gridTop;
        if (localX < 0 || localY < 0) return -1;
        int column = localX / (swatch + gap);
        int row = localY / (swatch + gap);
        if (column >= columns || row >= layout.paletteRowsVisible()
                || localX % (swatch + gap) >= swatch || localY % (swatch + gap) >= swatch) return -1;
        int index = (paletteScroll + row) * columns + column;
        return index >= 0 && index < filteredColors.size() ? filteredColors.get(index) : -1;
    }

    private int pixelAt(double mouseX, double mouseY) {
        if (boardSize <= 0 || !inside(mouseX, mouseY, boardX(), boardY(), boardWidth(), boardHeight())) return -1;
        int x = (int) Math.floor((mouseX - boardOriginX) / cellSize);
        int y = (int) Math.floor((mouseY - boardOriginY) / cellSize);
        return x >= 0 && y >= 0 && x < boardSize && y < boardSize ? y * boardSize + x : -1;
    }

    private void fitBoardIfPossible() {
        if (fitPending && boardSize > 0 && width > 400 && height > 200) fitBoard();
    }

    private void fitBoard() {
        if (boardSize <= 0) return;
        cellSize = Math.max(2, Math.min(24,
                Math.min((boardWidth() - 34) / boardSize, (boardHeight() - 42) / boardSize)));
        int total = boardSize * cellSize;
        boardOriginX = boardX() + (boardWidth() - total) / 2.0;
        boardOriginY = boardY() + (boardHeight() - total) / 2.0 - 4;
        fitPending = false;
    }

    private void zoomAt(double anchorX, double anchorY, int wanted) {
        int next = Math.max(2, Math.min(24, wanted));
        if (next == cellSize || boardSize <= 0) return;
        double boardX = (anchorX - boardOriginX) / cellSize;
        double boardY = (anchorY - boardOriginY) / cellSize;
        cellSize = next;
        boardOriginX = anchorX - boardX * cellSize;
        boardOriginY = anchorY - boardY * cellSize;
    }

    private void updateSummary(int filled, long updatedAt) {
        List<ClientPackets.ProjectSummary> projects = new ArrayList<>(station.projects());
        for (int i = 0; i < projects.size(); i++) {
            var item = projects.get(i);
            if (item.id().equals(projectId)) projects.set(i, new ClientPackets.ProjectSummary(item.id(), item.name(),
                    item.ownerName(), item.size(), filled, fused, updatedAt));
        }
        station = new ClientPackets.StationPayload(station.protocol(), station.stationId(), station.ownerName(),
                reservoirs.clone(), station.maxProjects(), station.beadsPerBatch(), station.boardSizes(), List.copyOf(projects));
    }

    private int firstAvailableColor() {
        for (int family = 0; family < reservoirs.length; family++)
            if (reservoirs[family] > 0) return ClientPalette.representative(family);
        return ClientPalette.representative(0);
    }

    private int filled() {
        int count = 0;
        for (int color : pixels) if (color >= 0) count++;
        return count;
    }

    private StudioLayout layout() { return StudioLayout.of(width, height); }
    private int paletteX() { return layout().paletteX(); }
    private int paletteWidth() { return layout().paletteWidth(); }
    private int boardX() { return layout().boardX(); }
    private int boardY() { return layout().top(); }
    private int boardWidth() { return layout().boardWidth(); }
    private int boardHeight() { return layout().boardHeight(); }
    private double boardCenterX() { return boardX() + boardWidth() / 2.0; }
    private double boardCenterY() { return boardY() + boardHeight() / 2.0; }

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
        g.outline(x, y, w, h, 0x334E5A70);
    }

    private void navButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                           boolean active, int mouseX, int mouseY) {
        int color = active ? 0xFF342744 : inside(mouseX, mouseY, x, y, w, h) ? PANEL_2 : PANEL;
        g.fill(x, y, x + w, y + h, color);
        if (active) g.fill(x, y, x + 4, y + h, ACCENT);
        g.text(font, label, x + 14, y + 10, active ? TEXT : MUTED, false);
    }

    private void toolButton(GuiGraphicsExtractor g, int x, int y, String label, Tool value,
                            int mouseX, int mouseY) {
        smallButton(g, x, y, 126, 26, label, false, mouseX, mouseY,
                tool == value ? ACCENT_2 : 0);
    }

    private void compactToolButton(GuiGraphicsExtractor g, int x, int y, int width, String label,
                                   Tool value, int mouseX, int mouseY) {
        smallButton(g, x, y, width, 22, label, false, mouseX, mouseY,
                tool == value ? ACCENT_2 : 0);
    }

    private void smallButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                             boolean disabled, int mouseX, int mouseY) {
        smallButton(g, x, y, w, h, label, disabled, mouseX, mouseY, 0);
    }

    private void smallButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                             boolean disabled, int mouseX, int mouseY, int selected) {
        int color = disabled ? 0xFF171B23 : selected != 0 ? 0xFF382D55
                : inside(mouseX, mouseY, x, y, w, h) ? 0xFF2B3342 : PANEL_2;
        g.fill(x, y, x + w, y + h, color);
        g.outline(x, y, w, h, selected != 0 ? selected : 0x445D6B80);
        g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, disabled ? 0xFF596170 : TEXT);
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int w, int h,
                      int mouseX, int mouseY, boolean completed) {
        int color = inside(mouseX, mouseY, x, y, w, h) ? 0xFF252D3B : PANEL;
        g.fill(x, y, x + w, y + h, color);
        g.outline(x, y, w, h, completed ? 0x6655E6A5 : 0x445D6B80);
    }

    private void drawBead(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int rgb = ClientPalette.argb(color);
        if (size <= 3) {
            g.fill(x, y, x + size, y + size, rgb);
            return;
        }
        int inset = size >= 8 ? 1 : 0;
        g.fill(x + inset, y + inset, x + size - inset, y + size - inset, rgb);
        if (size >= 7) {
            g.fill(x, y, x + 2, y + 2, BOARD);
            g.fill(x + size - 2, y, x + size, y + 2, BOARD);
            g.fill(x, y + size - 2, x + 2, y + size, BOARD);
            g.fill(x + size - 2, y + size - 2, x + size, y + size, BOARD);
            int hole = Math.max(2, size / 4);
            int hx = x + (size - hole) / 2;
            int hy = y + (size - hole) / 2;
            g.fill(hx, hy, hx + hole, hy + hole, 0xFF352F2A);
            g.horizontalLine(x + 2, x + size - 3, y + 1, 0x55FFFFFF);
        }
    }

    private void toast(String message, boolean error) {
        toast = message == null ? "" : message;
        toastError = error;
        toastTicks = error ? 100 : 55;
    }

    private static boolean inside(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    private static int[] ints(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private record Edit(int[] indices, int[] before, int[] after) {}
}
