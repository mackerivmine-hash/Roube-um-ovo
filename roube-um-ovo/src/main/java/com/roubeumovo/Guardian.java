package com.roubeumovo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Guardiao "virtual": nao e uma entidade, so uma posicao simulada que persegue o jogador
 * (visivel por particulas). Se encostar, voce perde o ovo e e jogado de volta ao portao.
 * A perseguicao termina ao entregar o ovo, ao cruzar o portao (zona segura) ou depois de 150s.
 */
public final class Guardian {
	public static final class Chase {
		public final int biome, rarity;
		public final BlockPos nest;
		public double gx, gy, gz, dist = 99;
		public int ticks;
		public long endMs;

		Chase(int biome, int rarity, BlockPos nest) {
			this.biome = biome;
			this.rarity = rarity;
			this.nest = nest;
		}
	}

	public static final Map<UUID, Chase> chases = new HashMap<>();

	private Guardian() {}

	public static void start(ServerPlayer p, int biome, int rarity, BlockPos nest) {
		Chase c = new Chase(biome, rarity, nest);
		c.gx = p.getX() + 7;        // o guardiao vem do fundo do mapa (leste); o portao fica a oeste
		c.gz = p.getZ();
		c.gy = p.getY();
		c.endMs = System.currentTimeMillis() + 150_000L;
		chases.put(p.getUUID(), c);
	}

	public static void tick(MinecraftServer server) {
		if (chases.isEmpty()) return;
		ServerLevel ow = server.overworld();
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, Chase>> it = chases.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Chase> e = it.next();
			Chase c = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !Game.carrying.containsKey(e.getKey())) { it.remove(); continue; }
			if (p.getX() < Game.save.mapX) {
				Game.say(p, "Voce passou do portao: zona segura! O guardiao desistiu.");
				it.remove();
				continue;
			}
			if (now > c.endMs) {
				Game.say(p, "O guardiao cansou e desistiu de te perseguir.");
				it.remove();
				continue;
			}
			double dx = p.getX() - c.gx, dz = p.getZ() - c.gz;
			double dist = Math.sqrt(dx * dx + dz * dz);
			c.dist = dist;
			if (dist < 1.8) {
				it.remove();
				Game.guardianCatch(p, c);
				continue;
			}
			double step = Biome.guardianSpeed(c.biome) / 20.0;
			c.gx += dx / dist * step;
			c.gz += dz / dist * step;
			c.gy += (p.getY() - c.gy) * 0.2;
			c.ticks++;
			if (c.ticks % 2 == 0) {
				ow.sendParticles(ParticleTypes.CLOUD, c.gx, c.gy + 0.8, c.gz, 6, 0.4, 0.5, 0.4, 0.02);
				ow.sendParticles(ParticleTypes.ANGRY_VILLAGER, c.gx, c.gy + 1.8, c.gz, 1, 0.2, 0.2, 0.2, 0);
			}
			if (c.ticks % 40 == 0) {
				ow.playSound(null, BlockPos.containing(c.gx, c.gy, c.gz), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 1.0f);
			}
			if (c.ticks % 10 == 0) Game.actionBar(p);
		}
	}
}
