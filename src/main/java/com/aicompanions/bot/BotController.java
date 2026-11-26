package com.aicompanions.bot;

import com.aicompanions.config.BotProfile;
import com.aicompanions.util.TickScheduler;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns high-level decisions into the exact text commands Carpet's fake
 * players understand. On purpose this NEVER imports a single Carpet class -
 * every call below is just "pretend a player typed this in chat", dispatched
 * through vanilla's own command dispatcher. That's what lets this mod avoid
 * a compile-time dependency on Carpet entirely (see build.gradle).
 * <p>
 * {@link #runCommand} goes straight through Brigadier's own
 * CommandDispatcher#execute(String, Object), not Minecraft's own
 * CommandManager#executeWithPrefix wrapper - the wrapper's exact shape has
 * changed across Minecraft versions (it did again in 1.21.11, which is why
 * an earlier draft of this file didn't compile), but Brigadier's own
 * dispatcher.execute(command, source) is part of the Brigadier library
 * itself and has stayed stable for years, so it's the more future-proof
 * choice here.
 */
public class BotController {

    private static final Logger LOGGER = Logger.getLogger("aicompanions");

    private final MinecraftServer server;

    public BotController(MinecraftServer server) {
        this.server = server;
    }

    private void runCommand(String command) {
        try {
            server.getCommandManager().getDispatcher().execute(command, server.getCommandSource());
        } catch (CommandSyntaxException e) {
            // Carpet isn't installed, or the installed Carpet version
            // doesn't accept this exact wording - skip this one action
            // instead of crashing the whole brain cycle over it.
            LOGGER.log(Level.FINE, "[AI Companions] Command rejected: " + command, e);
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Spawns the fake player near {@code nearPlayer} if it isn't already
     *  marked as spawned. Requires the Carpet mod to be installed - if it
     *  isn't, this command simply does nothing (unknown command), which is
     *  why ModCommands tells the player to double check their mods folder. */
    public void spawnIfNeeded(BotProfile bot, ServerPlayerEntity nearPlayer) {
        if (bot.spawned) {
            return;
        }
        int x = (int) Math.floor(nearPlayer.getX());
        int y = (int) Math.floor(nearPlayer.getY());
        int z = (int) Math.floor(nearPlayer.getZ());
        int yaw = Math.round(nearPlayer.getYaw());
        runCommand("player " + bot.carpetName + " spawn at " + x + " " + y + " " + z
                + " facing " + yaw + " in survival");
        bot.spawned = true;
    }

    public void kill(BotProfile bot) {
        runCommand("player " + bot.carpetName + " kill");
        bot.spawned = false;
    }

    // ------------------------------------------------------------------
    // Movement / look
    // ------------------------------------------------------------------

    public void move(BotProfile bot, String direction, int seconds) {
        runCommand("player " + bot.carpetName + " move " + direction);
        int ticks = Math.max(1, seconds) * 20;
        TickScheduler.schedule(ticks, () -> runCommand("player " + bot.carpetName + " stop"));
    }

    public void turn(BotProfile bot, String direction) {
        runCommand("player " + bot.carpetName + " turn " + direction);
    }

    public void lookDirection(BotProfile bot, String direction) {
        runCommand("player " + bot.carpetName + " look " + direction);
    }

    public void lookAtPlayer(BotProfile bot, ServerPlayerEntity player) {
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getEyeY());
        int z = (int) Math.floor(player.getZ());
        runCommand("player " + bot.carpetName + " look at " + x + " " + y + " " + z);
    }

    public void jump(BotProfile bot) {
        runCommand("player " + bot.carpetName + " jump");
    }

    public void sneak(BotProfile bot, boolean on) {
        runCommand("player " + bot.carpetName + (on ? " sneak" : " unsneak"));
    }

    public void sprint(BotProfile bot, boolean on) {
        runCommand("player " + bot.carpetName + (on ? " sprint" : " unsprint"));
    }

    public void stop(BotProfile bot) {
        runCommand("player " + bot.carpetName + " stop");
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    public void attack(BotProfile bot, String mode, int seconds) {
        runCommand("player " + bot.carpetName + " attack " + mode);
        if ("continuous".equals(mode)) {
            TickScheduler.schedule(Math.max(1, seconds) * 20,
                    () -> runCommand("player " + bot.carpetName + " stop"));
        }
    }

    public void useItem(BotProfile bot, String mode, int seconds) {
        runCommand("player " + bot.carpetName + " use " + mode);
        if ("continuous".equals(mode)) {
            TickScheduler.schedule(Math.max(1, seconds) * 20,
                    () -> runCommand("player " + bot.carpetName + " stop"));
        }
    }

    // ------------------------------------------------------------------
    // Higher-level helpers built out of the primitives above
    // ------------------------------------------------------------------

    public void approachPlayer(BotProfile bot, ServerPlayerEntity player) {
        lookAtPlayer(bot, player);
        move(bot, "forward", 1);
    }

    /** No real pathfinding (that would need Scarpet/Baritone-style logic) -
     *  this is a "walk pulses toward the player every half-second" loop,
     *  which looks convincing for a companion sticking near you. Fake
     *  players are registered in the normal player list (that's the whole
     *  point of Carpet's fake players), so we can fetch the bot's own
     *  entity back by name to measure the distance. */
    public void followPlayer(BotProfile bot, ServerPlayerEntity player, int seconds) {
        int pulses = Math.max(1, seconds) * 2; // every 10 ticks
        for (int i = 0; i < pulses; i++) {
            int delay = i * 10;
            TickScheduler.schedule(delay, () -> {
                ServerPlayerEntity botEntity = server.getPlayerManager().getPlayer(bot.carpetName);
                if (botEntity == null) {
                    return; // bot despawned / Carpet not installed - nothing to do
                }
                double dist = botEntity.getEntityPos().distanceTo(player.getEntityPos());
                if (dist > 3.0) {
                    lookAtPlayer(bot, player);
                    runCommand("player " + bot.carpetName + " move forward");
                } else {
                    runCommand("player " + bot.carpetName + " stop");
                }
            });
        }
        TickScheduler.schedule(pulses * 10 + 2, () -> stop(bot));
    }
}
