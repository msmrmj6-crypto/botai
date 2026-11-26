# AI Carpet Companions

Two Carpet-mod fake players with an AI brain (OpenAI) and a voice
(ElevenLabs): **رفيق** (polite, cooperative) and **الوقح** (rude: talks
back if you curse it, pranks you with a lava trap, argues, jokes, and
makes its own decisions). Both can be pushed-to-talk with and both act on
their own every `autonomyIntervalSeconds`.

Target: Minecraft **1.21.11**, **Fabric** loader, **singleplayer**.

## 1. What you still need to get

This project is 100% written for you, but it needs three things from you
that nobody but you can provide:

1. **The Carpet mod jar itself.** Download "Carpet" (by altrisi/gnembon)
   for Minecraft 1.21.11 / Fabric from Modrinth and drop it in your
   `mods/` folder, next to this mod's jar. This project never bundles or
   compiles against Carpet - it only ever sends the same `/player ...`
   text commands a human would type, so Carpet just needs to be present
   and loaded at runtime. If `/player <name> spawn` doesn't do anything in
   game, Carpet isn't installed/loaded correctly - check that first.
2. **An OpenAI API key** (for the "brain" - decisions + dialogue).
3. **An ElevenLabs API key** (for the voice in + voice out). Your two
   voice IDs are already wired in:
   - رفيق (polite): `7GuGTyc18I5wWanLbbNq`
   - الوقح (rude): `DhfzvPf0OIN8xdGwcHOk`

Put the two keys either as environment variables before launching
Minecraft (`OPENAI_API_KEY`, `ELEVENLABS_API_KEY`) or directly into
`config/aicompanions.json` after the first run (see below) - whichever
you're more comfortable with. Environment variables always win if both
are set.

## 2. Getting the jar

**You don't need to build this yourself.** Every push to this repo's
`main` branch automatically builds the jar on GitHub's own servers (via
`.github/workflows/build.yml`) and publishes it here:

**https://github.com/msmrmj6-crypto/botai/releases/tag/latest-build**

Open that link on your phone, download `ai-carpet-bots-1.0.0.jar`, and
drop it into your `mods/` folder next to Fabric API and Carpet. No
Termux, no local Gradle, no JDK on your phone required. That release page
always has the jar built from the latest commit - if you (or I) change
the code again, it gets replaced there automatically a minute or two
after the push.

If you ever do want to build it yourself on a real computer:

```bash
# from inside this project folder (needs JDK 21 + internet)
gradle build       # no wrapper is committed; a plain system Gradle 9.2.0+ works
```

The resulting jar shows up at `build/libs/ai-carpet-bots-1.0.0.jar`.

### If the build ever fails to resolve a dependency

Modding Minecraft 1.21.11 depends on a few fast-moving pieces (Loom,
Fabric API, Yarn mappings) that get new releases constantly.
`gradle.properties`/`build.gradle` have comments on exactly which value
to check/update if a version string goes stale.

## 3. Status: compiles clean against real 1.21.11 mappings

Earlier drafts of this project were written and syntax-checked without
ever downloading Minecraft (no network access in that environment), so a
few Minecraft-API call sites were educated guesses. The first real
GitHub Actions build caught exactly those, and they're now fixed and
confirmed building successfully:

| File | What changed in 1.21.11 | Fix |
|---|---|---|
| `bot/BotController.java`, `bot/PrankManager.java` | `CommandManager#executeWithPrefix` was removed | Switched to Brigadier's own `CommandDispatcher#execute(String, source)` - part of the Brigadier library itself, not Minecraft's wrapper, so it's more stable across future Minecraft versions too. |
| `command/ModCommands.java` | `ServerCommandSource#hasPermissionLevel(int)` was replaced by a much larger named-permission system | Dropped the permission gate on `/aibot` - this mod targets singleplayer with cheats on, where you're the only one who can ever run it anyway. |
| `bot/BotController.java` | `Entity#getPos()` was renamed | Now calls `getEntityPos()`. |
| `client/AICompanionsClient.java` | `KeyBinding`'s category parameter changed from a plain string to a `KeyBinding.Category` object | Builds one with `KeyBinding.Category.create(Identifier.of(...))`. |

None of this touched the actual logic (personalities, prompts, Carpet
command strings, HTTP calls) - just the handful of lines that talk
directly to Minecraft's own classes.

The exact Carpet command grammar this project relies on (`/player <name>
move forward`, `look at <x> <y> <z>`, `attack continuous`, `stop`, ...) is
taken straight from Carpet's own command documentation - if a particular
action doesn't do anything in-game, run it by hand in chat first to
confirm Carpet still accepts that exact wording in whatever Carpet
version you installed.

## 4. Using it in-game

1. Launch the world with cheats on (singleplayer).
2. `/aibot spawn Rafiq` and/or `/aibot spawn Waqah` (needs permission
   level 2 - the world owner has this by default with cheats on).
3. Hold **G** to talk to رفيق, **H** to talk to الوقح (configurable the
   normal Minecraft way, under Controls, once the mod is installed - they
   show up as "AI Carpet Companions" in the controls list).
4. Or just type their name in chat - any message that contains "Rafiq",
   "رفيق", "Waqah" or "الوقح" gets forwarded to that bot.
5. `/aibot rude Waqah false` turns off الوقح's cursing + pranks on the
   fly if it's too much; `/aibot rude Waqah true` turns it back on.
6. `/aibot kill <name>` removes a bot, `/aibot list` shows what's spawned.

## 5. Design notes / known limitations

- **No real pathfinding.** "Follow me" and "come here" are implemented as
  short move-and-recheck pulses toward you, not full A* pathing - they
  work fine in open terrain, less well across complex builds. Real
  pathfinding would mean scripting this in Carpet's own Scarpet language
  or wiring in a library like Baritone, which is a bigger project on top
  of this one.
- **The lava prank has guardrails on by default**: a cooldown
  (`prankCooldownSeconds`) and an auto-extinguish timer
  (`prankAutoExtinguishSeconds`, 20s by default) so it's a scare + a mess
  to clean up rather than a permanent base fire. Set
  `prankAutoExtinguishSeconds` to `0` for full chaos mode, or
  `pranksEnabled`/`cursingEnabled` to `false` to turn رود mode off
  per-bot in `config/aicompanions.json`.
- **Voice chat is singleplayer-only as written.** Push-to-talk reaches
  into the integrated server directly, which only exists because
  singleplayer runs the client and server in one process. Playing on a
  separate dedicated server would need a small addition (a network packet
  carrying the transcribed text) - flagged in `AICompanionsClient`'s
  comments, not implemented here since you asked for singleplayer.
- **Autonomous behaviour** fires every `autonomyIntervalSeconds` (45s by
  default) per bot, anchored on whichever player is first in the server's
  player list - fine for singleplayer, would need to pick the nearest
  player per bot for real multiplayer.
- Both personalities are defined in plain English in
  `ai/PersonalityPrompts.java` - that's the file to edit if you want to
  retune how pushy/greedy/talkative الوقح is, without touching any
  Minecraft code at all.
