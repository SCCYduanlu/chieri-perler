package com.chiericat.perler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class InventoryOps {
    private InventoryOps() {}

    static int count(ServerPlayer player, Item item) {
        int result = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }

    static void require(ServerPlayer player, Item item, int count, String name) {
        if (count(player, item) < count) throw new IllegalArgumentException("需要 " + count + " 个" + name + "。 ");
    }

    static void remove(ServerPlayer player, Item item, int amount) {
        require(player, item, amount, item.toString());
        int left = amount;
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
        changed(player);
    }

    static boolean canFit(ServerPlayer player, ItemStack addition) {
        List<ItemStack> simulated = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) simulated.add(player.getInventory().getItem(slot).copy());
        int left = addition.getCount();
        int max = addition.getItem().getDefaultMaxStackSize();
        for (ItemStack existing : simulated) {
            if (left == 0) break;
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, addition)) {
                int move = Math.min(left, max - existing.getCount());
                existing.grow(move);
                left -= move;
            }
        }
        for (ItemStack existing : simulated) {
            if (left == 0) break;
            if (existing.isEmpty()) left -= Math.min(left, max);
        }
        return left == 0;
    }

    static void add(ServerPlayer player, ItemStack item) {
        if (!canFit(player, item)) throw new IllegalArgumentException("背包空间不足。 ");
        ItemStack copy = item.copy();
        player.getInventory().add(copy);
        if (!copy.isEmpty()) throw new IllegalStateException("物品发放失败，背包状态可能已变化。 ");
        changed(player);
    }

    static void changed(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
}
