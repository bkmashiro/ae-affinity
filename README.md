![AE Affinity icon](docs/icon.png)

# AE Affinity

A small server-side addon that improves where AE2 keeps items.

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![NeoForge 21.1](https://img.shields.io/badge/NeoForge-21.1-EA6847)
![AE2 19.2](https://img.shields.io/badge/AE2-19.2-45C5D9)
![Server-side](https://img.shields.io/badge/side-server--only-7657C8)
![MIT license](https://img.shields.io/badge/license-MIT-blue)

AE2 already routes items well, but fixed priorities cannot describe which storage is best for each kind of item. AE Affinity makes occasional background moves to improve that placement:

- bulk stackable items prefer cells;
- a few unstackable or component-heavy items prefer slotted inventories;
- larger groups of unstackable items prefer cells.

It adds no blocks, items or screens. Install it on the server only; clients do not need AE Affinity.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Applied Energistics 2 19.2.x
- GuideME
- Java 21

## Installation

1. Install NeoForge, AE2 and GuideME.
2. Put the AE Affinity JAR in the server's `mods/` directory.
3. Restart the server.

AE Affinity is disabled by default. To enable it for every powered AE network, edit `world/serverconfig/aeaffinity-server.toml`:

```toml
activation = "all"
```

To enable only selected networks, leave `activation = "anchored"`, look at an AE block in the network, and run:

```text
/aeaffinity enable
```

Use `/aeaffinity disable` to remove that anchor and `/aeaffinity status` to inspect the network.

## Storage support

AE Affinity currently understands:

- standard AE2 item cells;
- direct item-handler inventories behind a Storage Bus, including vanilla chests and Create Item Vaults;
- simple child ME networks exposed through an ME Interface.

A child network is eligible only when every direct insertion route is a known non-void cell or slotted inventory. Its report excludes nested networks, which prevents recursive quotes and parent-child ping-pong.

The scheduler ignores fluid and chemical storage, unknown cell implementations, Void Cells, Storage Buses with a Void Card, one-way endpoints and other storage it cannot safely roll back into.

## How it works

Each powered AE network gets a small lazy scheduler:

```text
idle
→ inspect a bounded number of storage entries
→ plan without changing storage
→ revalidate and move at most one item unit
```

A move is completed in one server tick:

```text
simulate target
→ simulate source
→ extract
→ insert
→ immediately return any remainder to the source
```

The scheduler does not keep extracted items between ticks. It wakes when AE2 reports storage changes and lightly probes direct external inventories so changes made by other mods are noticed without scanning the whole network.

## Configuration

`world/serverconfig/aeaffinity-server.toml`:

| Option | Default | Meaning |
| --- | ---: | --- |
| `activation` | `anchored` | `off`, `anchored`, or `all` |
| `chargeEnergy` | `true` | Charge normal AE insertion energy |
| `minIdleTicks` | `20` | Shortest delay between rounds |
| `maxIdleTicks` | `1200` | Longest delay after repeated idle rounds |
| `planningTicks` | `4` | Read-only planning window |

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew verifyAll
```

`verifyAll` runs the unit tests, builds the JAR and runs the headless GameTest suite. Create is used only by GameTests and is not packaged or required at runtime.

## License

[MIT](LICENSE)
