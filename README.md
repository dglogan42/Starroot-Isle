# Starroot Isle

**Original** cozy island adventure for Android — farm, mine, fish, craft, level professions, follow the **Heartseed** story, and play **local or online visit co-op**.

Inspired by the *genre* of chill farming / exploration games, with **100% original IP**: names, creatures, biomes, items, art, and audio. Nothing here copies commercial titles or third-party assets.

[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208%2B-blue.svg)](#requirements)
[![Version](https://img.shields.io/badge/version-1.3.0-informational.svg)](#)

---

## Features (v1.3)

| Area | What you get |
|------|----------------|
| **World** | Procedural 56×56 island — Meadow, Deepwood, Crystal Hollows, Ember Shore |
| **Farming** | Till, plant, water, sleep to grow; biome-preferred crops |
| **Gathering** | Mine stone / crystal / ember rock; chop trees; forage Starroot |
| **Fishing** | Craft a rod; cast at glowing shore spots → Silverfin |
| **Puffkins** | Feed treats, bond companions that follow you |
| **Professions** | Farming, Mining, Foraging, Fishing, Crafting, Ranching (Lv 1–5) |
| **Story** | Heartseed quest chain, journal, claimable rewards, **cutscenes** |
| **Audio** | Procedural SFX + looping cozy music (day/night mood) |
| **Local co-op** | Same-device P1 / P2 controls |
| **Online** | Visit rooms (shared seed), **synced tile edits**, day-tick crops, chat, **QR share** |

---

## Requirements

- Android **8.0+** (API 26)
- JDK **17**
- Android SDK (platform **35**, build-tools)
- Optional: Node.js 18+ for the multiplayer server

---

## Quick start — build & install

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export JAVA_HOME=/path/to/jdk-17

cd starroot-isle

# Debug (package: com.starrootisle.app.debug)
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Full release (package: com.starrootisle.app, signed)
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

A convenience copy may also appear as `StarrootIsle-v1.3.0-release.apk` in the project root after packaging.

### `local.properties` (gitignored)

```properties
sdk.dir=/path/to/Android/Sdk

# Online multiplayer WebSocket (optional)
# Emulator → host machine:
ONLINE_URL=ws://10.0.2.2:8790
# Physical phone on LAN:
# ONLINE_URL=ws://192.168.1.20:8790
```

---

## Online co-op server

```bash
cd server
npm install
npm start
# listens on ws://0.0.0.0:8790
```

1. Host: in-game **Online → Create** → share **QR** or 4-letter code  
2. Friend: **Join** with code, or scan QR / open `starroot://join?room=…&ws=…`  
3. Same world seed → matching terrain; **tile edits and sleep/crop growth sync**  
4. Inventories stay personal (visit mode); chat from the Online menu  
5. Max **4** players per room  

Deep link format:

```text
starroot://join?room=ABCD&ws=ws%3A%2F%2Fhost%3A8790
```

---

## Controls

| Input | Action |
|-------|--------|
| Left stick | Move P1 |
| ACT / P1 | Use tool |
| Right stick + P2 | Local co-op player 2 |
| **Tool** | Cycle tools (long-press: P2 tool or seeds) |
| **Story** | Journal + claim rewards / cutscenes |
| **Jobs** | Profession levels |
| **Online** | Host, join, QR, chat |
| **Music / SFX** | Toggle independently |
| Sleep at tent | Restore energy; advance day; grow watered crops |

---

## Project layout

```text
starroot-isle/
├── app/                    # Android app (Kotlin)
│   └── src/main/java/com/starrootisle/app/
│       ├── audio/          # SFX + music engines
│       ├── data/           # Save/load
│       ├── game/           # World, player, quests, cutscenes, render
│       ├── net/            # WebSocket client, tile patches, QR
│       └── ui/             # MainActivity
├── server/                 # Node WebSocket lobby + tile sync
├── keystore/               # Local release keystore (gitignored)
├── gradle/                 # Wrapper
├── LICENSE
└── README.md
```

### Stack

| Piece | Tech |
|--------|------|
| Language | Kotlin |
| UI | Material 3, View Binding |
| Game | Custom Canvas `GameView`, tile world |
| Network | OkHttp WebSocket |
| QR | ZXing |
| Server | Node.js + `ws` |
| Save | SharedPreferences JSON |

---

## Original IP note

| Concept | This project |
|---------|----------------|
| Title | **Starroot Isle** |
| Companions | **Puffkins** |
| Story | **Heartseed** |
| Biomes | Meadow, Deepwood, Crystal Hollows, Ember Shore |
| Art / audio | Procedural only — no third-party game assets |

Do **not** ship under names, logos, or designs from other games.

---

## License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE).

```
Copyright (c) 2026 Starroot Isle contributors
```

You may use, modify, and distribute freely, provided the license notice is preserved.

---

## Contributing

1. Keep new content original (names, lore, art, sound).  
2. Prefer small, focused PRs.  
3. Test debug + release builds; if changing multiplayer, test with `server/`.  

---

## Changelog (summary)

- **1.3.0** — Synced online tiles & day-ticks, quest cutscenes, room QR share/scan  
- **1.2.0** — Online visit co-op, Heartseed quests, procedural music  
- **1.1.0** — Biomes, professions, sprites, local co-op, SFX  
- **1.0.0** — First playable island slice  
