# Prune (Fabric, 26.3)

Keeps a server's world small by deleting chunks nobody used, without leaving cut-off trees or warped terrain behind.

- **Prune on startup** (`pruneOnStartup`): before the worlds load, every chunk with less `InhabitedTime` than `pruneMinInhabitedTicks` (6000 = 5 minutes) is deleted from the region, entities and poi files, and the files are rewritten so the space comes back. Unreadable chunks are kept. No MCASelector needed.
- **Protection**: chunks where players placed/broke/used blocks, died, logged off, or where items, explosions, fire, pistons, portals and villager trades happened count as used.
- **Feature restore** (`restoreFeatures`): while pruning is on, each chunk remembers the blocks its features (trees, ores, plants, ...) wrote into its neighbours. When a pruned neighbour regenerates, those blocks are put back, so trees aren't cut at the border. Records of neighbours that can never regenerate are dropped at each prune (~2-3% of world size in the long run).
- **Chunk blending** (`chunkBlending`, `blendMode`): when a new chunk generates next to one saved by an older version, vanilla bends the new terrain toward the old chunk, which floods or dries out land around lone kept chunks. `"blendMode": "difference"` (default) instead shifts the new chunk only by how much each old chunk differs from a fresh copy of itself, so borders line up without warping the land. `"vanilla"` keeps vanilla's blend, `"chunkBlending": false` turns blending off.

Config: `config/PandaPrune.json`. `pruneDimensions` empty = all dimensions.

## Building

`./gradlew build` (Java 25). The jar is in `build/libs/`.

## License

[CC0](https://creativecommons.org/public-domain/cc0/)
