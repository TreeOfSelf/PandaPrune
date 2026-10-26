package net.TreeOfSelf.PandaPrune;

import net.minecraft.server.level.WorldGenRegion;

public interface DiffBlender {
	void pandaprune$attach(WorldGenRegion region);

	boolean pandaprune$isDiff();

	float pandaprune$offset(int blockX, int blockZ);
}
