package com.chiericat.perler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class PerlerMod implements ModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("ChieriPerler");
    static PerlerController controller;
    private static String startupError = "拼豆工坊正在初始化。 ";

    @Override
    public void onInitialize() {
        PerlerNetworking.register();
        registerCommands();
        registerEvents();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Path configPath = FabricLoader.getInstance().getConfigDir().resolve("chieri-perler.json");
                PerlerConfig config = Files.isRegularFile(configPath)
                        ? gson.fromJson(Files.readString(configPath, StandardCharsets.UTF_8), PerlerConfig.class)
                        : new PerlerConfig();
                if (config == null) config = new PerlerConfig();
                config.validate();
                atomic(configPath, gson.toJson(config));
                Path root = server.getWorldPath(LevelResource.ROOT).resolve("data/chieri-perler");
                Path statePath = root.resolve("state.json");
                PerlerState state = PerlerState.load(statePath, config);
                controller = new PerlerController(server, config, state, statePath);
                controller.start();
                startupError = "";
                LOG.info("Chieri Perler ready: {}", controller.status());
            } catch (Exception exception) {
                startupError = "拼豆配置或工程数据异常，已为保护作品停止服务。 ";
                controller = null;
                LOG.error("Chieri Perler initialization failed", exception);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (controller != null) controller.shutdown();
            controller = null;
        });
        LOG.info("Chieri Perler registered: authoritative tables, direct art displays, optional client studio, Mard 291 colors and equipment charms");
    }

    private static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (controller != null) controller.tick();
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (controller != null) controller.ensureEntityArt(entity);
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (controller == null || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            return controller.useBlock(serverPlayer, hand, hit);
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                controller == null || !(level instanceof ServerLevel serverLevel)
                        || !(player instanceof ServerPlayer serverPlayer)
                        || controller.beforeBreak(serverLevel, serverPlayer, pos));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (controller != null && player instanceof ServerPlayer serverPlayer
                    && controller.removeDirectArt(serverPlayer, entity)) return InteractionResult.SUCCESS_SERVER;
            if (controller != null && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer)
                controller.charmEffect(serverPlayer);
            return InteractionResult.PASS;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PerlerGui.clear(handler.player.getUUID()));
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(command("pindou"));
            dispatcher.register(command("perler"));
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String root) {
        return Commands.literal(root)
                .executes(context -> run(context.getSource(), PerlerMod::help))
                .then(Commands.literal("craft").executes(context -> run(context.getSource(),
                        player -> controller.craft(player))))
                .then(Commands.literal("charm").executes(context -> run(context.getSource(),
                        player -> controller.attachCharm(player))))
                .then(Commands.literal("uncharm").executes(context -> run(context.getSource(),
                        player -> controller.detachCharm(player))))
                .then(Commands.literal("admin")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .then(Commands.literal("give").executes(context -> run(context.getSource(),
                                player -> controller.giveBlank(player))))
                        .then(Commands.literal("status").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(controller == null
                                    ? startupError : controller.status()).withStyle(ChatFormatting.AQUA), false);
                            return controller == null ? 0 : 1;
                        }))
                        .then(Commands.literal("recover")
                                .then(Commands.argument("table_id", StringArgumentType.word())
                                        .executes(context -> run(context.getSource(), player -> controller.recover(player,
                                                StringArgumentType.getString(context, "table_id")))))));
    }

    private static void help(ServerPlayer player) {
        message(player, "拼豆工坊：制作并放置拼豆台，右键进入 Mard 291 色编辑器。 ");
        player.sendSystemMessage(Component.literal("成品右键方块表面直接张贴｜/pindou charm · 安装到武器或工具｜/pindou uncharm · 拆除挂饰")
                .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("配方/指令材料：1 合成器 + 5 玻璃板 + 1 蜜脾 + 2 红石")
                .withStyle(ChatFormatting.GRAY));
        if (isAdmin(player)) player.sendSystemMessage(Component.literal(
                "/pindou admin give｜status｜recover <台子ID>").withStyle(ChatFormatting.GOLD));
    }

    static boolean isAdmin(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }

    static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[拼豆工坊] " + text).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    static String safe(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "操作失败，请联系管理员。 " : message;
    }

    private static int run(CommandSourceStack source, Work work) {
        try {
            if (controller == null) throw new IllegalArgumentException(startupError);
            work.run(source.getPlayerOrException());
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("[拼豆工坊] " + safe(exception)));
            return 0;
        }
    }

    private static void atomic(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface private interface Work { void run(ServerPlayer player) throws Exception; }
}
