package com.aicompanions.bot;

import com.aicompanions.config.BotProfile;
import com.aicompanions.util.TickScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The "lava trap" prank, with two safety rails baked in so it's a scare +
 * a mess rather than a one-way disaster by default:
 *   1. A per-bot cooldown (BotProfile.prankCooldownSeconds) so it can't spam.
 *   2. An optional auto-extinguish timer (BotProfile.prankAutoExtinguishSeconds)
 *      that has the bot come back with a water bucket afterward.
 * Both are configurable per bot in aicompanions.json, including fully
 * disabling pranks (pranksEnabled = false).
 */
public class PrankManager {

    private final MinecraftServer server;
    private final BotController controller;

    public PrankManager(MinecraftServer server, BotController controller) {
        this.server = server;
        this.controller = controller;
    }

    public boolean canPrank(BotProfile bot) {
        if (!bot.isRude() || !bot.pranksEnabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = bot.prankCooldownSeconds * 1000L;
        return (now - bot.lastPrankTimeMillis) >= cooldownMs;
    }

    public void lavaTrap(BotProfile bot, ServerPlayerEntity target) {
        if (!canPrank(bot)) {
            return;
        }
        bot.lastPrankTimeMillis = System.currentTimeMillis();

        // Step 1: get close enough and get a lava bucket in hand.
        controller.approachPlayer(bot, target);
        runCommand("give " + bot.carpetName + " minecraft:lava_bucket 1");

        // Step 2 (slight delay so the approach/give land first): aim at the
        // block the player is standing on and place the lava.
        TickScheduler.schedule(15, () -> {
            aimAtPlayersFeet(bot, target);
            runCommand("player " + bot.carpetName + " use once");
        });

        if (bot.prankAutoExtinguishSeconds > 0) {
            int delayTicks = bot.prankAutoExtinguishSeconds * 20;
            TickScheduler.schedule(delayTicks, () -> {
                runCommand("give " + bot.carpetName + " minecraft:water_bucket 1");
                TickScheduler.schedule(10, () -> {
                    aimAtPlayersFeet(bot, target);
                    runCommand("player " + bot.carpetName + " use once");
                });
            });
        }
    }

    private void aimAtPlayersFeet(BotProfile bot, ServerPlayerEntity target) {
        int x = (int) Math.floor(target.getX());
        int y = (int) Math.floor(target.getY()) - 1;
        int z = (int) Math.floor(target.getZ());
        runCommand("player " + bot.carpetName + " look at " + x + " " + y + " " + z);
    }

    private void runCommand(String command) {
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
    }
}
