# Larper Client

**An addon for [Meteor Client](https://meteorclient.com/)**, made for **visuals** and **automation**. It works on **DonutSMP** too, and has a DonutSMP region finder built in.

**Free to use.** Download it, use it, change it and share it. See [License](#license).

![Wing styles: Dragon, Mech, Inferno and Shadow](wings-preview.webp)

## Download

Get the jar for your Minecraft version from the **[Releases](../../releases)** page:

| Minecraft | Jar | Needs |
|---|---|---|
| 1.21.11 | `larper-client-0.1.0-mc1.21.11.jar` | Fabric Loader, Meteor Client for 1.21.11, Java 21 |
| 26.1.2 | `larper-client-0.1.0-mc26.1.2.jar` | Fabric Loader, Meteor Client for 26.1.2, Java 25 |

Put the jar in your `.minecraft/mods` folder next to Meteor Client. The modules show up in the **Larper Client** category.
Custom Sky also needs the [Skyboxify](https://modrinth.com/mod/skyboxify) mod.

## Modules

### Visuals (only you see them)
| Module | What it does |
|---|---|
| Knife | CS2-style knives (butterfly and karambit with animated arms, plus more) in an empty slot or over another item, with an inspect animation |
| Hand Aura | Colours your hand and held item, with swirling particles |
| Wings | Dragon, mech, fire or shadow wings on your back, with an optional halo |
| China Hat | A glowing cone hat on your head |
| Orbit Orbs | Glowing orbs circling you |
| Trail | A glowing ribbon behind you |
| Jump Rings | Shockwave rings when you jump and land |
| Magic Circle | A turning circle under your feet: Runes, Sun, Ripples, Gear or Portal design |
| Footprints | Glowing footprints where you walk, fading behind you |
| Atmosphere | Fireflies, snow, petals or embers drifting around you |
| Target Circle | A glowing ring sliding up and down whoever you're fighting |
| Damage Numbers | Floating damage (and healing) numbers when mobs and players get hit |
| Kill Effects | Lightning, a shockwave, hearts or rising souls when you kill something |
| Custom Crosshair | Your own crosshair: cross, dot, circle or chevron, with spread and a cooldown bar |
| Cinematic Death | A sword to the liver, a last look, then they go limp. Players rise as angels |
| Custom Sky | Any image, **GIF link** or **YouTube video** as your sky, up to 120 fps, with play/pause (yt-dlp and ffmpeg download automatically the first time on Windows) |
| Custom Totem | Your own totem image or **animated GIF** (file or link) and pop sound (with speed, start and end controls) |
| Skin Changer | Wear any skin: a PNG file or any player's username |
| Compass | A compass strip with directions and degrees |
| BPS | Your speed in blocks per second |
| Hit Sounds | Your own sound when a hit lands |
| Crystal FX | End crystals glow, with spin rings, light pillars, a spawn ring, and a shockwave plus sparks when one pops |
| Anchor FX | Respawn anchors show their charge, pulse when charged, get a warning beam when full and blow with a shockwave and sparks |
| Mace FX | Landing target, smash damage preview and a big shockwave on the hit |
| Spear FX | Spears: glowing tip, charge streaks, jab trails, lunge lines and hit bursts |
| Anime FX | Manga speed lines when you go fast and impact frames when you hit |
| Electric Aura | Lightning arcs crackling around you |
| Energy Shield | A glowing bubble or hex force field around you that ripples when you get hit |
| Shooting Stars | Shooting stars, meteor showers and comets across the sky |
| Screen Shaders | Chromatic, retro CRT, dreamy glow, movie grade, noir, vaporwave, comic or custom |
| Player Model | Wear a Figura avatar (.zip) or Blockbench model: put it in `.minecraft/larper-client/models` |
| Camera Tilt | The camera leans into strafes and turns |
| Dynamic FOV | FOV widens smoothly the faster you go |
| Smooth Camera | Mouse look glides instead of snapping |
| Smooth Third Person | The third-person camera trails you with a soft lag |
| No Fog | Removes fog in the Overworld, Nether and End |
| Click GUI | Restyles Meteor's click GUI: 24 colour themes or your own, opacity, rainbow, layout and size |
| Keybinds | Your keybinds on screen, green when that module is on. Drag and resize it anywhere |
| Module List | The modules you have on, sliding in and out. Drag and resize it anywhere |

### Automation
| Module | What it does |
|---|---|
| Auto Firework | Elytra autopilot: holds a height with rockets from your hotbar, with human-like timing |
| Fast Place | Places blocks or uses items faster, with LiquidBounce-style settings |
| TNT Cart Assist | Predicts your flame-bow arrow and places a rail and TNT minecart in its path (normal reach only) |
| Key Wind Charge | Throws a wind charge with one key |
| Twerk | Crouches and uncrouches fast |
| Schematic Builder | Builds a .litematic/.schem/.nbt like a player: takes materials from your chest, places every block facing the right way (uses temporary supports, crouching or pillaring when needed) and walks with Baritone |
| Area Miner | Left-click two corners and it clears the box out with a real held left click; made for 3x3 pickaxes like the Shard Pickaxe, no Baritone needed |
| Debris Finder | Finds ancient debris from the world seed and checks each spot against what the server shows you |
| Auto Miner | Mines your chosen ores with Baritone, pausing near players, at low health or when you move |
| Pearl Catch | Throws a pearl and a wind charge in the same tick so the pearl catches it in mid-air |
| XP Macro | Throws XP bottles from your hotbar at your feet while you hold a key, until your gear is mended |
| Silent Crystal | Crystal macro with a silent swap |
| Silent Anchor | Anchor macro: place, charge and blow a respawn anchor with silent swaps |

### Utility
| Module | What it does |
|---|---|
| Region Route | **DonutSMP:** which way and how far to any region, with blocks N/S/E/W, nether coordinates and a waypoint. Also the `.region` command (`.region 5`, `.region 28 5`) |
| Ore Sim | Shows where ores generate from the world seed (`.seed <seed>`). Ported from Meteor Rejects |
| Turn Off All | Turns off every module at once |
| Auto Home Method | Sets a home, /rtp's away, then /home's back |
| Render Method | Drops your render distance to 2 for a moment, then back to normal |

> ⚠️ **Use at your own risk. I'm not responsible for any bans.** Check a server's rules before using it there. Many servers don't allow automation modules.

## More is coming, and I need suggestions

I'll keep adding to this. **I need suggestions:** if you have an idea for a module or a visual, or find a bug, [open an issue](../../issues).

## Building it yourself

Each Minecraft version has its own folder:

```
cd mc-1.21.11     # or mc-26.1.2 (needs JDK 25)
./gradlew build   # the jar ends up in build/libs/
```

## Credits

- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) and its addon template.
- [Baritone](https://github.com/cabaletta/baritone) is not bundled: install it yourself (Meteor's build) for the modules that walk with it (Schematic Builder, Auto Miner). Area Miner works without it.
- Ore Sim is ported from [Meteor Rejects](https://github.com/AntiCope/meteor-rejects) (GPL-3.0).
- The butterfly and karambit viewmodel frames come from the [CS:GO Hands](https://www.curseforge.com/minecraft/mc-mods/cs-go-hands) mod by StsThorn (MIT). The license is in the assets folder.
- The DonutSMP region layout matches [Glazed](https://github.com/realnnpg/glazed)'s Region Map.

## License

Free to use under the [GNU GPL v3](LICENSE): you can use, change and share it. If you share a changed version, it has to stay open source under the same license.
