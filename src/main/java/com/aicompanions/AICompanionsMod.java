package com.aicompanions;

import com.aicompanions.bot.BotManager;
import com.aicompanions.command.ModCommands;
import com.aicompanions.config.BotProfile;
import com.aicompanions.config.ModConfig;
import com.aicompanions.listener.ChatListener;
import com.aicompanions.util.TickScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class AICompanionsMod implements ModInitializer {

    public static ModConfig CONFIG;
    private static volatile BotManager botManager;

    public static BotManager getBotManager() {
        return botManager;
    }

    @Override
    public void onInitialize() {
        CONFIG = ModConfig.load();

        // Command + chat listeners are safe to register immediately: both
        // look up the live BotManager at the moment they actually fire,
        // not now (see the comments in ModCommands / ChatListener).
        ModCommands.register();
        ChatListener.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            botManager = new BotManager(server, CONFIG);
            server.getPlayerManager().broadcast(
                    net.minecraft.text.Text.literal("[AI Companions] loaded - try /aibot spawn "
                            + (CONFIG.bots.isEmpty() ? "<name>" : CONFIG.bots.get(0).carpetName)),
                    false);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CONFIG.save();
            BotManager.shutdown();
            botManager = null;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TickScheduler.tick();

            BotManager manager = botManager;
            if (manager == null) {
                return;
            }
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            if (players.isEmpty()) {
                return;
            }
            // Singleplayer-oriented: autonomous ticks are anchored on the
            // first online player. For a real multiplayer server you'd want
            // to pick the nearest online player per bot instead.
            ServerPlayerEntity anchor = players.get(0);
            for (BotProfile bot : manager.bots()) {
                if (bot.spawned) {
                    manager.autonomousTick(bot, anchor);
                }
            }
        });
    }
}
