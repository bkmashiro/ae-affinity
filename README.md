# AE Affinity

A small, server-side Applied Energistics 2 addon that slowly improves storage placement according to endpoint affinity.

## Target

- Minecraft 1.21.1
- NeoForge 21.1
- Applied Energistics 2 19.2.x
- Java 21

The MVP registers no blocks or items and contains no client code.

## What the MVP does

- Registers one headless grid service for every AE grid.
- Preserves storage-provider provenance with one narrow mount/unmount mixin.
- Automatically recognizes ordinary AE item cells and direct slotted containers behind storage buses.
- Leaves nested ME storage and unknown storage implementations opaque.
- Excludes extract-only sources, insert-only targets, unknown cells, and storage buses/cells with void upgrades.
- Moves sparse unstackable items (up to four of an exact key) from cells to slotted storage.
- Moves numerous unstackables and ordinary bulk stacks from slotted storage back toward cells.
- Uses read-only planning rounds followed by one synchronous validate-and-commit tick.
- Returns any insertion remainder to the source in the same server tick.
- Exponentially backs off from 10 seconds to 15 minutes when rounds find no useful work.

## Activation

The default server config is `ANCHORED`, so installing the mod does not immediately rearrange every network.

With operator permission, target any loaded AE node position:

```text
/aeaffinity enable <x> <y> <z>
/aeaffinity disable <x> <y> <z>
/aeaffinity status <x> <y> <z>
```

`activation=ALL` enables every grid; `OFF` pauses scheduling. Server config is generated as `config/aeaffinity-server.toml`.

If a network splits, only the side containing the anchored node remains enabled. A merged network is enabled when it contains at least one anchor.

## Build and verify

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew clean test build
./gradlew runServer
```

The current tests cover scheduler phase separation and backoff, affinity direction, full simulation gating, partial insertion rollback, and rollback failure reporting.

## Current boundary

Subnetwork aggregation and third-party affinity adapters are intentionally not guessed in the MVP. A storage bus targeting another `MEStorage` remains opaque until a later aggregate-report protocol can describe it safely.
