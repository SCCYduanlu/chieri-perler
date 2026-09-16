package com.chiericat.perler;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class PerlerItems {
    static final String DIRECT_ART_TAG = "chieri_perler_direct_art";
    private static final String KIND = "chieri_perler_kind";
    private static final String STATION_ID = "chieri_perler_station";
    private static final String PROJECT_ID = "chieri_perler_project";
    private static final String PROJECT_NAME = "chieri_perler_project_name";
    private static final String MAP_ID = "chieri_perler_map_id";
    private static final String PALETTE_VERSION = "chieri_perler_palette_version";
    private static final String ART_SIZE = "chieri_perler_art_size";
    private static final String ART_COLORS = "chieri_perler_art_colors";
    private static final String ART_FILLED = "chieri_perler_art_filled";
    private static final String CHARM_MARKER = "chieri_perler_charm";
    private static final String CHARM_SIZE = "chieri_perler_charm_size";
    private static final String CHARM_COLORS = "chieri_perler_charm_colors";
    private static final String CHARM_FILLED = "chieri_perler_charm_filled";
    private static final String OLD_GLINT = "chieri_perler_old_glint";
    private static final String TABLE = "table";
    private static final String ART = "art";
    private static final String CHARM_LORE_PREFIX = "拼豆挂饰：";

    private static final Set<Item> CHARMABLE = Set.of(
            Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD,
            Items.COPPER_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE,
            Items.COPPER_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE,
            Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE,
            Items.COPPER_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
            Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.GOLDEN_SHOVEL,
            Items.COPPER_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL,
            Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.GOLDEN_HOE,
            Items.COPPER_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE,
            Items.WOODEN_SPEAR, Items.STONE_SPEAR, Items.COPPER_SPEAR, Items.IRON_SPEAR,
            Items.GOLDEN_SPEAR, Items.DIAMOND_SPEAR, Items.NETHERITE_SPEAR,
            Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE, Items.SHIELD,
            Items.SHEARS, Items.FISHING_ROD, Items.BRUSH, Items.FLINT_AND_STEEL,
            Items.CARROT_ON_A_STICK, Items.WARPED_FUNGUS_ON_A_STICK
    );

    private PerlerItems() {}

    static ItemStack table(String stationId) {
        ItemStack stack = new ItemStack(Items.CRAFTER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("拼豆台")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("放置后右键：补充耗材、创建与编辑拼豆工程").withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("支持 Mard 291 色与 16/32/64/128 尺寸").withStyle(ChatFormatting.AQUA));
        if (stationId != null && !stationId.isBlank()) {
            lore.add(Component.literal("台子 ID：" + stationId).withStyle(ChatFormatting.DARK_GRAY));
            lore.add(Component.literal("工程数据已安全保存在世界中").withStyle(ChatFormatting.GREEN));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CompoundTag tag = new CompoundTag();
        tag.putString(KIND, TABLE);
        if (stationId != null && !stationId.isBlank()) tag.putString(STATION_ID, stationId);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    static ItemStack art(PerlerState.Project project) {
        if (!project.fused || project.mapId < 0) throw new IllegalArgumentException("工程尚未烫豆。 ");
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, new MapId(project.mapId));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("拼豆成品 · " + project.name)
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(project.size + "×" + project.size + "｜" + project.filled() + " 颗拼豆")
                        .withStyle(ChatFormatting.AQUA),
                Component.literal("手持右键墙面、地面或天花板可直接张贴").withStyle(ChatFormatting.GRAY),
                Component.literal("可在拼豆台直接安装，或放副手用 /pindou charm 安装挂饰").withStyle(ChatFormatting.GRAY),
                Component.literal("作品 ID：" + project.id).withStyle(ChatFormatting.DARK_GRAY))));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CompoundTag tag = new CompoundTag();
        tag.putString(KIND, ART);
        tag.putString(PROJECT_ID, project.id);
        tag.putString(PROJECT_NAME, project.name);
        tag.putInt(MAP_ID, project.mapId);
        putPixels(tag, project, ART_SIZE, ART_COLORS, ART_FILLED);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    static boolean isTable(ItemStack stack) {
        return TABLE.equals(kind(stack));
    }

    static String stationId(ItemStack stack) {
        return tag(stack).getStringOr(STATION_ID, "");
    }

    static boolean isArt(ItemStack stack) {
        return ART.equals(kind(stack));
    }

    static String projectId(ItemStack stack) {
        return tag(stack).getStringOr(PROJECT_ID, "");
    }

    static boolean charmable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && CHARMABLE.contains(stack.getItem());
    }

    static boolean hasCharm(ItemStack stack) {
        return tag(stack).getBooleanOr(CHARM_MARKER, false);
    }

    static void attachCharm(ItemStack weapon, PerlerState.Project project) {
        if (!charmable(weapon)) throw new IllegalArgumentException("主手必须持武器或工具，副手持拼豆成品。 ");
        if (hasCharm(weapon)) throw new IllegalArgumentException("这件武器已经安装了拼豆挂饰。 ");
        CompoundTag tag = tag(weapon);
        Boolean previousGlint = weapon.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        tag.putBoolean(CHARM_MARKER, true);
        tag.putInt(OLD_GLINT, previousGlint == null ? 0 : previousGlint ? 1 : 2);
        tag.putString(PROJECT_ID, project.id);
        tag.putString(PROJECT_NAME, project.name);
        tag.putInt(MAP_ID, project.mapId);
        putCharmPixels(tag, project);
        CustomData.set(DataComponents.CUSTOM_DATA, weapon, tag);
        List<Component> lore = new ArrayList<>();
        ItemLore old = weapon.get(DataComponents.LORE);
        if (old != null) lore.addAll(old.lines());
        lore.add(Component.literal(CHARM_LORE_PREFIX + project.name).withStyle(ChatFormatting.LIGHT_PURPLE));
        lore.add(Component.literal("攻击时绽放拼豆光效").withStyle(ChatFormatting.GRAY));
        weapon.set(DataComponents.LORE, new ItemLore(lore));
        weapon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    static Charm removeCharm(ItemStack weapon) {
        if (!hasCharm(weapon)) throw new IllegalArgumentException("主手武器或工具没有安装拼豆挂饰。 ");
        CompoundTag tag = tag(weapon);
        Charm charm = new Charm(tag.getStringOr(PROJECT_ID, ""), tag.getStringOr(PROJECT_NAME, "拼豆作品"),
                tag.getIntOr(MAP_ID, -1));
        int oldGlint = tag.getIntOr(OLD_GLINT, 0);
        tag.remove(CHARM_MARKER);
        tag.remove(OLD_GLINT);
        tag.remove(PROJECT_ID);
        tag.remove(PROJECT_NAME);
        tag.remove(MAP_ID);
        tag.remove(CHARM_SIZE);
        tag.remove(CHARM_COLORS);
        tag.remove(CHARM_FILLED);
        tag.remove(PALETTE_VERSION);
        CustomData.set(DataComponents.CUSTOM_DATA, weapon, tag);
        ItemLore old = weapon.get(DataComponents.LORE);
        if (old != null) {
            List<Component> lore = old.lines().stream()
                    .filter(line -> !line.getString().startsWith(CHARM_LORE_PREFIX)
                            && !line.getString().equals("攻击时绽放拼豆光效"))
                    .toList();
            if (lore.isEmpty()) weapon.remove(DataComponents.LORE);
            else weapon.set(DataComponents.LORE, new ItemLore(lore));
        }
        if (oldGlint == 1) weapon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        else if (oldGlint == 2) weapon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        else weapon.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        return charm;
    }

    static Charm charm(ItemStack weapon) {
        if (!hasCharm(weapon)) throw new IllegalArgumentException("主手武器或工具没有安装拼豆挂饰。 ");
        CompoundTag tag = tag(weapon);
        return new Charm(tag.getStringOr(PROJECT_ID, ""), tag.getStringOr(PROJECT_NAME, "拼豆作品"),
                tag.getIntOr(MAP_ID, -1));
    }

    static boolean ensureCharmPixels(ItemStack weapon, PerlerState.Project project) {
        if (!hasCharm(weapon)) return false;
        CompoundTag tag = tag(weapon);
        int expected = project.size * project.size;
        boolean valid = tag.getIntOr(CHARM_SIZE, 0) == project.size
                && tag.getIntOr(PALETTE_VERSION, 0) == PerlerPalette.VERSION
                && tag.getByteArray(CHARM_COLORS).map(bytes -> bytes.length == expected * 2).orElse(false)
                && tag.getByteArray(CHARM_FILLED).map(bytes -> bytes.length == (expected + 7) / 8).orElse(false);
        if (valid) return false;
        putCharmPixels(tag, project);
        CustomData.set(DataComponents.CUSTOM_DATA, weapon, tag);
        return true;
    }

    private static void putCharmPixels(CompoundTag tag, PerlerState.Project project) {
        putPixels(tag, project, CHARM_SIZE, CHARM_COLORS, CHARM_FILLED);
    }

    static boolean ensureArtPixels(ItemStack art, PerlerState.Project project) {
        if (!isArt(art)) return false;
        CompoundTag tag = tag(art);
        int expected = project.size * project.size;
        boolean valid = tag.getIntOr(PALETTE_VERSION, 0) == PerlerPalette.VERSION
                && tag.getIntOr(ART_SIZE, 0) == project.size
                && tag.getByteArray(ART_COLORS).map(bytes -> bytes.length == expected * 2).orElse(false)
                && tag.getByteArray(ART_FILLED).map(bytes -> bytes.length == (expected + 7) / 8).orElse(false);
        if (valid) return false;
        putPixels(tag, project, ART_SIZE, ART_COLORS, ART_FILLED);
        CustomData.set(DataComponents.CUSTOM_DATA, art, tag);
        return true;
    }

    private static void putPixels(CompoundTag tag, PerlerState.Project project,
                                  String sizeKey, String colorsKey, String filledKey) {
        int count = project.size * project.size;
        byte[] colors = new byte[count * 2];
        byte[] filled = new byte[(count + 7) / 8];
        for (int pixel = 0; pixel < count; pixel++) {
            int color = project.colors[pixel];
            if (color < 0) continue;
            colors[pixel * 2] = (byte) (color >>> 8);
            colors[pixel * 2 + 1] = (byte) color;
            filled[pixel >> 3] |= (byte) (1 << (pixel & 7));
        }
        tag.putInt(PALETTE_VERSION, PerlerPalette.VERSION);
        tag.putInt(sizeKey, project.size);
        tag.putByteArray(colorsKey, colors);
        tag.putByteArray(filledKey, filled);
    }

    private static String kind(ItemStack stack) {
        return tag(stack).getStringOr(KIND, "");
    }

    private static CompoundTag tag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    record Charm(String projectId, String projectName, int mapId) {}
}
