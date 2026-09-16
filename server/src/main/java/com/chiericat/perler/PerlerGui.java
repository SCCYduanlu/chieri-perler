package com.chiericat.perler;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.SimpleGuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PerlerGui {
    private static final Map<UUID, Integer> SELECTED = new HashMap<>();
    private static final int PIXELS_PER_PAGE = 35;

    private PerlerGui() {}

    static void clear(UUID playerId) {
        SELECTED.remove(playerId);
    }

    static void openMain(ServerPlayer player, PerlerState.Station station) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("拼豆台 · " + station.id).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        fill(gui, 54, Items.STAINED_GLASS_PANE.gray());
        int total = 0;
        for (int value : station.reservoirs) total += value;
        gui.setSlot(4, label(Items.CRAFTER, "拼豆台 " + station.id, List.of(
                "主人：" + station.ownerName,
                "总耗材：" + total + " 颗｜工程：" + station.projects.size() + "/" + PerlerMod.controller.config.maxProjectsPerTable)));
        gui.setSlot(0, button(Items.HONEYCOMB, "材料仓 · 16 色系", List.of(
                "1 染料 + 1 蜜脾 = " + PerlerMod.controller.config.beadsPerBatch + " 颗同系拼豆",
                "16 种基础染料共同覆盖 Mard 291 色"), () -> openMaterials(player, station)));
        gui.setSlot(8, button(Items.PAPER, "创建拼豆板", List.of(
                "可选 16×16、32×32、64×64、128×128",
                "每个玩家可在同一台子保存多个工程"), () -> openSizes(player, station)));

        List<PerlerState.Project> projects = station.projects.values().stream()
                .sorted(Comparator.comparingLong(project -> project.createdAt)).toList();
        int[] slots = {9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26};
        for (int i = 0; i < projects.size() && i < slots.length; i++) {
            PerlerState.Project project = projects.get(i);
            Item icon = project.fused ? Items.FILLED_MAP : Items.MAP;
            List<String> lore = new ArrayList<>();
            lore.add("作者：" + project.ownerName + "｜尺寸：" + project.size + "×" + project.size);
            lore.add("进度：" + project.filled() + "/" + project.colors.length + " 颗");
            lore.add(project.fused ? "已烫豆锁定｜点击查看或制作副本" : "进行中｜点击继续拼豆");
            lore.add("工程 ID：" + project.id);
            gui.setSlot(slots[i], button(icon, (project.fused ? "成品 · " : "工程 · ") + project.name,
                    lore, () -> openProject(player, station, project)));
        }
        gui.setSlot(45, label(Items.BOOK, "操作提示", List.of(
                "编辑时先选择 Mard 291 色之一，再点击格子放豆",
                "再次点击相同颜色可取回拼豆耗材",
                "每次点击都会立即写入世界数据")));
        gui.setSlot(49, label(Items.IRON_INGOT, "烫豆说明", List.of(
                "至少放置 1 颗豆后可烫豆",
                "消耗 1 煤炭 + 1 空地图，生成锁定地图成品")));
        gui.setSlot(53, button(Items.OAK_DOOR, "关闭", List.of(), () -> gui.close(false)));
        gui.open();
    }

    private static void openMaterials(ServerPlayer player, PerlerState.Station station) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("拼豆材料仓 · 点击直接补充").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        fill(gui, 27, Items.STAINED_GLASS_PANE.black());
        int[] slots = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
        for (int family = 0; family < PerlerPalette.FAMILIES; family++) {
            int value = family;
            gui.setSlot(slots[family], button(PerlerPalette.DYES[family], PerlerPalette.NAMES[family] + "系 · "
                    + station.reservoirs[family] + " 颗", List.of(
                    "1 染料 + 1 蜜脾 → " + PerlerMod.controller.config.beadsPerBatch + " 颗",
                    "覆盖该基础染料对应的标准色号",
                    "点击立即补充"),
                    () -> action(player, () -> PerlerMod.controller.refill(player, station, value),
                            () -> openMaterials(player, station))));
        }
        gui.setSlot(22, label(Items.HONEYCOMB, "耗材原理", List.of(
                "Minecraft 的 16 种基础染料负责色系",
                "291 个固定色号按最接近的基础染料归仓",
                "蜜脾代表用于成型的塑料耗材")));
        gui.setSlot(18, button(Items.ARROW, "返回拼豆台", List.of(), () -> openMain(player, station)));
        gui.setSlot(26, button(Items.OAK_DOOR, "关闭", List.of(), () -> gui.close(false)));
        gui.open();
    }

    private static void openSizes(ServerPlayer player, PerlerState.Station station) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("选择拼豆板尺寸").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        fill(gui, 27, Items.STAINED_GLASS_PANE.lightBlue());
        int[] slots = {10, 12, 14, 16, 20, 22, 24, 26};
        for (int i = 0; i < PerlerMod.controller.config.boardSizes.length; i++) {
            int size = PerlerMod.controller.config.boardSizes[i];
            gui.setSlot(slots[i], button(Items.MAP, size + "×" + size + " 拼豆板", List.of(
                    "共 " + (size * size) + " 个拼豆位",
                    "成品会等比放大为一张 128×128 地图",
                    "点击立即建立可持续保存的工程"),
                    () -> action(player, () -> {
                        PerlerState.Project project = PerlerMod.controller.createProject(player, station, size);
                        SELECTED.put(player.getUUID(), 0);
                        openEditor(player, station, project, 0);
                    }, () -> {})));
        }
        gui.setSlot(18, button(Items.ARROW, "返回", List.of(), () -> openMain(player, station)));
        gui.open();
    }

    private static void openProject(ServerPlayer player, PerlerState.Station station, PerlerState.Project project) {
        if (!project.fused) {
            openEditor(player, station, project, 0);
            return;
        }
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("拼豆成品 · " + project.name).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        fill(gui, 27, Items.STAINED_GLASS_PANE.purple());
        gui.setSlot(4, label(Items.FILLED_MAP, project.name, List.of(
                "作者：" + project.ownerName,
                "尺寸：" + project.size + "×" + project.size + "｜用豆：" + project.filled(),
                "工程 ID：" + project.id)));
        gui.setSlot(11, button(Items.LEAD, "安装挂饰到主手", List.of(
                "主手持需要安装的武器或工具",
                "消耗 1 张空地图，无需把成品放在副手"),
                () -> action(player, () -> PerlerMod.controller.attachCharmFromTable(player, project),
                        () -> openProject(player, station, project))));
        gui.setSlot(15, button(Items.MAP, "制作成品副本", List.of(
                "消耗 1 张空地图",
                "副本可直接张贴或安装为武器、工具挂饰"),
                () -> action(player, () -> PerlerMod.controller.reprint(player, project),
                        () -> openProject(player, station, project))));
        gui.setSlot(18, button(Items.ARROW, "返回拼豆台", List.of(), () -> openMain(player, station)));
        gui.setSlot(26, button(Items.OAK_DOOR, "关闭", List.of(), () -> gui.close(false)));
        gui.open();
    }

    private static void openFamilies(ServerPlayer player, PerlerState.Station station,
                                     PerlerState.Project project, int page) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("选择基础色系").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        fill(gui, 27, Items.STAINED_GLASS_PANE.gray());
        int[] slots = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
        for (int family = 0; family < PerlerPalette.FAMILIES; family++) {
            int value = family;
            gui.setSlot(slots[family], button(PerlerPalette.DYES[family], PerlerPalette.NAMES[family]
                    + "系 · 剩余 " + station.reservoirs[family], List.of(
                    "点击查看该基础染料覆盖的 " + PerlerPalette.colorsForFamily(family).length + " 个色号"),
                    () -> openShades(player, station, project, page, value, 0)));
        }
        gui.setSlot(18, button(Items.ARROW, "返回画板", List.of(), () -> openEditor(player, station, project, page)));
        gui.setSlot(22, button(Items.HONEYCOMB, "去材料仓补充", List.of(), () -> openMaterials(player, station)));
        gui.open();
    }

    private static void openShades(ServerPlayer player, PerlerState.Station station,
                                   PerlerState.Project project, int page, int family, int requestedColorPage) {
        int[] colors = PerlerPalette.colorsForFamily(family);
        int colorPages = Math.max(1, (colors.length + 17) / 18);
        int colorPage = Math.max(0, Math.min(colorPages - 1, requestedColorPage));
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal(PerlerPalette.NAMES[family] + "系 · 选择明暗色")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        fill(gui, 27, Items.STAINED_GLASS_PANE.black());
        for (int local = 0; local < 18; local++) {
            int index = colorPage * 18 + local;
            if (index >= colors.length) break;
            int color = colors[index];
            gui.setSlot(local, swatch(color, List.of(
                    "RGB #" + String.format("%06X", PerlerPalette.rgb(color)),
                    "共享耗材剩余：" + station.reservoirs[family] + " 颗",
                    "点击选中并返回画板"), () -> {
                SELECTED.put(player.getUUID(), color);
                openEditor(player, station, project, page);
            }));
        }
        gui.setSlot(18, button(Items.ARROW, "返回色系", List.of(), () -> openFamilies(player, station, project, page)));
        if (colorPage > 0) gui.setSlot(19, button(Items.SPECTRAL_ARROW, "上一页", List.of(),
                () -> openShades(player, station, project, page, family, colorPage - 1)));
        gui.setSlot(22, label(Items.BOOK, "色号 " + (colorPage + 1) + "/" + colorPages,
                List.of("本色系共 " + colors.length + " 色")));
        if (colorPage + 1 < colorPages) gui.setSlot(25, button(Items.SPECTRAL_ARROW, "下一页", List.of(),
                () -> openShades(player, station, project, page, family, colorPage + 1)));
        gui.setSlot(26, button(Items.OAK_DOOR, "关闭", List.of(), () -> gui.close(false)));
        gui.open();
    }

    private static void openEditor(ServerPlayer player, PerlerState.Station station,
                                   PerlerState.Project project, int requestedPage) {
        int pages = Math.max(1, (project.colors.length + PIXELS_PER_PAGE - 1) / PIXELS_PER_PAGE);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        int selected = SELECTED.getOrDefault(player.getUUID(), 0);
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal(project.name + " · " + project.size + "×" + project.size
                + " · " + (page + 1) + "/" + pages).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        fill(gui, 54, Items.STAINED_GLASS_PANE.black());
        for (int cell = 0; cell < PIXELS_PER_PAGE; cell++) {
            int pixel = page * PIXELS_PER_PAGE + cell;
            int slot = (cell / 7) * 9 + (cell % 7);
            if (pixel >= project.colors.length) {
                gui.setSlot(slot, new GuiElementBuilder(Items.BARRIER).setName(Component.empty()).hideTooltip().build());
                continue;
            }
            int x = pixel % project.size;
            int y = pixel / project.size;
            gui.setSlot(slot, pixelButton(project.colors[pixel], x, y, () -> action(player, () -> {
                PerlerMod.controller.paint(player, station, project, pixel, selected);
                int now = project.colors[pixel];
                gui.setSlot(slot, pixelButton(now, x, y, null));
                gui.setSlot(7, selectedButton(selected, station, () -> openFamilies(player, station, project, page)));
                gui.setSlot(25, progressButton(project, page, pages));
            }, () -> {})));
        }
        gui.setSlot(7, selectedButton(selected, station, () -> openFamilies(player, station, project, page)));
        gui.setSlot(8, button(Items.FLOWER_BANNER_PATTERN, "打开 Mard 291 色调色板", List.of("固定色号 · 16 个基础耗材仓"),
                () -> openFamilies(player, station, project, page)));
        gui.setSlot(16, button(Items.SPECTRAL_ARROW, "向前 10 页", List.of(),
                () -> openEditor(player, station, project, page - 10)));
        gui.setSlot(17, button(Items.ARROW, "上一页", List.of(),
                () -> openEditor(player, station, project, page - 1)));
        gui.setSlot(25, progressButton(project, page, pages));
        gui.setSlot(26, button(Items.ARROW, "下一页", List.of(),
                () -> openEditor(player, station, project, page + 1)));
        gui.setSlot(34, button(Items.SPECTRAL_ARROW, "向后 10 页", List.of(),
                () -> openEditor(player, station, project, page + 10)));
        gui.setSlot(35, button(Items.COMPASS, "回到第一页", List.of(),
                () -> openEditor(player, station, project, 0)));
        gui.setSlot(43, button(Items.HONEYCOMB, "材料仓", List.of("耗材不足时在这里补充"),
                () -> openMaterials(player, station)));
        gui.setSlot(44, button(Items.CRAFTER, "工程列表", List.of("进度已经实时保存"),
                () -> openMain(player, station)));
        if (project.fused) {
            gui.setSlot(52, label(Items.FILLED_MAP, "已烫豆锁定", List.of("工程不能再编辑", "可在工程列表制作副本")));
        } else {
            gui.setSlot(52, button(Items.FIRE_CHARGE, "烫豆并锁定", List.of(
                    "消耗 1 煤炭 + 1 空地图",
                    "当前已放置 " + project.filled() + " 颗",
                    "烫豆后不可继续编辑"),
                    () -> action(player, () -> PerlerMod.controller.fuse(player, station, project),
                            () -> openProject(player, station, project))));
        }
        gui.setSlot(53, button(Items.OAK_DOOR, "关闭", List.of("所有进度已保存"), () -> gui.close(false)));
        gui.open();
    }

    private static GuiElement selectedButton(int selected, PerlerState.Station station, Runnable callback) {
        int family = PerlerPalette.family(selected);
        return swatch(selected, List.of(
                "RGB #" + String.format("%06X", PerlerPalette.rgb(selected)),
                "该色系剩余：" + station.reservoirs[family] + " 颗",
                "点击更换颜色"), callback);
    }

    private static GuiElement progressButton(PerlerState.Project project, int page, int pages) {
        int start = page * PIXELS_PER_PAGE;
        int end = Math.min(project.colors.length, start + PIXELS_PER_PAGE);
        return label(Items.CLOCK, "画板进度 " + project.filled() + "/" + project.colors.length, List.of(
                "第 " + (page + 1) + "/" + pages + " 页",
                "本页对应像素序号 " + (start + 1) + "–" + end,
                "点击同一种颜色可拆下并返还耗材"));
    }

    private static GuiElement pixelButton(int color, int x, int y, Runnable callback) {
        if (color < 0) return button(Items.STAINED_GLASS_PANE.lightGray(), "空白像素 · (" + x + ", " + y + ")",
                List.of("点击放置当前选中的拼豆"), callback);
        return swatch(color, List.of(
                "坐标：(" + x + ", " + y + ")",
                "RGB #" + String.format("%06X", PerlerPalette.rgb(color)),
                "选中相同颜色再点击可拆下；选中其他颜色可替换"), callback);
    }

    private static GuiElement swatch(int color, List<String> lore, Runnable callback) {
        ItemStack stack = new ItemStack(Items.LEATHER_HORSE_ARMOR);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(PerlerPalette.rgb(color)));
        GuiElementBuilder builder = new GuiElementBuilder(stack)
                .setName(Component.literal(PerlerPalette.label(color)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .setLore(lore.stream().<Component>map(line -> Component.literal(line).withStyle(ChatFormatting.GRAY)).toList());
        if (callback != null) builder.setCallback((index, click, action, handler) -> callback.run());
        return builder.build();
    }

    private static GuiElement label(Item item, String name, List<String> lore) {
        return button(item, name, lore, null);
    }

    private static GuiElement button(Item item, String name, List<String> lore, Runnable callback) {
        GuiElementBuilder builder = new GuiElementBuilder(item)
                .setName(Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .setLore(lore.stream().<Component>map(line -> Component.literal(line).withStyle(ChatFormatting.GRAY)).toList());
        if (callback != null) builder.setCallback((index, click, action, handler) -> callback.run());
        return builder.build();
    }

    private static void fill(SimpleGui gui, int size, Item item) {
        SimpleGuiElement filler = new GuiElementBuilder(item).setName(Component.empty()).hideTooltip().build();
        for (int slot = 0; slot < size; slot++) gui.setSlot(slot, filler);
    }

    private static void action(ServerPlayer player, Checked work, Runnable after) {
        player.level().getServer().execute(() -> {
            try {
                work.run();
                after.run();
            } catch (Exception exception) {
                PerlerMod.message(player, PerlerMod.safe(exception));
            }
        });
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
