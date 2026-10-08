# SafeDetect

Personal Bedwars util for **Lunar Client 1.8.9**. It watches nearby players, shows stats on a small overlay, and remembers people you have already seen.

It is not a Lunar mod. It loads with `-javaagent`.

## Build

Java 8 (Temurin 8 is what Lunar 1.8.9 uses). The script uses `-Jdk <path>`, then `JAVA_HOME`, then the newest JDK 8 under `%USERPROFILE%\.jdks`:

```powershell
powershell -ExecutionPolicy Bypass -File agent\build.ps1 -Test -Deploy
```

That writes `agent\out\safedetect-agent.jar`, runs the unit checks and the mock-game harness (`-Test`), and copies the jar to `%APPDATA%\.minecraft\safedetect\safedetect-agent.jar` (`-Deploy`).

## Install

1. Copy `safedetect-agent.jar` to `%APPDATA%\.minecraft\safedetect\`.
2. In Lunar Client, select **1.8.9** → JVM arguments, and add:

   `-javaagent:C:\Users\YOURNAME\AppData\Roaming\.minecraft\safedetect\safedetect-agent.jar`

3. Fully quit Lunar, then launch 1.8.9.
4. You should see `[SD] SafeDetect is running.` Type `/sd help`.

## Stats

Stars / FKDR / WLR need a Hypixel API key from [developer.hypixel.net](https://developer.hypixel.net/).

Set it with the **Keys** button in the overlay footer, which opens the Keys page of the settings panel. The same page takes the anti-sniper, Urchin, Seraph, Aurora and Discord values. Blank fields are left unchanged, and **Clear all** removes every key.

`/sd key YOUR_KEY` still works, but SafeDetect can't stop chat commands from also being sent to the server, so the key reaches Hypixel as an unknown command. Prefer the overlay.

## Overlay

Keep Lunar in borderless fullscreen.

| Control | What it does |
| --- | --- |
| Right-click a row | Copy name or `/wdr`, blacklist or unblacklist, skip (friend), open Plancke or NameMC |
| Double-click a row | Copy `/wdr name cheating` |
| Column headers | Click to sort (third click restores threat order), drag to reorder or resize |
| Search box | Filter the current tab by name or tag |
| Refresh | Reload lobby stats |
| Mini | Small always-on-top list of threats (or everyone), sized to fit |
| Gear | Settings panel |
| Tab / Chat / Sound | Toggle tab marks, chat alerts, and pings |
| Clear | Wipe every saved tracker name |
| Keys | Set API keys without typing them in chat |
| Opacity slider | Window transparency, when the system supports it |
| `/sd gui` | Bring the overlay back if it was closed |

A red banner lists lobby players worth dodging. Flagged players are pinned to the top by default, and rows get a stripe in their team colour.

The settings panel has five pages:

- **Appearance:** theme (Dark, Light, Classic Hypixel, High contrast), accent colour, font size, row density, opacity, mini-mode filter, group by team, pin flagged first.
- **Columns:** pick, order and reset columns (level, name, team, flags, winstreak, FKDR, WLR, finals, wins, sniper, seen, last flag).
- **Alerts:** tab marks, chat alerts, sound, borderless, update check, and the dodge rules and thresholds.
- **Checks:** turn single checks on or off, pick a sensitivity (lenient, normal, strict), and tune reach, aura angle, CPS and speed limits.
- **Keys:** API keys.

Overlay layout is saved in `config/safedetect-overlay.json`; game options live in `config/safedetect-settings.json`.

Drag it from the **SafeDetect** title / Lobby-Saved tabs, not from the buttons.

## Dodge warnings

When a lobby player is blacklisted, has a saved cheat flag from an earlier game, has an Urchin/Seraph tag, scores at or above the sniper threshold (default 60), or has an FKDR at or above the threshold (default 8, with an optional star minimum), you get one warning per player per world: a red `DODGE?` chat line, a quiet sound and a title. Each rule can be turned off in **Alerts**, or all of them with `/sd dodge off`.

## Evidence and history

Every flag stores what triggered it, for example `reach 3.71, 3 long hits` or `0.60 b/t avg (limit 0.42)`. It shows in the chat alert, the flag log, the row tooltip and `/sd check`.

SafeDetect also counts how often you have been in a game with each player (once per world) in `config/safedetect-seen.json`. Entries older than 120 days are dropped. The **Seen** column shows it as `3x · 2d`.

## Commands

```
/sd help
/sd list [page]
/sd check <name>
/sd clear confirm
/sd gui
/sd friend <name>
/sd unfriend <name>
/sd key <hypixel-api-key>
/sd bl add|remove|list|import
/sd nick <shown> <real>
/sd borderless
/sd checks [code on|off]   list checks and thresholds, or toggle one (e.g. /sd checks SP off)
/sd dodge [on|off]         dodge warning status, or turn them on/off
/sd export                 write config/safedetect-export-<date>.csv and safedetect-encounters-<date>.csv
/sd reload                 re-read settings, blacklist, flags and encounters after editing them by hand
```

Optional: `/sd urchin`, `/sd seraph`, `/sd aurora`, `/sd sniper`, `/sd discord`.

Once a day SafeDetect checks GitHub for a newer release and prints a clickable line if there is one. Turn it off under **Alerts** → Update check.

## Credits

Detection ideas adapted from [Meowtils 2.0.1](https://github.com/femboytatp/meowtils) (AutoBlock, NoSlow, Killaura, Legit Scaffold, sniper name/stat rules). Gameplay modules from Meowtils are not included.
