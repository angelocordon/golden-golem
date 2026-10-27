# Golden Golem

A Paper 26.3 plugin that adds a Golden Golem: a helper you build from a gold block and a carved
pumpkin. Give it a pickaxe, axe or shovel and a sample block, lead it to a spot, and it gathers
that block nearby and carries the drops back to its golden chest.

Players don't install anything. Java and Bedrock (through Geyser) players both get it.

## Install

Drop `golden-golem.jar` from the [latest release](https://github.com/angelocordon/golden-golem/releases/latest)
into the server's `plugins/` folder and restart. Requires Paper 26.3.

## How to play

1. **Craft a Golden Chest:** a chest in the middle of the crafting grid with 8 gold ingots
   around it. Place it where you want the loot to go. Two side by side make a double chest,
   which can serve two golems.
2. **Build the golem:** place a block of gold within 4 blocks of the chest and put a carved
   pumpkin (or jack o'lantern) on top. The golem links to the nearest golden chest with room.
3. **Equip it:** right-click the golem to open its menu. Put a pickaxe, axe or shovel in the
   tool slot and one block of what it should gather in the target slot (logs with an axe; stone,
   ores and similar with a pickaxe; dirt, sand and gravel with a shovel).
4. **Put it to work:** click the Follow/Stay button (or sneak + right-click the golem) so it
   follows you. Walk to the spot and click Stay. It works a 33x33 area around that spot, 4
   blocks up and down, and must be within 64 blocks of its chest.

The golem walks home when its 15 slots are full, when its tool is nearly worn out, or when the
area runs out. At the chest it drops everything off and swaps a worn tool for the best spare of
the same kind it finds there. It never uses a tool up completely.

If it gets stuck (no tool, chest full, chest gone, can't reach), it shakes its head, tells you
why in the action bar, and checks again every few seconds.

**More:**
- Right-click it with a gold ingot to heal it.
- If it dies, it drops its tool, target block and everything it carried.
- To relink it to another chest, have it follow you and right-click that golden chest.
- It respects protection plugins: it only breaks blocks its owner could break. It pauses while
  its owner is offline and picks up again when they're back.
- It won't dig a block with sand or gravel on top if that would bury it, and it skips blocks
  next to lava or above a big drop.

## Configuration

`plugins/GoldenGolem/config.yml`: work radius (default 16, max 32), vertical range, how far the
work spot may be from the chest, how many blocks it scans per tick, and when a tool counts as
worn out. Restart after changing it.

Permission `goldengolem.admin` (default: op) lets a player open and control other players'
golems.

## Known limitations (0.1.0)

- The golem looks like a copper golem with a gold name and gold particles. There's no custom
  texture yet.
- Golems in unloaded chunks aren't counted toward a chest's two-golem limit.
- It doesn't flee when hurt, and mined blocks drop no experience.
- Each block the golem breaks is reported to other plugins as broken by its owner. That's how
  protection plugins apply, but it also means plugins like mcMMO or Jobs may reward the owner,
  and anti-cheat plugins may block breaks because the owner is far away.
- Not yet play-tested at scale; please report issues.

## Build

CI builds and tests every push, and boots a real Paper 26.3 server with the plugin. Locally,
with JDK 25 and Gradle:

```bash
gradle build
```

The jar lands in `build/libs/golden-golem.jar`.

See [CONTRIBUTING.md](CONTRIBUTING.md) for how work is organized.
