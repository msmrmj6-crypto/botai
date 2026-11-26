package com.aicompanions.command;

import com.aicompanions.AICompanionsMod;
import com.aicompanions.bot.BotManager;
import com.aicompanions.config.BotProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * /aibot spawn <name>        - spawn one of your configured bots near you
 * /aibot kill <name>         - remove it
 * /aibot say <name> <text>   - manually feed it a line, as if you'd said it
 * /aibot rude <name> <bool>  - toggle cursing + prank features for that bot
 * /aibot list                - show configured bots and their state
 * <p>
 * No .requires(...) permission gate: Minecraft 1.21.11 replaced the old
 * simple "permission level" integer with a much more involved named-
 * permission system (net.minecraft.command.permission.*), and this mod's
 * target setup is singleplayer with cheats on, where you - the world owner
 * - are the only one who can ever run these commands anyway. If you later
 * add other players and want to gate this, look at Fabric API's
 * permission APIs for a version-independent way to do it.
 */
public final class ModCommands {

    private ModCommands() {
    }

    /** Takes no BotManager: the manager only exists once a server has
     *  started, while commands are registered earlier, so every branch
     *  below fetches the live instance from AICompanionsMod at the moment
     *  someone actually runs the command instead of at registration time. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("aibot")
                        .then(CommandManager.literal("spawn")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            BotManager botManager = AICompanionsMod.getBotManager();
                                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                                            String name = StringArgumentType.getString(ctx, "name");
                                            if (botManager == null || player == null) {
                                                ctx.getSource().sendError(Text.literal("Bot manager not ready yet."));
                                                return 0;
                                            }
                                            Optional<BotProfile> bot = botManager.findBot(name);
                                            if (bot.isEmpty()) {
                                                ctx.getSource().sendError(Text.literal("Unknown bot: " + name));
                                                return 0;
                                            }
                                            botManager.ensureSpawned(bot.get(), player);
                                            ctx.getSource().sendFeedback(() ->
                                                    Text.literal("Spawned " + bot.get().displayName), false);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("kill")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            BotManager botManager = AICompanionsMod.getBotManager();
                                            String name = StringArgumentType.getString(ctx, "name");
                                            if (botManager == null) {
                                                ctx.getSource().sendError(Text.literal("Bot manager not ready yet."));
                                                return 0;
                                            }
                                            Optional<BotProfile> bot = botManager.findBot(name);
                                            if (bot.isEmpty()) {
                                                ctx.getSource().sendError(Text.literal("Unknown bot: " + name));
                                                return 0;
                                            }
                                            botManager.despawn(bot.get());
                                            ctx.getSource().sendFeedback(() ->
                                                    Text.literal("Removed " + bot.get().displayName), false);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("say")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .then(CommandManager.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    BotManager botManager = AICompanionsMod.getBotManager();
                                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String text = StringArgumentType.getString(ctx, "text");
                                                    if (botManager == null || player == null) {
                                                        ctx.getSource().sendError(Text.literal("Bot manager not ready yet."));
                                                        return 0;
                                                    }
                                                    Optional<BotProfile> bot = botManager.findBot(name);
                                                    if (bot.isEmpty()) {
                                                        ctx.getSource().sendError(Text.literal("Unknown bot: " + name));
                                                        return 0;
                                                    }
                                                    botManager.handleSpeech(bot.get(), player, text);
                                                    return 1;
                                                }))))
                        .then(CommandManager.literal("rude")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    BotManager botManager = AICompanionsMod.getBotManager();
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                    if (botManager == null) {
                                                        ctx.getSource().sendError(Text.literal("Bot manager not ready yet."));
                                                        return 0;
                                                    }
                                                    Optional<BotProfile> bot = botManager.findBot(name);
                                                    if (bot.isEmpty()) {
                                                        ctx.getSource().sendError(Text.literal("Unknown bot: " + name));
                                                        return 0;
                                                    }
                                                    bot.get().cursingEnabled = enabled;
                                                    bot.get().pranksEnabled = enabled;
                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                            bot.get().displayName + " rude mode: " + enabled), false);
                                                    return 1;
                                                }))))
                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    BotManager botManager = AICompanionsMod.getBotManager();
                                    if (botManager == null) {
                                        ctx.getSource().sendError(Text.literal("Bot manager not ready yet."));
                                        return 0;
                                    }
                                    StringBuilder sb = new StringBuilder("Bots: ");
                                    for (BotProfile b : botManager.bots()) {
                                        sb.append(b.displayName).append("(").append(b.carpetName).append(", ")
                                                .append(b.spawned ? "spawned" : "not spawned").append(") ");
                                    }
                                    ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                                    return 1;
                                }))
                ));
    }
}
