# SafeDetect

Personal Bedwars util for **Lunar Client 1.8.9**. It watches nearby players, shows stats on a small overlay, and remembers people you have already seen.

It is not a Lunar mod. It loads with `-javaagent`.

## Build

Java 8 (Temurin 8 is what Lunar 1.8.9 uses):

```powershell
powershell -ExecutionPolicy Bypass -File agent\build.ps1 -Deploy
```

That writes `agent\out\safedetect-agent.jar` and copies it to `%APPDATA%\.minecraft\safedetect\safedetect-agent.jar`.

## Install

1. Copy `safedetect-agent.jar` to `%APPDATA%\.minecraft\safedetect\`.
2. In Lunar Client, select **1.8.9** → JVM arguments, and add:

   `-javaagent:C:\Users\YOURNAME\AppData\Roaming\.minecraft\safedetect\safedetect-agent.jar`

3. Fully quit Lunar, then launch 1.8.9.
4. You should see `[SD] SafeDetect is running.` Type `/sd help`.

## Stats

Stars / FKDR / WLR need a Hypixel API key from [developer.hypixel.net](https://developer.hypixel.net/):

```
/sd key YOUR_KEY
```

## Overlay

Keep Lunar in borderless fullscreen.

| Control | What it does |
| --- | --- |
| Click a name | Copy `/wdr name cheating` and add them to the local blacklist |
| Refresh | Reload lobby stats |
| Tab / Chat / Sound | Toggle tab marks, chat alerts, and pings |
| Clear | Wipe every saved tracker name |
| `/sd gui` | Bring the overlay back if it was closed |

Drag it from the **SafeDetect** title / Lobby-Saved tabs, not from Refresh/Close.

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
```

Optional: `/sd urchin`, `/sd seraph`, `/sd aurora`, `/sd sniper`, `/sd discord`.

## Credits

Detection ideas adapted from [Meowtils 2.0.1](https://github.com/femboytatp/meowtils) (AutoBlock, NoSlow, Killaura, Legit Scaffold, sniper name/stat rules). Gameplay modules from Meowtils are not included.
