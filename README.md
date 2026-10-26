# Golden Golem

A Paper 26.3 plugin that adds a Golden Golem: a helper you build from a gold block and a carved
pumpkin. Give it a pickaxe, axe or shovel and a sample block, lead it to a spot, and it gathers
that block nearby and carries the drops back to its golden chest.

Players don't install anything. Java and Bedrock (through Geyser) players both get it.

**Status:** in development toward 0.1.0.

## Install

Drop `golden-golem.jar` from the [latest release](https://github.com/angelocordon/golden-golem/releases/latest)
into the server's `plugins/` folder and restart.

## Build

CI builds every push. Locally, with JDK 25 and Gradle:

```bash
gradle build
```

The jar lands in `build/libs/golden-golem.jar`.

See [CONTRIBUTING.md](CONTRIBUTING.md) for how work is organized.
