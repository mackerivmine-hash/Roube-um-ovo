package com.roubeumovo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Estruturas de dados salvas em JSON (Gson). */
public final class Data {
	private Data() {}

	public static class Slot {
		public int state;       // 0 = vazio, 1 = chocando, 2 = chocado (bicho)
		public int rarity;
		public int biome;
		public long hatchAtMs;
		public String petName = "";
	}

	public static class PlayerData {
		public String name = "?";
		public double coins = 100;
		public int speedLevel = 0;
		public boolean hasBase;
		public int baseX, baseY, baseZ;
		public int plot = -1;   // indice do lote no hub (-1 = base livre)
		public Slot[] slots = newSlots();
		public long lockedUntilMs;
		public long lastSeenMs;

		static Slot[] newSlots() {
			Slot[] s = new Slot[Game.SLOTS];
			for (int i = 0; i < s.length; i++) s[i] = new Slot();
			return s;
		}

		public double incomePerSec() {
			double sum = 0;
			for (Slot s : slots) {
				if (s != null && s.state == 2) sum += Rarity.values()[s.rarity].income(s.biome);
			}
			return sum;
		}
	}

	public static class Nest {
		public int x, y, z;     // y = bloco de feno; o ovo fica em y + 1
		public int rarity;
		public int biome = -1;  // -1 = ninho selvagem fora do mapa
		public long expiresMs;
	}

	public static class Save {
		public Map<String, PlayerData> players = new HashMap<>();
		public List<Nest> nests = new ArrayList<>();
		// Mapa de biomas
		public boolean hasMap, mapBuilt;
		public int mapX, mapY, mapZ, mapProgress;
	}

	/** Ovo que o jogador esta carregando (so um por vez, como no jogo original). Nao e salvo. */
	public static class Carry {
		public final int rarity;
		public final int biome;
		public final UUID from;     // dono original (null = veio de um ninho selvagem)
		public final int fromSlot;

		public Carry(int rarity, int biome, UUID from, int fromSlot) {
			this.rarity = rarity;
			this.biome = biome;
			this.from = from;
			this.fromSlot = fromSlot;
		}
	}
}
