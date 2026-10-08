# SafeDetect

Personal **Hypixel Bedwars** util for **Lunar Client 1.8.9**. It watches nearby players, flags cheat-like movement and combat, shows lobby stats on a small overlay, remembers people you have already seen, and lets you turn extra add-ons on from an in-game marketplace.

It is **not** a Lunar mod. It loads with `-javaagent` on the 1.8.9 profile.

Current version is the `Implementation-Version` in `agent/MANIFEST.MF` (1.2.0).

## What it does

- Live checks in a **16-chunk** range (256 blocks) around you: aura, reach, clicker, block, slow, velocity, speed, and several bridging styles.
- Overlay window listing everyone in the lobby (from tab, `/who`, and join chat) with stars, FKDR, tags, and dodge warnings.
- Saved flags that persist across games, with evidence (`reach 3.71, 3 long hits`) and a “seen” count.
- Tab marks and chat names show flags from SafeDetect, Urchin, and your local blacklist — without replacing team colour. Hover a name for the full tooltip.
- Optional Hypixel / Urchin / Aurora / anti-sniper keys for stats, nicks, and community tags.
- A **Plugins** marketplace: bundled add-ons and drop-in jars, all commanded under `/sd`.

Detection **does not** use the scoreboard or the tab list as a cheat signal. Tab is only written so flags and plugin labels can appear next to names.

## What it is not

- Not a Forge/Fabric mod, Badlion plugin, or ChatTriggers module.
- Not an injector you configure separately — Lunar’s JVM argument is enough.
- Not a full client. Combat assists (auto W-tap and similar) are not included.
- Live checks are **Bedwars-oriented**. Duels extras live in the optional DuelDesk plugin.

Hypixel NPC lobby bots and invalid names are ignored. `trnsmt` and `zoxide` are skipped by default (the friends list). Add anyone else with `/sd friend <name>` or the overlay row menu.

---

## Install

### From a GitHub release (easiest)

