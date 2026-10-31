package com.roubeumovo;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

/**
 * O mapa: uma faixa plana de 9 biomas indo para o LESTE (+X) a partir do portao.
 * O hub com as bases fica a OESTE do portao (zona segura dos guardioes).
 * A construcao e feita aos poucos (1 coluna por tick) para nao travar o servidor.
 */
public final class WorldMap {
	public static final int BL = 70;        // comprimento de cada bioma
	public static final int HALF = 20;      // meia largura (faixa de 41 blocos)
	public static final int BIOMES = 9;
	private static final Random RNG = new Random();

	private WorldMap() {}

	public static int totalColumns() {
		return BL * BIOMES;
	}

	/** Indice do bioma na posicao, ou -1 se estiver fora da faixa. */
	public static int biomeAt(double x, double z) {
		Data.Save sv = Game.save;
		if (!sv.hasMap) return -1;
		double dx = x - sv.mapX, dz = z - sv.mapZ;
		if (dx < 0 || dx >= totalColumns() || Math.abs(dz) > HALF + 1) return -1;
		return (int) (dx / BL);
	}

	/** Centro (nivel dos pes) do k-esimo lote de base no hub, a oeste do portao. */
	public static BlockPos plotOrigin(int k) {
		Data.Save sv = Game.save;
		int col = k / 21;
		int row = k % 21 - 10;
		return new BlockPos(sv.mapX - 14 - col * 16, sv.mapY, sv.mapZ + row * 16);
	}

	// ------------------------------------------------------------------ construcao

	public static void tick(MinecraftServer server) {
		Data.Save sv = Game.save;
		if (!sv.hasMap || sv.mapBuilt) return;
		ServerLevel lv = server.overworld();
		int total = totalColumns();
		int c = sv.mapProgress;
		if (c < total) {
			buildColumn(lv, c);
			if (c >= 2) decor(lv, c - 2);
			sv.mapProgress++;
		}
		if (sv.mapProgress >= total) {
			decor(lv, total - 2);
			decor(lv, total - 1);
			sv.mapBuilt = true;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				Game.say(p, "O mapa ficou pronto! Use /ovo mapa para ver os biomas e /ovo base para pegar seu lote no hub.");
			}
			Game.save();
		}
	}

	private static void set(ServerLevel lv, int x, int y, int z, Block b) {
		lv.setBlock(new BlockPos(x, y, z), b.defaultBlockState(), 2);   // flag 2 = so avisa clientes (rapido)
	}

	private static void setIfAir(ServerLevel lv, int x, int y, int z, Block b) {
		BlockPos p = new BlockPos(x, y, z);
		if (lv.getBlockState(p).isAir()) lv.setBlock(p, b.defaultBlockState(), 2);
	}

	private static void buildColumn(ServerLevel lv, int c) {
		Data.Save sv = Game.save;
		int x = sv.mapX + c;
		int y0 = sv.mapY - 1;                      // superficie do chao
		Biome b = Biome.values()[Math.min(BIOMES - 1, c / BL)];
		for (int dz = -HALF; dz <= HALF; dz++) {
			int z = sv.mapZ + dz;
			for (int dy = 2; dy <= 8; dy++) {      // preenche buracos embaixo
				BlockPos sp = new BlockPos(x, y0 - dy, z);
				var st = lv.getBlockState(sp);
				if (st.isAir() || !st.getFluidState().isEmpty()) lv.setBlock(sp, Blocks.STONE.defaultBlockState(), 2);
			}
			set(lv, x, y0 - 1, z, Blocks.STONE);
			set(lv, x, y0, z, RNG.nextInt(7) == 0 ? b.alt : b.floor);
			for (int dy = 1; dy <= 10; dy++) {     // limpa o ar acima
				BlockPos ap = new BlockPos(x, y0 + dy, z);
				if (!lv.getBlockState(ap).isAir()) lv.setBlock(ap, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		if (c % BL == 0) buildGate(lv, x, sv.mapY, sv.mapZ, b);
	}

	/** Arco de entrada de cada bioma: dois pilares nas bordas e uma viga em cima. */
	private static void buildGate(ServerLevel lv, int x, int y, int z0, Biome b) {
		for (int dy = 0; dy <= 6; dy++) {
			set(lv, x, y + dy, z0 - HALF, b.gate);
			set(lv, x, y + dy, z0 + HALF, b.gate);
		}
		for (int dz = -HALF; dz <= HALF; dz++) set(lv, x, y + 6, z0 + dz, b.gate);
	}

	/** Decoracao do bioma. So roda quando as colunas vizinhas (x-2..x+2) ja existem. */
	private static void decor(ServerLevel lv, int c) {
		if (c < 0 || c % BL < 6 || RNG.nextInt(30) != 0) return;
		int bi = c / BL;
		if (bi >= BIOMES) return;
		Data.Save sv = Game.save;
		int x = sv.mapX + c;
		int z = sv.mapZ + RNG.nextInt(35) - 17;
		int y = sv.mapY;                            // primeiro bloco acima do chao
		switch (Biome.values()[bi]) {
			case FLORESTA -> tree(lv, x, y, z, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4 + RNG.nextInt(2));
			case LAGO -> {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (dx * dx + dz * dz <= 5) set(lv, x + dx, y - 1, z + dz, Blocks.WATER);
					}
				}
			}
			case DESERTO -> {
				int h = 1 + RNG.nextInt(3);
				for (int dy = 0; dy < h; dy++) set(lv, x, y + dy, z, Blocks.CACTUS);
			}
			case SELVA -> tree(lv, x, y, z, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, 6 + RNG.nextInt(3));
			case NEVE -> tree(lv, x, y, z, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, 5 + RNG.nextInt(3));
			case VULCAO -> {
				int h = 1 + RNG.nextInt(3);
				for (int dy = 0; dy < h; dy++) set(lv, x, y + dy, z, Blocks.MAGMA_BLOCK);
			}
			case ABISMO -> {
				int h = 2 + RNG.nextInt(3);
				for (int dy = 0; dy < h; dy++) set(lv, x, y + dy, z, Blocks.PRISMARINE_BRICKS);
				set(lv, x, y + h, z, Blocks.SEA_LANTERN);
			}
			case PRE_HISTORICO -> tree(lv, x, y, z, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, 7 + RNG.nextInt(3));
			case COSMICO -> {
				int h = 3 + RNG.nextInt(4);
				for (int dy = 0; dy < h; dy++) set(lv, x, y + dy, z, Blocks.PURPUR_PILLAR);
				set(lv, x, y + h, z, Blocks.END_ROD);
			}
		}
	}

	private static void tree(ServerLevel lv, int x, int y, int z, Block log, Block leaves, int h) {
		for (int dy = 0; dy < h; dy++) set(lv, x, y + dy, z, log);
		var leaf = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);   // nao apodrece
		for (int dy = h - 2; dy <= h; dy++) {
			int r = dy == h ? 1 : 2;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx == 0 && dz == 0 && dy < h) continue;
					if (Math.abs(dx) == r && Math.abs(dz) == r && dy < h) continue;
					BlockPos p = new BlockPos(x + dx, y + dy, z + dz);
					if (lv.getBlockState(p).isAir()) lv.setBlock(p, leaf, 2);
				}
			}
		}
	}
}
