package com.roubeumovo;

import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Os 9 biomas do mapa, em ordem fixa (como no Steal an Egg). O indice = nivel de velocidade recomendado. */
public enum Biome {
	FLORESTA("Floresta", "Galinha", ChatFormatting.GREEN, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.OAK_LOG),
	LAGO("Lago", "Cisne", ChatFormatting.AQUA, Blocks.SAND, Blocks.GRAVEL, Blocks.LIGHT_BLUE_CONCRETE),
	DESERTO("Deserto", "Escorpiao", ChatFormatting.YELLOW, Blocks.SAND, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE),
	SELVA("Selva", "Tigre", ChatFormatting.DARK_GREEN, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.JUNGLE_LOG),
	NEVE("Neve", "Yeti", ChatFormatting.WHITE, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.PACKED_ICE),
	VULCAO("Vulcao", "Golem de Lava", ChatFormatting.RED, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.POLISHED_BLACKSTONE_BRICKS),
	ABISMO("Abismo Oceanico", "Kraken", ChatFormatting.DARK_AQUA, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS),
	PRE_HISTORICO("Pre-Historico", "T-Rex", ChatFormatting.GOLD, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.BONE_BLOCK),
	COSMICO("Cosmico", "Rei Dragao", ChatFormatting.DARK_PURPLE, Blocks.END_STONE, Blocks.OBSIDIAN, Blocks.PURPUR_BLOCK);

	public final String label;
	public final String guardian;
	public final ChatFormatting color;
	public final Block floor;
	public final Block alt;
	public final Block gate;

	Biome(String label, String guardian, ChatFormatting color, Block floor, Block alt, Block gate) {
		this.label = label;
		this.guardian = guardian;
		this.color = color;
		this.floor = floor;
		this.alt = alt;
		this.gate = gate;
	}

	/** Velocidade (blocos/s) do guardiao. Jogador correndo: ~5,6 * (1 + 0,2 * nivel). */
	public static double guardianSpeed(int biome) {
		return 5.0 * (1 + 0.2 * biome);
	}
}
