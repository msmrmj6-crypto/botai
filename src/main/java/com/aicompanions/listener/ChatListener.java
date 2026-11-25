package com.aicompanions.listener;

import com.aicompanions.AICompanionsMod;
import com.aicompanions.bot.BotManager;
import com.aicompanions.config.BotProfile;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

/**
 * Forwards a chat message to a bot's brain ONLY when the message mentions
 * that bot by name (carpetName or displayName) - this keeps the mod from
 * burning an OpenAI call on every unrelated line of chat. General "ambient"
 * behaviour without being spoken to comes from BotManager#autonomousTick
 * instead, and direct voice chat comes from the push-to-talk keybinds in
 * AICompanionsClient.
 * <p>
 * ============================= KNOWN RISK AREA =============================
 * SignedMessage's accessor for the plain text content has had a couple of
 * different names across 1.19-1.21 snapshots (getContent(), raw(), ...).
 * If message.getContent().getString() doesn't compile, autocomplete on
 * `message.` in your IDE to find the current one - everything below this
 * line is unaffected.
 * =============================================================================
 */
public final class ChatListener {

    private ChatListener() {
    }

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            BotManager botManager = AICompanionsMod.getBotManager();
            if (botManager == null) {
                return;
            }
            String text = message.getContent().getString();
            if (text == null || text.isBlank()) {
                return;
            }
            String lower = text.toLowerCase();
            for (BotProfile bot : botManager.bots()) {
                boolean mentioned = lower.contains(bot.carpetName.toLowerCase())
                        || lower.contains(bot.displayName.toLowerCase());
                if (mentioned) {
                    botManager.handleSpeech(bot, sender, text);
                }
            }
        });
    }
}
