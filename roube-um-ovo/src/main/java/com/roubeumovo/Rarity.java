package com.roubeumovo;

import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum Rarity {
	COMUM("Comum", ChatFormatting.WHITE, 0.5, 15, 60, ParticleTypes.HAPPY_VILLAGER, Blocks.COPPER_BLOCK,
		"Pintinho Sonolento", "Galinha Pintadinha", "Ovo Frito Fujao"),
	RARO("Raro", ChatFormatting.AQUA, 2, 30, 25, ParticleTypes.ENCHANT, Blocks.LAPIS_BLOCK,
		"Tartaruga Turbinada", "Sapo Cururu", "Lagarto Gamer"),
	EPICO("Epico", ChatFormatting.LIGHT_PURPLE, 8, 60, 10, ParticleTypes.PORTAL, Blocks.AMETHYST_BLOCK,
		"Sniffer Sorrateiro", "Axolote Neon", "Raposa Ninja"),
	LENDARIO("Lendario", ChatFormatting.GOLD, 30, 120, 4, ParticleTypes.FLAME, Blocks.GOLD_BLOCK,
		"Dragao de Bolso", "Fenix do Churrasco", "Grifo Milionario"),
	MITICO("Mitico", ChatFormatting.RED, 120, 240, 1, ParticleTypes.TOTEM_OF_UNDYING, Blocks.DIAMOND_BLOCK,
		"Wyvern Supremo", "Ovo Cosmico Eterno", "Tung Tung Dragao");

	public final String label;
	public final ChatFormatting color;
	public final double incomePerSec;
	public final int hatchSeconds;
	public final int weight;
	public final ParticleOptions particle;
	public final Block petBlock;
	private final String[] names;

	Rarity(String label, ChatFormatting color, double income, int hatch, int weight,
		   ParticleOptions particle, Block petBlock, String... names) {
		this.label = label;
		this.color = color;
		this.incomePerSec = income;
		this.hatchSeconds = hatch;
		this.weight = weight;
		this.particle = particle;
		this.petBlock = petBlock;
		this.names = names;
	}

	public String randomName(Random rng) {
		return names[rng.nextInt(names.length)];
	}

	/** Renda por segundo; biomas mais fundos dao pets bem mais ricos. */
	public double income(int biome) {
		return incomePerSec * (1 + 1.5 * Math.max(0, biome));
	}

	public static int roll(Random rng) {
		return roll(rng, 0);
	}

	/** Biomas mais fundos aumentam a chance de raridades altas. */
	public static int roll(Random rng, int biome) {
		int[] w = {Math.max(5, 60 - 6 * biome), 25, 10 + 2 * biome, 4 + 2 * biome, 1 + biome};
		int total = 0;
		for (int v : w) total += v;
		int x = rng.nextInt(total);
		for (int i = 0; i < w.length; i++) {
			x -= w[i];
			if (x < 0) return i;
		}
		return 0;
	}
}
