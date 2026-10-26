# Prune (Fabric, 26.3)

> **Warning:** use this mod at your own risk. Its whole purpose is to delete world files. I am a hobbyist modder and cannot guarantee the safety of your worlds or server. Keep backups and use mods responsibly.

Keeps a server's world small by deleting chunks nobody used, without leaving cut-off trees or warped terrain behind.

- **Prune on startup** (`pruneOnStartup`): before the worlds load, every chunk with less `InhabitedTime` than `pruneMinInhabitedTicks` (6000 = 5 minutes) is deleted from the region, entities and poi files, and the files are rewritten so the space comes back. Unreadable chunks are kept. No MCASelector needed.
- **Protection**: chunks where players placed/broke/used blocks, died, logged off, got off a mount, or where items, explosions, fire, pistons, portals, villager trades or named/leashed/tamed/ridden mobs were count as used. Prune marks them with its own chunk flag (`panda-prune:used`), so vanilla data like `InhabitedTime` is never changed; chunks with 5+ minutes of vanilla `InhabitedTime` count as used too.
- **Chunks from before the install**: on the first start with Prune, every chunk that already exists is recorded (`world/prune_old_chunks/`) and never pruned, because what players did there before was never tracked. `"pruneOldChunks": true` prunes them by `InhabitedTime` alone.
- **Feature restore** (`restoreFeatures`): while pruning is on, each chunk remembers the blocks its features (trees, ores, plants, ...) wrote into its neighbours. When a pruned neighbour regenerates, those blocks are put back, so trees aren't cut at the border. Records of neighbours that can never regenerate are dropped at each prune (~2-3% of world size in the long run).
- **Chunk blending** (`chunkBlending`, `blendMode`): when a new chunk generates next to one saved by an older version, vanilla bends the new terrain toward the old chunk, which floods or dries out land around lone kept chunks. `"blendMode": "difference"` (default) instead shifts the new chunk only by how much each old chunk differs from a fresh copy of itself, so borders line up without warping the land. `"vanilla"` keeps vanilla's blend, `"chunkBlending": false` turns blending off.

Removing the mod is safe: vanilla and other loaders ignore its chunk flag, and Fabric drops it on the next save.

Config: `config/PandaPrune.json`. Pruning is off until `"pruneOnStartup": true`. `pruneDimensions` empty = all dimensions.

## Building

`./gradlew build` (Java 25). The jar is in `build/libs/`.

## License

[CC0](https://creativecommons.org/public-domain/cc0/)