1. Download `SafeDetect-vX.Y.Z.zip` from [Releases](https://github.com/AidanFitzpatrick-priv/safedetect/releases/latest).
2. Extract the zip and run `install.bat`. It copies the jar to `%APPDATA%\.minecraft\safedetect\` and puts the JVM argument on your clipboard.
3. In the **Lunar Client** launcher: settings cog → **1.8.9** profile → **JVM Arguments**. Paste, keeping anything already there, separated by a space:

   `-javaagent:C:\Users\YOURNAME\AppData\Roaming\.minecraft\safedetect\safedetect-agent.jar`

4. Close Lunar settings so it saves. Fully quit Lunar, then launch **1.8.9**.
5. In game you should see `[SD] SafeDetect is running.` and the overlay. Type `/sd help`.

If the path has spaces, `install.bat` copies a short 8.3 path instead so Lunar does not split the argument.

### By hand

1. Copy `safedetect-agent.jar` to `%APPDATA%\.minecraft\safedetect\`.
2. Add the `-javaagent:…\safedetect-agent.jar` line as above.
3. Fully quit Lunar, then launch 1.8.9.

A jar copied while Lunar is open does **not** reload the checks. Restart Lunar after every install or update. The overlay can restart itself on a new jar; the agent inside `javaw` cannot.

Keep Lunar in **borderless fullscreen** so the overlay stays on top.

---

## First run

1. Open the overlay (it should appear on its own; **Right Shift** hides or shows it, `/sd gui` brings it back).
2. Click **Keys** (or Gear → Keys) and paste a [Hypixel API key](https://developer.hypixel.net/) so stars / FKDR / WLR / nicks work.
3. Optionally add Urchin, Aurora, anti-sniper, and Discord there too. Prefer the overlay: `/sd key …` is also sent to Hypixel as an unknown command.
4. Gear → **Plugins** to enable add-ons (QuickPlay is on by default).
5. Gear → **Alerts** / **Checks** to tune dodge rules and which flags run.

---

## Overlay

The overlay is a **child window**, not drawn inside Minecraft. Drag it from the **SafeDetect** title or the Lobby / Saved tabs, not from the buttons.

Two lists:

- **Lobby** — everyone currently in the game or pre-game (tab, join lines, `/who`).
- **Saved** — everyone you have flagged before.

A red banner lists people worth dodging. Flagged players are pinned to the top by default. Rows get a stripe in their team colour.

| Control | What it does |
| --- | --- |
| Right-click a row | Copy name or `/wdr`, blacklist / unblacklist, skip (friend) / unfriend, open Plancke or NameMC |
| Double-click a row | Copy `/wdr name cheating` |
| Click a flag chip | Same `/wdr` copy |
| Column headers | Click to sort (third click restores threat order); drag to reorder or resize |
| Search | Filter the current tab by name or tag |
| Refresh | Reload lobby stats from the APIs |
| Mini | Small always-on-top list of threats (or everyone), sized to fit |
| Gear | Settings panel |
| Tab / Chat / Sound | Tab marks, chat alerts, alert pings |
| Update | Download the latest jar (same as Alerts → Check for updates) |
| Clear | Wipe every saved tracker name (needs confirm in chat if you use `/sd clear`) |
| Keys | API keys without typing them in chat |
| Opacity slider | Window transparency, when the system supports it |
| Right Shift | Hide or show the overlay (changeable under Appearance). Ignored while chat is open |
| `/sd gui` | Reopen if you hid or closed it |

Row tooltips show evidence and last-flag time.

---

## Settings

Gear opens six pages. Appearance and Columns are saved in the overlay process. Alerts, Checks, Keys, and Plugins are sent to the game.

### Appearance

Theme (**Dark**, **Light**, **Classic Hypixel**, **High contrast**), accent colour, font size, row density, opacity, hide/show hotkey (default **Right Shift**), mini-mode filter (threats vs everyone), group by team, pin flagged first.

### Columns

Pick, order, and reset lobby columns:

`Lvl`, `Name`, `Team`, `Flags`, `WS`, `FKDR`, `WLR`, `Finals`, `Wins`, `Sniper`, `Nick`, `Seen`, `Last`

`Nick` is optional (off by default): denick map or a version-1 UUID is **high**, a saved `NK` flag alone is **mid**. It is display-only — not a cheat check.

Saved tab always uses name, flags, times flagged, and last flag.

### Alerts

- Tab marks, chat hovers, chat alerts, alert sound
- Stay on top (borderless)
- Daily GitHub update check
- **Check for updates** — downloads the latest `safedetect-agent.jar`, restarts the overlay, stages the agent for the next Lunar launch
- Dodge rules and thresholds (see [Dodge warnings](#dodge-warnings))

### Checks

Turn each live check on or off, read what SD and Urchin chips mean, pick sensitivity (**lenient** / **normal** / **strict**), and tune reach, aura angle, CPS, and speed limits.

Lenient needs about 1.5× the evidence; strict about 0.75×. Defaults: reach **3.35**, killaura angle **80°**, autoclicker **15 CPS**, speed **0.42** blocks/tick.

### Keys

Never shown again after save. Blank fields are left unchanged. **Clear all** removes every key.

| Field | Used for |
| --- | --- |
| Hypixel API key | Stars, FKDR, WLR, finals, wins, winstreak, nicked UUID lookup |
| Anti-sniper key | Built-in sniper score API |
| Anti-sniper URL | Custom URL; use `{{name}}` / `{{uuid}}` if the service needs them |
| Urchin key | Community tags (`U:Cheater`, `U:Sniper`, …) from [Urchin](https://urchin.gg) |
| Aurora key | Number-denick (Vega `/api view`) |
| Discord app id | Optional Rich Presence (session games/wins) |

### Plugins

Marketplace of bundled add-ons plus any jars in `config/safedetect-plugins`. See [Plugins](#plugins).

---

## Live checks

Checks run only on **real players** in range, not Hypixel NPCs, not friends, not you. Teammates are checked unless they are on the skip list. Creative / lobby fly and similar out-of-game movement is ignored.

| Code | Name | Meaning |
| --- | --- | --- |
| KA | Killaura | Hits you while looking somewhere else |
| SI | Silent Aura | Hits you without turning their head |
| RE | Reach | Hits from further than vanilla reach |
| AC | Autoclicker | Clicks faster than a person usually can |
| AB | AutoBlock | Swings the sword while blocking |
| NS | NoSlow | Full walk speed while using an item (bow, food, rod) |
| VL | Velocity | Takes no knockback when you hit them |
| SA | Snap Aim | Instant large look snaps when they swing |
| SP | Speed | Faster than vanilla sprinting allows |
| LS | Legit Scaffold | Scripted sneak-place bridging (same crouch rhythm) |
| SS | Sprint Scaffold | Sprints while bridging backwards |
| GB | God Bridge | God-bridges without sneaking |
| KY | Keep-Y | Same-Y bridging without sneaking |
| AS | Air Scaffold | Places in the air looking down for too long |
| DS | Diagonal Scaffold | Sprint-bridges on a diagonal |
| TL | Telly | Fast pitch flicks while placing |
| TW | Tower | Towers up faster than placing should allow |

Toggle with Gear → Checks, or `/sd checks KA off`. Overlay chips look like `SD:AB`.

Other SD chips (not live movement checks):

| Chip | Meaning |
| --- | --- |
| SD:SN | Sniper name list or sniper-looking stats |
| SD:NK | Nicked account |
| SD:FK | High Bedwars stars and FKDR |
| SD:AL | Possible alt of someone you already flagged |
| SD:BL | On your local blacklist |

Urchin chips (`U:…`) come from the Urchin API when a key is set:

| Chip | Meaning |
| --- | --- |
| U:Account | Account note, not a cheat tag |
| U:Info | Staff/community note |
| U:Caution | Weak warning |
| U:Possible Sniper | Might be lobby sniping |
| U:Sniper | Tagged as a sniper |
| U:Legit Sniper | High-skill sniper (stats), not a cheat client |
| U:Closet Cheater | Suspected hidden cheats |
| U:Blatant Cheater | Obvious cheating |
| U:Confirmed Cheater | Staff reviewed with evidence |

**Tab** (Alerts → Tab marks) puts those chips after the name, including Urchin `U:` tags, and keeps team colour.

**Chat** (Alerts → Chat hovers) does the same next to the name in the message, plus a Cubelify-style tooltip on hover: SafeDetect flags + evidence, Urchin tags, and blacklist. Click still copies `/wdr` only when the name did not already have a Hypixel click action.

---

## Dodge warnings

Once per player per world: a red `DODGE?` chat line, a quiet sound, and a title. Fired when someone in the lobby is:

- on your blacklist
- flagged in an earlier game
- Urchin-tagged
- at or above the sniper score (default **60**)
- at or above the FKDR threshold (default **8**, optional star minimum)

Each rule can be turned off under Alerts. `/sd dodge off` disables all of them. Friends and current party members never trigger dodge.

Old `BB` / `FL` (BedBreaker / Fly) values in saved flag files still display; they are not live checks and cannot be toggled back on.

---

## Friends, nicks, blacklist

| Action | How |
| --- | --- |
| Never check someone | Overlay row → skip, or `/sd friend <name>` |
| Undo | `/sd unfriend <name>` or the row menu |
| List skipped | `/sd friend` / `/sd friends` |
| Local blacklist | Overlay right-click, or `/sd bl add\|remove\|list\|import` |
| Manual denick | `/sd nick <shown> <real>` |
| Auto denick | Hypixel nicked UUIDs + optional Aurora key; skins remembered in `safedetect-skins.json` |

Blacklist import: drop a `.txt` or `.json` of names in the config folder, then `/sd bl import`. Overlay click copies `/wdr name cheating`.

---

## Commands

Everything the agent understands starts with **`/sd`**. These are client-side, but Minecraft still sends the line to the server, so **do not put API keys in chat**.

### Core

| Command | What it does |
| --- | --- |
| `/sd` / `/sd help` | List commands |
| `/sd gui` | Reopen the overlay (Right Shift also hides/shows it) |
| `/sd list [page]` | Saved flags in chat |
| `/sd check <name>` | One player: flags, evidence, encounters |
| `/sd clear confirm` | Delete every saved player |
| `/sd update` | Download the latest jar |
| `/sd export` | CSV of flags and encounters in `config/` |
| `/sd reload` | Re-read settings, blacklist, flags, encounters (after editing files by hand) |
| `/sd checks` | List checks and thresholds |
| `/sd checks <code> on\|off` | Toggle one check, e.g. `/sd checks SP off` |
| `/sd dodge [on\|off]` | Dodge warning status or master switch |
| `/sd borderless [on\|off]` | Keep overlay on top |

### People

| Command | What it does |
| --- | --- |
| `/sd friend <name>` | Skip forever |
| `/sd unfriend <name>` | Stop skipping |
| `/sd nick <shown> <real>` | Map a nick to a real IGN |
| `/sd bl add\|remove\|list\|import [file]` | Local blacklist |

### Keys (prefer the overlay Keys page)

| Command | What it does |
| --- | --- |
| `/sd key <hypixel-api-key>` | Stats and nicks (`clear` to remove) |
| `/sd urchin <key>` | Urchin tags (`off` to disable) |
| `/sd aurora <key>` | Number denick |
| `/sd sniper <key>` | Anti-sniper API |
| `/sd sniper url <https://…>` | Custom sniper URL |
| `/sd sniper off` | Disable external anti-sniper |
| `/sd discord <application-id>` | Rich Presence (`off` to disable) |

### Plugins

| Command | What it does |
| --- | --- |
| `/sd plugins` | List bundled and drop-in add-ons |
| `/sd plugins on\|off <id>` | Enable or disable one |
| `/sd <id> …` | That plugin’s own subcommands (see below) |

---

## Plugins

**Settings → Plugins** is the browser. Each card has an On/Off button, a short description, extra keys it needs, and its `/sd` commands.

- **Off by default**, except **QuickPlay** (`play`), so `/sd play 2s` works immediately.
- State is saved in `config/safedetect-plugins.json` (enabled ids + plugin config strings).
- `/sd nickfind` while NickFind is off prints that it is off and how to enable it.

### Bundled add-ons

| Id | Name | Enable | Commands | Needs | What it does |
| --- | --- | --- | --- | --- | --- |
| `play` | QuickPlay | on by default | `/sd play 1s\|2s\|3s\|4s\|44s\|rush` | — | Queues Bedwars solos / doubles / 3s / 4s / 4v4 / doubles rush |
| `nickfind` | NickFind | `/sd plugins on nickfind` | `/sd nickfind setkey <key>` `/sd nickfind lookup <nick>` `/sd nickfind scan` | [Bedlify](https://api.bedlify.xyz/docs) API key | At Bedwars start (`Protect your bed…`), looks up version-1 (nicked) UUIDs and can suffix tab with the real name |
| `duel` | DuelDesk | `/sd plugins on duel` | `/sd duel layout full\|minimal\|numbers` `/sd duel session` `/sd duel webhook <url>` `/sd duel clearwebhook` `/sd duel q <mode>` | Hypixel key; optional Discord webhook | Opponent stats on `Opponent:`, session W/L, webhook on win/loss (`[P]` party, `[RM]` rematch, `[D]` `/duel`, `[U]` other unranked). Queues: `classic`, `bridge`, `sw`, `uhc`, `op`, `sumo`, `combo`, `bow`, `nodebuff`, `blitz`, `mw`, `parkour`, `spleef`, `quake`, plus `*2s` / `uhc4s` / `bw` / `bwrush` |
| `partywarn` | PartyWarn | `/sd plugins on partywarn` | `/sd partywarn` | Urchin key | After `/who`, `/pc` enemy cheater/sniper tags (skips party and friends) |
| `split` | SplitPing | `/sd plugins on split` | `/sd split [window-ms]` | — | Sound + “Split the gen!” if you and a teammate die within the window (default 8000 ms) |
| `snipe` | SnipeWatch | `/sd plugins on snipe` | `/sd snipe add\|remove\|list\|clear <ign>` | optional party chat | Alerts (and `/pc`) if a watched name is in `ONLINE:` |
| `autogg` | AutoGG | `/sd plugins on autogg` | `/sd autogg [message]` | — | Sends `gg` (or your text) after Victory / Game over. Use this if Badlion AutoGG does not run under the agent |
| `pdodge` | PartyDodge | `/sd plugins on pdodge` | `/sd pdodge on\|off` `/sd pdodge 1s\|2s\|3s\|4s` | — | Requeues when several people dump into the lobby at once |
| `meow` | Meow | `/sd plugins on meow` | `/sd meow` | — | Replies `mrowww~ :3` when someone meows in chat |
| `height` | HeightCall | `/sd plugins on height` | `/sd height <map>` | — | Looks up a Bedwars height limit; can announce it when you are sent to a known map |
| `ses` | SessionPad | `/sd plugins on ses` | `/sd ses` `/sd ses reset\|pause\|resume\|share` | — | Separate Bedwars session W/L from chat (the overlay footer already tracks games/wins for Discord) |
| `tags` | TagPeek | `/sd plugins on tags` | `/sd tags <name>` | Urchin key optional | One-off Urchin tag lookup without replacing tab |
| `statcall` | StatCall | `/sd plugins on statcall` | `/sd statcall` | Hypixel key | If someone mentions you in pre-game chat, print their stars and FKDR |

Core already covers Urchin tags, Aurora denick, auto `/who` at game start, overlay session games/wins, and dodge. Those are not duplicate plugins.

### Sharing and writing a plugin

Drop a `.jar` in:

`%APPDATA%\.minecraft\config\safedetect-plugins\`

Manifest must contain:

```
Plugin-Class: com.example.MyPlugin
```

The class needs a **public no-arg constructor** and must implement `com.safedetect.agent.Plugin`. Optionally also implement `PluginEvents`. Compile against `safedetect-agent.jar` on the classpath (Java 8).

Restart Lunar after adding or replacing a jar.

Minimal plugin:

```java
package com.example;

import com.safedetect.agent.Plugin;
import com.safedetect.agent.PluginApi;
import com.safedetect.agent.PluginEvents;
import com.safedetect.agent.PluginInfo;
import com.safedetect.agent.PluginPlayer;
import java.util.List;

public class MyPlugin implements Plugin, PluginEvents {
    private PluginApi api;

    @Override
    public PluginInfo info() {
        return new PluginInfo("hello", "Hello", "You", "1.0.0",
                "Says hi.", "", "/sd hello");
    }

    @Override
    public void start(PluginApi api) { this.api = api; }

    @Override
    public void stop() { api = null; }

    @Override
    public boolean command(String[] parts) {
        api.chat("hello");
        return true;
    }

    @Override
    public void chat(String plain) { }

    @Override
    public void world() { }

    @Override
    public void tab(List<PluginPlayer> players) { }
}
```

`command` is called as `/sd hello …` with `parts[0] = /sd`, `parts[1] = hello`. Return `false` to print the usage string from `PluginInfo`.

`PluginApi` (client thread, except HTTP):

| Method | Use |
| --- | --- |
| `chat` / `sound` | Message the user / play `note.pling` |
| `send` | Chat packet to the server (`/play`, `/who`, `/pc`, …) |
| `selfName` / `selfUuid` | You |
| `skipped` | Friends list |
| `hypixelKey` / `urchinKey` | Keys from the Keys page |
| `teamPrefix` | Scoreboard colour prefix for a tab name |
| `tabSuffix` / `clearTabSuffix` | Extra text after the name in tab |
| `config` / `configOn` / `configInt` | Persist strings in `safedetect-plugins.json` |
| `httpGet` / `httpPost` | Blocking HTTP — call from a **worker thread** |

Jars run with the **same privileges as SafeDetect** (your Minecraft process). Only install plugins you trust.

---

## Updates

Once a day (if Alerts → Update check is on) SafeDetect looks at GitHub for a newer release than the jar’s `Implementation-Version`.

To install now: overlay **Update**, Alerts → **Check for updates**, or `/sd update`.

- The overlay process restarts on the new jar immediately.
- Checks inside Lunar need a **full Lunar restart**.
- If the live jar is locked, a `safedetect-agent.jar.new` plus `safedetect-apply-update.bat` is written next to it and applied when Lunar exits.

Unauthenticated GitHub API 404s if the repository is private. Public releases do not need a token.

---

## Files

Game directory is `%APPDATA%\.minecraft\` unless Lunar uses another data dir.

| Path | What |
| --- | --- |
| `.minecraft/safedetect/safedetect-agent.jar` | The agent Lunar loads |
| `.minecraft/safedetect/safedetect-agent.log` | Agent log |
| `config/safedetect-settings.json` | Keys, friends, dodge, checks, nicks (game process only) |
| `config/safedetect-overlay.json` | Overlay theme, bounds, columns |
| `config/safedetect-flags.json` | Saved players and evidence |
| `config/safedetect-flags.log` | Append-only readable flag log |
| `config/safedetect-seen.json` | Encounter counts (entries older than 120 days dropped) |
| `config/safedetect-blacklist.txt` | Local blacklist |
| `config/safedetect-skins.json` | Skin hashes for denick |
| `config/safedetect-plugins.json` | Enabled plugins and their config |
| `config/safedetect-plugins/*.jar` | Drop-in plugins |
| `config/safedetect-hud.json` | Live overlay snapshot (game → overlay) |
| `config/safedetect-cmd/` | Overlay → game commands |
| `config/safedetect-overlay.log` | Overlay process log |
| `config/safedetect-export-*.csv` | `/sd export` |

Do not commit API keys. The overlay never reads key values back; it only shows Set / Not set.

---

## Build

Java **8** (Temurin 8 matches Lunar 1.8.9). `build.ps1` uses `-Jdk <path>`, then `JAVA_HOME`, then the newest JDK 8 under `%USERPROFILE%\.jdks`:

```powershell
powershell -ExecutionPolicy Bypass -File agent\build.ps1 -Test -Deploy
```

- Writes `agent\out\safedetect-agent.jar`
- `-Test` runs unit checks and a mock-game harness
- `-Deploy` copies the jar to `%APPDATA%\.minecraft\safedetect\safedetect-agent.jar`

Then fully restart Lunar.

## Release

1. Bump `Implementation-Version` in `agent/MANIFEST.MF`.
2. Commit and push to `main`.
3. `powershell -ExecutionPolicy Bypass -File agent\release.ps1`

That publishes a GitHub Release with the zip (`install.bat` + jar) that in-game Update downloads.

---

## Credits

Detection ideas adapted from [Meowtils 2.0.1](https://github.com/femboytatp/meowtils) (AutoBlock, NoSlow, Killaura, Legit Scaffold, sniper name/stat rules). Gameplay modules from Meowtils are not included.

Bundled plugin behaviour is inspired by community Starfish-style add-ons (Bedlify nick resolve, Duels trackers, party/sniper alerts, AutoGG, queue shortcuts, and similar). They are Java rewrites under `/sd`, not those original scripts.
