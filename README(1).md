# 2DLE — 2D Level Editor Engine

> A complete 2D RPG game engine and level editor for **J2ME / MIDP 2.0** mobile devices.  
> Build, script, and publish full games that run on classic Nokia and Sony Ericsson feature phones.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-J2ME%20MIDP%202.0-blue.svg)](https://en.wikipedia.org/wiki/Java_Platform,_Micro_Edition)
[![Java](https://img.shields.io/badge/Java-1.3%20%28CLDC%201.0%29-orange.svg)](https://en.wikipedia.org/wiki/Connected_Limited_Device_Configuration)
[![Tiles](https://img.shields.io/badge/Tiles-87%20types-green.svg)](#tile-reference)
[![Format](https://img.shields.io/badge/Export-.2dip-purple.svg)](#publishing-games)

---

## What is 2DLE?

2DLE is a **self-contained RPG engine** that runs entirely on J2ME feature phones — no PC tools needed at runtime. Design your game maps directly on the device, write scripts in the built-in 2DLS language, place NPCs from a library of 15 built-in characters, link maps together with doors and warps, then export everything into a single `.2dip` game package that players can download and play.

**The editor IS the game engine.** Switch between editing and playtesting with a single key press. When you're done, compile and distribute via `.2dip`.

---

## Screenshots

```
┌─────────────────────────────────┐   ┌─────────────────────────────────┐
│  Lv│level01  *=pause 0=save     │   │ ██░░░░░  NPC Mode               │
│ ██ │                             │   │ ░░░░░░░  5=BuiltinPicker        │
│    │   [EDITOR MODE]             │   │ ░╔══╗░░  0=Script               │
│    │   Tile painting             │   │ ░║  ║░░  #=LinkEditor           │
│    │   NPC placement             │   │ ░╚══╝░░                         │
│    │   Script editing            │   │ S       Green dot = has script  │
│    │   Map linking               │   │ ──►     Cyan = has link         │
└─────────────────────────────────┘   └─────────────────────────────────┘
    Level Editor                           NPC + Script Overlay
```

---

## Features

### Level Editor
- **87 tile types** with pixel-art sprites — ground, walls, water, lava, ice, buildings, transport, animals
- **14 draw tools** — pencil, fill, rectangle, line, ellipse, diamond, scatter, eyedropper, select/copy/paste, stamp
- **3 map layers** — background, walls, objects — plus NPC mode
- **3 zoom levels** (x1, x2, x3)
- **Undo/redo** with 64-step history
- **Auto-save** before New Map and before Export
- Visual overlays for scripts (green dot) and links (cyan border + arrow)

### Scripting — 2DLS Language
- Custom line-based scripting language compiled to **bytecode**
- **35 commands** — dialogue, choices, variables, switches, items, combat, weather, teleport, quests, audio
- Scripts attach to **tile types**, **specific NPCs**, or **map load events**
- **In-editor Script Editor** — write, compile, and test without leaving the device
- **10 built-in templates** — chest, shop NPC, quest giver, boss battle, and more
- Scripts are saved as `.2dls` source + `.2dlb` bytecode, both packed into `.2dip`

### NPC System
- 15 **built-in characters** — place with full scripts pre-attached in one keypress
- 10 friendly/quest NPCs: Villager, Guard, Merchant, Innkeeper, Elder, Blacksmith, Healer, Quest Giver, Knight, Wizard
- 5 bosses: Slime King, Orc Warlord, Lich, Dragon Lord, Dark Knight
- Custom dialogue editing via built-in Dialogue Editor

### Map Links
- **Door-to-door spawning** — step on a door → appear at exact spawn point in target level
- **All transition types**: house doors, stairs up/down, telepads, warp stones, map edges
- **Trigger modes**: auto (step on) or manual (press 5)
- Links saved per-level in `.mls` files, fully packed into `.2dip`

### Game Systems
| System | Details |
|--------|---------|
| Battle | Turn-based / ATB combat, 20 skills, 10 enemy types, elemental affinities |
| Weather | 8 types with particle effects (rain, fog, snow, storm, sandstorm) |
| Day/Night | 24-hour cycle, seasons, moon phases, darkness overlay |
| Quests | 80 quests with 5 objective types |
| Inventory | Items, equipment, gold, shops, haggling |
| Factions | 10 factions with reputation ripple effects |
| Stealth | Stealth level, tile modifiers, enemy detection |
| Crafting | Recipe-based item crafting |
| Dungeon Gen | Procedural BSP dungeon generation |

### `.2dip` Distribution Format
- Compile your entire project into **one file** for distribution
- Packs: all levels, map links, compiled scripts, script sources, NPC dialogues, all assets (PNG sprites, icons), and any other project files
- Players only need `GamePlayerMIDlet.jar` + the `.2dip` file
- Or bundle `game.2dip` inside the JAR for a **fully self-contained game**

---

## Project Structure

```
src/
├── Core Engine
│   ├── LevelEditorMIDlet.java   App lifecycle, screen switching
│   ├── EditorCanvas.java        Main editor (4500 lines)
│   ├── PlayEngine.java          Player movement, particles, physics
│   ├── BattleSystem.java        Combat engine
│   └── GameConfig.java          All engine constants
│
├── Scripting
│   ├── ScriptLang.java          2DLS compiler (text → bytecode)
│   ├── ScriptManager.java       Script registry
│   ├── ScriptEditor.java        In-editor script UI
│   └── EventSystem.java         Bytecode interpreter
│
├── Map System
│   ├── TileData.java            87 tile types with all data arrays
│   ├── MapLinkSystem.java       Door/warp/stair transitions
│   └── LinkEditor.java          Link configuration UI
│
├── NPCs & Characters
│   ├── NPCManager.java          NPC positions and dialogue
│   ├── BuiltinNPCs.java         15 ready-made characters
│   ├── EnemyData.java           10 enemy types
│   └── PlayerData.java          Player stats and serialization
│
├── Distribution
│   ├── GameCompiler.java        Project → .2dip packager
│   ├── GamePlayer.java          .2dip runtime player
│   └── GamePlayerMIDlet.java    Public distribution MIDlet
│
├── World Systems
│   ├── WeatherSystem.java
│   ├── DayNightSystem.java
│   ├── FactionSystem.java
│   ├── StealthSystem.java
│   ├── DungeonGenerator.java
│   └── WorldMap.java
│
└── Support
    ├── FileManager.java         Storage, backup, project CRUD
    ├── ProjectManager.java      Splash screen, install chooser
    ├── SoundManager.java        32 SFX + 21 music tracks
    ├── InventorySystem.java
    ├── SkillSystem.java
    ├── QuestSystem.java
    ├── ShopSystem.java
    ├── CraftingSystem.java
    ├── DialogueEditor.java
    ├── CutsceneSystem.java
    └── AISystem.java
```

---

## Building

### Requirements
- **Java ME SDK** (J2ME Wireless Toolkit 2.5.2 or equivalent)
- Java source/target level **1.3**
- MIDP 2.0 / CLDC 1.0 APIs

### With J2ME Wireless Toolkit
```
1. Open WTK 2.5.2
2. File → Open Project → select "2D Level Editor"
3. Build → Build
4. Run → Run (emulator) or Deploy → Create Package (for .jar/.jad)
```

### With Ant
```bash
ant -f build.xml
```

### Important J2ME Constraints
The codebase strictly follows J2ME 1.3 rules — these will cause compile errors if violated:
- ❌ No binary literals (`0b101`) — use decimal or hex
- ❌ No `String.replace(String, String)` — use `substring`/`endsWith`
- ❌ No `Character.isLetterOrDigit()` — use manual range checks
- ❌ No enhanced for-loops (`for (Type x : collection)`)
- ❌ No generics, no varargs, no autoboxing
- ✅ Use `Vector` not `ArrayList`
- ✅ Use `Canvas()` + `setFullScreenMode(true)` not `Canvas(boolean)`

---

## Editor Controls

| Key | Action |
|-----|--------|
| `2` `4` `6` `8` | Move cursor |
| `5` | Apply draw tool / place NPC (opens Builtin Picker in NPC mode) |
| `0` | Open Script Editor for tile or NPC under cursor |
| `1` / `3` | Previous / next tile (or NPC type) |
| `#` | On door/warp tile: open Link Editor. Otherwise: cycle layer → NPC mode |
| `*` | Toggle grid / cancel shape drag |
| `7` / `9` | Cycle draw tool |
| `Softkey` | Menu (Save, Load, New Map, Export .2dip, Play .2dip, Exit…) |

---

## Tile Reference

### Ground & Nature (0–39)
`Void` `Grass` `Dirt` `Stone` `Rock` `Water` `Brick` `Wood` `Chest` `Lava` `Path` `FloorTile` `Carpet` `Tree` `Flower` `TallGrass` `Snow` `Ice` `Vine` `Cactus` `Cloud` `Door` `StairsUp` `StairsDn` `Sign` `Fence` `Roof` `Bridge` `Barrel` `Bookshelf`

### Interactive & Hazard (40–65)
`SpikeTrap` `Fireplace` `Altar` `WarpStone` `CrackedWall` `DeepWater` `Conveyors×4` `BombWall` `Torch` `CrystalBall` `TrapFloor` `Vine` `GlowOrb` `Mirror` `Seabed` `NightSky` `Volcano` `Crystal` `Cave` `HouseDoor` `HouseExit`

### Buildings (66–73) — NEW
`Shop` `Inn` `CastleWall` `CastleGate` `Tower` `Ruin` `Temple` `HouseWall`

### Transport (74–78) — 4-frame animated
`Boat` `Cart` `Horse` `Airship` `Raft`

### Animals (79–86) — 4-frame animated
`Bird` `Fish` `Cat` `Dog` `Rabbit` `Deer` `Wolf` `Bear`

---

## 2DLS Scripting Language

Quick reference — see the full documentation for all 35 commands.

```
// Treasure chest — opens once, gives item, remembers state
if switch 10 on goto empty
sound 10
text "You found the Ancient Sword!"
give item 10 1
give exp 30
set switch 10 on
goto done
label empty
text "(The chest is empty.)"
label done
end
```

```
// Boss battle with weather and music
if switch 50 on goto dead
text "The Dragon awakens!"
shake 5 40
weather 4 100
fadeout 3
music 4
fadein 3
battle 1 9
set switch 50 on
weather 0 0
music 5
text "The dragon falls..."
give exp 500
goto done
label dead
text "The air is silent here."
label done
end
```

### Script Commands
| Category | Commands |
|----------|----------|
| Dialogue | `text` `choice` `wait` |
| Variables | `set var` `add` `sub` `mul` `div` `mod` `rnd` |
| Switches | `set switch on/off` |
| Flow | `if` `goto` `label` `loop` `endloop` `break` `end` |
| Items | `give item` `take item` `give gold` `take gold` `give exp` |
| Player | `heal` `damage` `status` `learnSkill` `changeClass` |
| World | `teleport` `weather` `shake` `fadeout` `fadein` |
| Audio | `sound` `music` |
| Combat | `battle` `shop` `inn` `craft` `savepoint` `cutscene` |
| Quests | `startQuest` `checkQuest` `updateQuest` |

---

## Map Linking

```
// Entering a house (level01 → inn)
// 1. Paint TILE_HOUSE_DOOR (64) at (8, 6) in level01
// 2. Press # on it → Link Editor
//    Target: inn   SpawnX: 5   SpawnY: 3   Facing: Down   Trigger: Auto

// Exiting the house (inn → level01)
// 1. Paint TILE_HOUSE_EXIT (65) at (5, 3) in inn
// 2. Press # on it → Link Editor
//    Target: level01   SpawnX: 8   SpawnY: 7   Facing: Up   Trigger: Auto
```

| Transition | Tile | Trigger |
|------------|------|---------|
| House enter | `TILE_HOUSE_DOOR` (64) | Auto — step on |
| House exit | `TILE_HOUSE_EXIT` (65) | Auto — step on |
| Dungeon down | `TILE_STAIRS_DN` (23) | Auto — step on |
| Dungeon up | `TILE_STAIRS_UP` (22) | Auto — step on |
| Locked gate | `TILE_CASTLE_GATE` (69) | Manual — press 5 |
| Warp pad | `TILE_TELEPAD` (36) | Auto — step on |
| Map edge | No tile needed | Choose EdgeN/S/E/W |

---

## Publishing Games

### Compile to `.2dip`
```
Menu → Export .2dip
  Title:       My Game
  Author:      My Studio
  Version:     1.0
  Start Level: level01
→ Press "Build .2dip"
→ Saved to: 2DLE/<project>/publish/MyGame.2dip
```

### What gets packed
Every file in your project is included automatically:

| Section | Contents |
|---------|----------|
| `LEVEL` | All `.l2de` level files |
| `LINKS` | All `links_*.mls` map transition files |
| `SCRIPTS` | `scripts.sreg` compiled script registry |
| `SCRIPSRC` | All `.2dls` script source files |
| `SCRIPBIN` | All `.2dlb` compiled bytecode files |
| `NPC` | `npcs.txt` dialogue data |
| `ASSET` | Everything in `assets/` (PNG sprites, icons…) |
| `ICON` | `icon.png` shown on title screen |
| `META` | Title, author, version, counts |

### Distributing to Players
```
Option A — Separate files (simplest):
  1. Give players: GamePlayerMIDlet.jar + MyGame.2dip
  2. They install the JAR, place the .2dip anywhere on their storage
  3. GamePlayer auto-scans and lists all .2dip games found

Option B — Self-contained JAR:
  1. Rename MyGame.2dip to game.2dip
  2. Place it inside GamePlayerMIDlet.jar as a resource
  3. Distribute only the JAR — auto-loads on startup
```

### Playing from Inside the Editor
```
Menu → Play .2dip Game → pick your game
*  (once)  = pause  →  shows "* again = exit"
*  (twice) = exit back to editor
```

---

## .2dip File Format

Binary format for game distribution:

```
[4 bytes]  Magic = 0x32444950 ("2DIP")
[4 bytes]  Format version
[4 bytes]  Flags (reserved)
[UTF]      Game title
[UTF]      Author
[UTF]      Version string
[UTF]      Start level name
[4 bytes]  Section count

For each section:
  [UTF]     Type  (LEVEL / LINKS / SCRIPTS / SCRIPSRC / SCRIPBIN / NPC / ASSET / ICON / META / RAW)
  [UTF]     Name  (e.g. "level01", "hero.png", "scripts.sreg")
  [4 bytes] Data length
  [N bytes] Data
```

---

## Built-in NPC Library

| ID | Name | Type | Default Script |
|----|------|------|----------------|
| 0 | Villager | Friendly | Greet on first meet, give 5 EXP |
| 1 | Guard | Friendly | Block passage with choice dialogue |
| 2 | Merchant | Shopkeeper | Opens shop 0 |
| 3 | Innkeeper | Friendly | Rest for 10 gold |
| 4 | Elder | Quest | Lore + give 2 Potions on first meet |
| 5 | Blacksmith | Shopkeeper | Weapon shop + crafting |
| 6 | Healer | Friendly | Free heal once, then 20 gold |
| 7 | Quest Giver | Quest | Full quest accept/complete/reward |
| 8 | Knight | Friendly | Training battle, gives Axe |
| 9 | Wizard | Quest | Teach Fire or Ice skill for 50 gold |
| 10 | **Slime King** | **BOSS** | Boss fight +lv3, 200 EXP |
| 11 | **Orc Warlord** | **BOSS** | Boss fight +lv5, 200 EXP + gold |
| 12 | **Lich** | **BOSS** | Storm weather + boss, 350 EXP |
| 13 | **Dragon Lord** | **BOSS** | Final boss, 500 EXP + gold |
| 14 | **Dark Knight** | **BOSS** | Story boss with dialogue branch |

---

## Compatibility

| Device | Status |
|--------|--------|
| Nokia S40 (3rd–6th edition) | ✅ Target platform |
| Nokia S60 (2nd–3rd edition) | ✅ Compatible |
| Sony Ericsson JP-8/JP-7 | ✅ Compatible |
| Motorola iDEN / RAZR | ✅ Compatible |
| Any MIDP 2.0 / CLDC 1.0 device | ✅ Should work |
| Android via J2ME emulator | ✅ (PSPKVM, J2ME Loader) |

---

## Roadmap

- [ ] Upload source `.java` files to repository
- [ ] Add sample project with demo levels
- [ ] Add pre-built `.jar` and `.jad` release
- [ ] Add sample `.2dip` demo game
- [ ] Tileset image support (custom PNG tilesets override generated sprites)
- [ ] Multiplayer battle via Bluetooth
- [ ] Music tracker format for in-engine music composition

---

## Contributing

Contributions are welcome. Please keep all code **J2ME 1.3 compatible**:

- Use `Vector` not `ArrayList`
- No binary literals (`0b...`)
- No `String.replace(String, String)` — use `substring` workarounds
- No `Character.isLetterOrDigit()` — use `>= 'a' && <= 'z'` range checks
- No enhanced for-loops, no generics, no autoboxing
- Test on WTK 2.5.2 emulator before submitting

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

## Author

**Dahmalahi** — [@Dahmalahi](https://github.com/Dahmalahi)

> *Built for a platform that fits in your pocket and runs without an internet connection.*  
> *Every feature works on a 2003 Nokia.*
