package com.aicompanions.ai;

import com.google.gson.JsonArray;

/** Parsed result of one OpenAI call: what the bot says + what it does. */
public record Decision(String speech, JsonArray actions) {
}
