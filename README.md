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

## 2. Building the jar

This was written entirely offline, without network access, so it has
**not been compiled or run yet**. You'll build it the normal way Fabric
mods get built:

```bash
# from inside this project folder
gradle wrapper              # one-time: generates gradlew for you (needs internet)
./gradlew build             # downloads Minecraft/Fabric/Yarn and compiles
```

The resulting jar shows up at `build/libs/ai-carpet-bots-1.0.0.jar`.
Requirements: **JDK 21** and an internet connection (Gradle needs to
download Minecraft's libraries, Yarn mappings, Fabric API, and the Loom
Gradle plugin the first time).

**Easier alternative:** open the project folder directly in **IntelliJ
IDEA** with the Minecraft Development plugin installed - it'll offer to
import the Gradle project and set everything up for you, and you'll get
inline red underlines for anything that needs a one-line fix (see next
section) instead of a wall of command-line errors.

### If the build fails to resolve a dependency

Modding Minecraft 1.21.11 specifically depends on a few fast-moving
pieces (Loom, Fabric API, Yarn mappings) that get new releases
constantly. `gradle.properties` has comments on exactly which value to
check/update and where, if a version string in there is stale by the time
you build this.

## 3. The handful of spots most likely to need a tiny fix

I wrote and syntax-checked every file in this project, and fully compiled
the ~10 files that don't touch Minecraft at all (the AI brain, the voice
code, the config). I could **not** compile-test the files that call into
Minecraft/Fabric's own API, because that requires downloading Minecraft
itself, which needs network access I don't have in this environment.
Minecraft's internal method names shift slightly between versions, so
treat these as "probably right, quick to fix if not" rather than
guaranteed:

| File | What to check if it won't compile |
|---|---|
| `bot/BotController.java` | `runCommand()` - the one line that runs a command as the server/console (`executeWithPrefix`). |
| `bot/BotManager.java` | `broadcastBotChat()` - the one line that sends a chat message to everyone. |
| `listener/ChatListener.java` | `message.getContent().getString()` - how to get plain text out of a chat message. |

In every case, the fix is a one-line signature swap once you have
IntelliJ's autocomplete pointed at the real, mapped Minecraft classes -
none of these touch the actual logic (personalities, prompts, Carpet
command strings, HTTP calls), which is the part that's hardest to get
wrong and the part I'm most confident in.

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
