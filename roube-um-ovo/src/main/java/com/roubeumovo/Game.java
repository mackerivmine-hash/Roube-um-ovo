package com.roubeumovo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

/** Toda a logica do "Roube um Ovo". Tudo roda na thread principal do servidor. */
public final class Game {
	public static final int SLOTS = 5;
	public static final int R = 6;                 // raio da base (13x13)
	public static final int MAX_SPEED = 8;
	private static final long NEST_LIFETIME_MS = 10 * 60 * 1000L;
	private static final Random RNG = new Random();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Locale PT = Locale.of("pt", "BR");

	public static Data.Save save = new Data.Save();
	public static final Map<UUID, Data.Carry> carrying = new HashMap<>();
	private static final Map<UUID, Integer> lastBiome = new HashMap<>();
	private static MinecraftServer server;
	private static Path file;
	private static long tick;

	private Game() {}

	// ------------------------------------------------------------------ persistencia

	public static void onStart(MinecraftServer s) {
		server = s;
		carrying.clear();
		lastBiome.clear();
		Guardian.chases.clear();
		save = new Data.Save();
		file = s.getWorldPath(LevelResource.ROOT).resolve("roubeumovo.json");
		try {
			if (Files.exists(file)) {
				Data.Save loaded = GSON.fromJson(Files.readString(file), Data.Save.class);
				if (loaded != null) {
					save = loaded;
					if (save.players == null) save.players = new HashMap<>();
					if (save.nests == null) save.nests = new ArrayList<>();
				}
			}
		} catch (Exception e) {
			RoubeUmOvoMod.LOGGER.error("Falha ao carregar dados", e);
		}
	}

	public static void save() {
		if (file == null) return;
		try {
			Path tmp = file.resolveSibling("roubeumovo.json.tmp");
			Files.writeString(tmp, GSON.toJson(save));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			RoubeUmOvoMod.LOGGER.error("Falha ao salvar dados", e);
		}
	}

	// ------------------------------------------------------------------ helpers

	public static Data.PlayerData data(ServerPlayer p) {
		Data.PlayerData d = save.players.computeIfAbsent(p.getUUID().toString(), k -> new Data.PlayerData());
		d.name = p.getName().getString();
		return d;
	}

	private static String fmt(double v) {
		return String.format(PT, "%,.0f", v);
	}

	private static MutableComponent rar(Rarity r) {
		return Component.literal(r.label).withStyle(r.color);
	}

	public static void say(ServerPlayer p, Component body) {
		p.sendSystemMessage(Component.literal("[Ovo] ").withStyle(ChatFormatting.GOLD).append(body));
	}

	public static void say(ServerPlayer p, String s) {
		say(p, Component.literal(s).withStyle(ChatFormatting.YELLOW));
	}

	private static void sound(ServerLevel lv, BlockPos pos, SoundEvent ev) {
		lv.playSound(null, pos, ev, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	public static BlockPos slotPos(Data.PlayerData d, int i) {
		return new BlockPos(d.baseX + (i - 2) * 2, d.baseY, d.baseZ - 3);
	}

	private static boolean isOverworld(ServerPlayer p) {
		return p.level().dimension() == Level.OVERWORLD;
	}

	private static void teleport(ServerPlayer p, double x, double y, double z) {
		String cmd = String.format(Locale.ROOT, "tp %s %.1f %.1f %.1f", p.getUUID(), x, y, z);
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
	}

	// ------------------------------------------------------------------ protecao

	public static boolean inAnyBase(BlockPos pos) {
		for (Data.PlayerData d : save.players.values()) {
			if (!d.hasBase) continue;
			if (Math.abs(pos.getX() - d.baseX) <= R && Math.abs(pos.getZ() - d.baseZ) <= R
				&& pos.getY() >= d.baseY - 2 && pos.getY() <= d.baseY + 6) return true;
		}
		return false;
	}

	public static Data.Nest findNest(BlockPos pos) {
		for (Data.Nest n : save.nests) {
			if (n.x == pos.getX() && n.z == pos.getZ() && (n.y == pos.getY() || n.y + 1 == pos.getY())) return n;
		}
		return null;
	}

	public static boolean isProtected(BlockPos pos) {
		return inAnyBase(pos) || findNest(pos) != null;
	}

	// ------------------------------------------------------------------ slots / chocar

	private static final class Hit {
		final UUID id;
		final Data.PlayerData data;
		final int slot;

		Hit(UUID id, Data.PlayerData data, int slot) {
			this.id = id;
			this.data = data;
			this.slot = slot;
		}
	}

	private static Hit findSlot(BlockPos pos) {
		for (Map.Entry<String, Data.PlayerData> e : save.players.entrySet()) {
			Data.PlayerData d = e.getValue();
			if (!d.hasBase) continue;
			for (int i = 0; i < SLOTS; i++) {
				BlockPos sp = slotPos(d, i);
				if (sp.equals(pos) || sp.above().equals(pos)) {
					return new Hit(UUID.fromString(e.getKey()), d, i);
				}
			}
		}
		return null;
	}

	private static void syncBlock(ServerLevel lv, Data.PlayerData d, int i) {
		BlockPos pos = slotPos(d, i).above();
		if (!lv.hasChunkAt(pos)) return;
		Data.Slot s = d.slots[i];
		Block want = s.state == 0 ? Blocks.AIR : s.state == 1 ? Blocks.DRAGON_EGG : Rarity.values()[s.rarity].petBlock;
		if (!lv.getBlockState(pos).is(want)) lv.setBlockAndUpdate(pos, want.defaultBlockState());
	}

	private static void refreshSlot(ServerLevel lv, Data.PlayerData d, int i, UUID ownerId) {
		Data.Slot s = d.slots[i];
		if (s.state == 1 && System.currentTimeMillis() >= s.hatchAtMs) {
			Rarity r = Rarity.values()[s.rarity];
			s.state = 2;
			s.petName = r.randomName(RNG);
			syncBlock(lv, d, i);
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			if (owner != null) {
				say(owner, Component.literal("Seu ovo chocou! Nasceu ").withStyle(ChatFormatting.YELLOW)
					.append(Component.literal(s.petName).withStyle(r.color))
					.append(Component.literal(" (" + r.label + ", +" + String.format(PT, "%,.1f", r.income(s.biome)) + " moedas/s)").withStyle(ChatFormatting.YELLOW)));
				sound(lv, slotPos(d, i), SoundEvents.PLAYER_LEVELUP);
			}
		}
	}

	// ------------------------------------------------------------------ ninhos

	private static void placeNest(ServerLevel lv, BlockPos pos, int rarity, int biome, long expiresMs) {
		lv.setBlockAndUpdate(pos, Blocks.HAY_BLOCK.defaultBlockState());
		lv.setBlockAndUpdate(pos.above(), Blocks.DRAGON_EGG.defaultBlockState());
		Data.Nest n = new Data.Nest();
		n.x = pos.getX();
		n.y = pos.getY();
		n.z = pos.getZ();
		n.rarity = rarity;
		n.biome = biome;
		n.expiresMs = expiresMs;
		save.nests.add(n);
	}

	private static void removeNest(ServerLevel lv, Data.Nest n) {
		BlockPos pos = new BlockPos(n.x, n.y, n.z);
		if (lv.hasChunkAt(pos)) {
			lv.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
			lv.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}
		save.nests.remove(n);
	}

	private static void spawnNests() {
		ServerLevel ow = server.overworld();
		if (save.nests.size() >= 80) return;
		long now = System.currentTimeMillis();
		Data.Save sv = save;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!isOverworld(p)) continue;
			BlockPos pp = p.blockPosition();
			if (sv.hasMap) {
				spawnMapNests(ow, p, pp, now);
				continue;
			}
			// Sem mapa: ninhos selvagens aleatorios ao redor do jogador (modo antigo)
			int near = 0;
			for (Data.Nest n : sv.nests) {
				if (Math.abs(n.x - pp.getX()) <= 96 && Math.abs(n.z - pp.getZ()) <= 96) near++;
			}
			if (near >= 3) continue;
			double ang = RNG.nextDouble() * Math.PI * 2;
			int dist = 30 + RNG.nextInt(35);
			BlockPos probe = new BlockPos(pp.getX() + (int) (Math.cos(ang) * dist), 64, pp.getZ() + (int) (Math.sin(ang) * dist));
			if (!ow.hasChunkAt(probe)) continue;
			BlockPos top = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
			if (!ow.getFluidState(top.below()).isEmpty() || isProtected(top)) continue;
			placeNest(ow, top, Rarity.roll(RNG), -1, now + NEST_LIFETIME_MS);
		}
	}

	/** Ninhos do mapa: nascem no bioma do jogador (ou nos vizinhos), com raridade conforme o bioma. */
	private static void spawnMapNests(ServerLevel ow, ServerPlayer p, BlockPos pp, long now) {
		Data.Save sv = save;
		int cur = WorldMap.biomeAt(p.getX(), p.getZ());
		if (cur < 0) return;
		int near = 0;
		for (Data.Nest n : sv.nests) {
			if (Math.abs(n.x - pp.getX()) <= 80 && Math.abs(n.z - pp.getZ()) <= 40) near++;
		}
		if (near >= 5) return;
		for (int attempt = 0; attempt < 2; attempt++) {
			int bi = Math.max(0, Math.min(WorldMap.BIOMES - 1, cur + RNG.nextInt(3) - 1));
			int minX = sv.mapX + bi * WorldMap.BL + 4;
			int maxX = sv.mapX + (bi + 1) * WorldMap.BL - 5;
			int x = Math.max(minX, Math.min(maxX, pp.getX() + RNG.nextInt(91) - 45));
			int z = sv.mapZ + RNG.nextInt(35) - 17;
			if (x - sv.mapX >= sv.mapProgress - 3) continue;      // coluna ainda nao construida
			BlockPos pos = new BlockPos(x, sv.mapY, z);
			if (!ow.hasChunkAt(pos)) continue;
			if (!ow.getBlockState(pos).isAir() || !ow.getBlockState(pos.above()).isAir()) continue;
			if (!ow.getFluidState(pos.below()).isEmpty() || findNest(pos) != null) continue;
			placeNest(ow, pos, Rarity.roll(RNG, bi), bi, now + NEST_LIFETIME_MS);
		}
	}

	// ------------------------------------------------------------------ interacao principal

	/** Clique direito num bloco. Retorna true se o mod tratou o clique (cancela o vanilla). */
	public static boolean onUse(ServerPlayer p, BlockPos pos) {
		if (!isOverworld(p)) return false;
		ServerLevel ow = server.overworld();
		UUID me = p.getUUID();
		long now = System.currentTimeMillis();

		Data.Nest nest = findNest(pos);
		if (nest != null) {
			if (carrying.containsKey(me)) {
				say(p, "Voce ja esta carregando um ovo! Leve-o ate a sua base.");
				return true;
			}
			Rarity r = Rarity.values()[nest.rarity];
			BlockPos nestPos = new BlockPos(nest.x, nest.y, nest.z);
			removeNest(ow, nest);
			carrying.put(me, new Data.Carry(nest.rarity, nest.biome, null, 0));
			say(p, Component.literal("Voce pegou um ovo ").withStyle(ChatFormatting.YELLOW).append(rar(r))
				.append(Component.literal("! Leve-o ate um pedestal vazio da sua base.").withStyle(ChatFormatting.YELLOW)));
			sound(ow, pos, SoundEvents.EXPERIENCE_ORB_PICKUP);
			if (nest.biome >= 0) {
				Biome b = Biome.values()[nest.biome];
				Guardian.start(p, nest.biome, nest.rarity, nestPos);
				say(p, Component.literal("O guardiao (" + b.guardian + ") acordou! Corra para OESTE ate o portao!").withStyle(ChatFormatting.RED));
			}
			return true;
		}

		Hit h = findSlot(pos);
		if (h == null) return false;
		refreshSlot(ow, h.data, h.slot, h.id);
		Data.Slot s = h.data.slots[h.slot];
		Data.Carry c = carrying.get(me);

		if (h.id.equals(me)) {
			if (s.state == 0) {
				if (c == null) {
					say(p, "Pedestal vazio. Roube um ovo de um ninho (veja /ovo mapa)!");
				} else {
					Rarity r = Rarity.values()[c.rarity];
					s.state = 1;
					s.rarity = c.rarity;
					s.biome = Math.max(0, c.biome);
					s.hatchAtMs = now + r.hatchSeconds * 1000L;
					s.petName = "";
					carrying.remove(me);
					syncBlock(ow, h.data, h.slot);
					say(p, Component.literal("Ovo ").withStyle(ChatFormatting.YELLOW).append(rar(r))
						.append(Component.literal(" guardado! Choca em " + r.hatchSeconds + "s. Cuidado com ladroes!").withStyle(ChatFormatting.YELLOW)));
					sound(ow, pos, SoundEvents.TURTLE_EGG_CRACK);
				}
			} else if (s.state == 1) {
				say(p, "Chocando... faltam " + Math.max(0, (s.hatchAtMs - now) / 1000) + "s.");
			} else {
				Rarity r = Rarity.values()[s.rarity];
				say(p, Component.literal(s.petName).withStyle(r.color)
					.append(Component.literal(" rende " + String.format(PT, "%,.1f", r.income(s.biome)) + " moedas/s.").withStyle(ChatFormatting.YELLOW)));
			}
			return true;
		}

		// pedestal de outro jogador
		if (s.state != 1) {
			say(p, s.state == 2 ? "Esse bicho ja nasceu. So da pra roubar ovos que ainda nao chocaram!" : "Pedestal vazio.");
			return true;
		}
		if (c != null) {
			say(p, "Voce ja esta carregando um ovo! Deixe-o na sua base primeiro.");
			return true;
		}
		if (now < h.data.lockedUntilMs) {
			say(p, "Base trancada por mais " + ((h.data.lockedUntilMs - now) / 1000 + 1) + "s!");
			return true;
		}
		Rarity r = Rarity.values()[s.rarity];
		carrying.put(me, new Data.Carry(s.rarity, s.biome, h.id, h.slot));
		s.state = 0;
		s.petName = "";
		syncBlock(ow, h.data, h.slot);
		h.data.lockedUntilMs = now + 10_000L;   // 10s de folga para o dono reagir
		say(p, Component.literal("VOCE ROUBOU um ovo ").withStyle(ChatFormatting.GREEN).append(rar(r))
			.append(Component.literal(" de " + h.data.name + "! Fuja pra sua base!").withStyle(ChatFormatting.GREEN)));
		sound(ow, pos, SoundEvents.ENDERMAN_TELEPORT);
		ServerPlayer owner = server.getPlayerList().getPlayer(h.id);
		if (owner != null) {
			say(owner, Component.literal(p.getName().getString() + " roubou seu ovo ").withStyle(ChatFormatting.RED)
				.append(rar(r)).append(Component.literal("! Mate-o para recuperar!").withStyle(ChatFormatting.RED)));
		}
		return true;
	}

	/** O guardiao te alcancou: perde o ovo (volta pro ninho) e e jogado de volta ao portao. */
	public static void guardianCatch(ServerPlayer p, Guardian.Chase ch) {
		Data.Carry c = carrying.remove(p.getUUID());
		ServerLevel ow = server.overworld();
		Biome b = Biome.values()[ch.biome];
		if (c != null && ow.hasChunkAt(ch.nest) && ow.getBlockState(ch.nest).isAir() && ow.getBlockState(ch.nest.above()).isAir()) {
			placeNest(ow, ch.nest, c.rarity, c.biome, System.currentTimeMillis() + NEST_LIFETIME_MS);
		}
		say(p, Component.literal("O guardiao (" + b.guardian + ") te pegou! Voce perdeu o ovo e foi arremessado pro portao.").withStyle(ChatFormatting.RED));
		sound(ow, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT);
		teleport(p, save.mapX - 3 + 0.5, save.mapY, save.mapZ + 0.5);
	}

	/** Chamado na morte ou saida do jogador: o ovo volta para o dono (ou vira ninho). */
	public static void dropCarry(ServerPlayer p) {
		Guardian.chases.remove(p.getUUID());
		Data.Carry c = carrying.remove(p.getUUID());
		if (c == null) return;
		ServerLevel ow = server.overworld();
		Rarity r = Rarity.values()[c.rarity];
		if (c.from != null) {
			Data.PlayerData od = save.players.get(c.from.toString());
			if (od != null && od.hasBase) {
				for (int k = 0; k < SLOTS; k++) {
					int i = (c.fromSlot + k) % SLOTS;
					Data.Slot s = od.slots[i];
					if (s.state == 0) {
						s.state = 1;
						s.rarity = c.rarity;
						s.biome = Math.max(0, c.biome);
						s.hatchAtMs = System.currentTimeMillis() + r.hatchSeconds * 1000L;
						s.petName = "";
						syncBlock(ow, od, i);
						ServerPlayer owner = server.getPlayerList().getPlayer(c.from);
						if (owner != null) say(owner, "Seu ovo " + r.label + " voltou para a sua base!");
						return;
					}
				}
			}
		}
		if (isOverworld(p)) {
			BlockPos top;
			if (save.hasMap && WorldMap.biomeAt(p.getX(), p.getZ()) >= 0) {
				top = new BlockPos(p.blockPosition().getX(), save.mapY, p.blockPosition().getZ());
				if (!ow.getBlockState(top).isAir() || !ow.getBlockState(top.above()).isAir()) return;
			} else {
				top = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition());
			}
			if (!isProtected(top) && ow.getFluidState(top.below()).isEmpty()) {
				placeNest(ow, top, c.rarity, c.biome, System.currentTimeMillis() + NEST_LIFETIME_MS);
			}
		}
	}

	// ------------------------------------------------------------------ join / leave

	public static void onJoin(ServerPlayer p) {
		Data.PlayerData d = data(p);
		long now = System.currentTimeMillis();
		if (d.hasBase) {
			ServerLevel ow = server.overworld();
			for (int i = 0; i < SLOTS; i++) refreshSlot(ow, d, i, p.getUUID());
			if (d.lastSeenMs > 0) {
				long secs = Math.min((now - d.lastSeenMs) / 1000, 7200);
				double earned = d.incomePerSec() * secs;
				if (earned >= 1) {
					d.coins += earned;
					say(p, "Seus bichos renderam " + fmt(earned) + " moedas enquanto voce estava fora.");
				}
			}
		} else {
			say(p, "Bem-vindo ao Roube um Ovo! Digite /ovo ajuda para comecar.");
		}
		d.lastSeenMs = now;
	}

	public static void onLeave(ServerPlayer p) {
		dropCarry(p);
		lastBiome.remove(p.getUUID());
		data(p).lastSeenMs = System.currentTimeMillis();
		save();
	}

	// ------------------------------------------------------------------ tick

	public static void onTick(MinecraftServer s) {
		if (server == null) return;
		tick++;
		Guardian.tick(server);
		WorldMap.tick(server);
		if (tick % 20 == 0) everySecond();
		if (tick % (save.hasMap ? 200 : 600) == 0) spawnNests();
		if (tick % 6000 == 0) save();
	}

	private static void everySecond() {
		ServerLevel ow = server.overworld();
		long now = System.currentTimeMillis();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Data.PlayerData d = data(p);
			if (d.hasBase) {
				for (int i = 0; i < SLOTS; i++) {
					refreshSlot(ow, d, i, p.getUUID());
					syncBlock(ow, d, i);
				}
				d.coins += d.incomePerSec();
			}
			applyEffects(p, d);
			announceBiome(p);
			actionBar(p);
		}
		for (Data.Nest n : new ArrayList<>(save.nests)) {
			BlockPos pos = new BlockPos(n.x, n.y, n.z);
			if (!ow.hasChunkAt(pos)) continue;
			if (now > n.expiresMs) {
				removeNest(ow, n);
				continue;
			}
			Rarity r = Rarity.values()[n.rarity];
			ow.sendParticles(r.particle, n.x + 0.5, n.y + 1.8, n.z + 0.5, 3, 0.25, 0.3, 0.25, 0.01);
		}
	}

	private static void announceBiome(ServerPlayer p) {
		int b = isOverworld(p) ? WorldMap.biomeAt(p.getX(), p.getZ()) : -1;
		Integer last = lastBiome.put(p.getUUID(), b);
		if (b >= 0 && (last == null || last != b)) {
			Biome bio = Biome.values()[b];
			say(p, Component.literal("Entrando: " + bio.label).withStyle(bio.color)
				.append(Component.literal(" | Guardiao: " + bio.guardian + " | Velocidade recomendada: nivel " + b).withStyle(ChatFormatting.GRAY)));
		}
	}

	private static void applyEffects(ServerPlayer p, Data.PlayerData d) {
		if (d.speedLevel > 0) {
			p.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, d.speedLevel - 1, true, false, true));
		}
		Data.Carry c = carrying.get(p.getUUID());
		if (c != null && c.rarity > 0) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, c.rarity - 1, true, false, true));
		}
	}

	public static void actionBar(ServerPlayer p) {
		Data.PlayerData d = data(p);
		MutableComponent line = Component.literal("Moedas: " + fmt(d.coins)).withStyle(ChatFormatting.GOLD)
			.append(Component.literal("  (+" + String.format(PT, "%,.1f", d.incomePerSec()) + "/s)").withStyle(ChatFormatting.GREEN))
			.append(Component.literal("  Vel " + d.speedLevel).withStyle(ChatFormatting.AQUA));
		Data.Carry c = carrying.get(p.getUUID());
		if (c != null) {
			line.append(Component.literal("  Ovo: ").withStyle(ChatFormatting.GRAY)).append(rar(Rarity.values()[c.rarity]));
		}
		int cur = isOverworld(p) ? WorldMap.biomeAt(p.getX(), p.getZ()) : -1;
		if (cur >= 0) line.append(Component.literal("  " + Biome.values()[cur].label).withStyle(Biome.values()[cur].color));
		Guardian.Chase ch = Guardian.chases.get(p.getUUID());
		if (ch != null) line.append(Component.literal("  GUARDIAO A " + (int) ch.dist + "m!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		p.displayClientMessage(line, true);
	}

	// ------------------------------------------------------------------ comandos

	public static void cmdHelp(ServerPlayer p) {
		say(p, "=== Roube um Ovo ===");
		p.sendSystemMessage(Component.literal(
			"1) /ovo mapa gerar - (uma vez) constroi o mapa de 9 biomas para o LESTE de onde voce esta\n"
			+ "2) /ovo base - pega seu lote no hub (oeste do portao)\n"
			+ "3) Corra pelos biomas, clique direito num ovo de ninho. O GUARDIAO do bioma acorda e te persegue!\n"
			+ "4) Fuja para OESTE ate o portao (zona segura), leve o ovo ao pedestal da sua base: ele choca e vira um bicho que rende moedas\n"
			+ "5) Biomas mais fundos = ovos mais raros e pets bem mais ricos, mas guardioes mais rapidos\n"
			+ "6) /ovo upgrade velocidade | /ovo mapa | /ovo trancar (100 moedas) | /ovo casa | /ovo status | /ovo top\n"
			+ "7) Clique direito no ovo de OUTRO jogador para roubar. Se morrer carregando, o ovo volta ao dono."
		).withStyle(ChatFormatting.GRAY));
	}

	public static void cmdMapGenerate(ServerPlayer p) {
		if (!isOverworld(p)) { say(p, "O mapa so pode ser criado no Overworld."); return; }
		if (save.hasMap) { say(p, "O mapa ja existe. Use /ovo mapa."); return; }
		BlockPos o = p.blockPosition();
		save.hasMap = true;
		save.mapBuilt = false;
		save.mapProgress = 0;
		save.mapX = o.getX();
		save.mapY = o.getY();
		save.mapZ = o.getZ();
		say(p, "Construindo o mapa para o LESTE (+X) a partir daqui: 9 biomas de " + WorldMap.BL + " blocos. Leva uns 40 segundos; "
			+ "o hub das bases fica a OESTE deste ponto (este e o portao).");
		save();
	}

	public static void cmdMap(ServerPlayer p) {
		Data.Save sv = save;
		if (!sv.hasMap) {
			say(p, "O mapa ainda nao existe. Fique onde sera o portao, olhando para o LESTE, e use /ovo mapa gerar.");
			return;
		}
		boolean ow = isOverworld(p);
		int cur = ow ? WorldMap.biomeAt(p.getX(), p.getZ()) : -1;
		int[] nests = new int[WorldMap.BIOMES];
		for (Data.Nest n : sv.nests) if (n.biome >= 0 && n.biome < nests.length) nests[n.biome]++;
		int pct = (int) (100.0 * sv.mapProgress / WorldMap.totalColumns());
		say(p, Component.literal("=== MAPA DO OVO ===").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(sv.mapBuilt ? "" : "  (construindo: " + pct + "%)").withStyle(ChatFormatting.GRAY)));
		p.sendSystemMessage(Component.literal("   HUB / Bases  <- portao (oeste)").withStyle(ChatFormatting.GRAY));
		for (int i = 0; i < WorldMap.BIOMES; i++) {
			Biome b = Biome.values()[i];
			MutableComponent line = Component.literal((i == cur ? " >> " : "    ") + (i + 1) + ". " + b.label).withStyle(b.color);
			if (i == cur) line.withStyle(ChatFormatting.BOLD);
			line.append(Component.literal("  vel " + i + "+  " + b.guardian + "  ninhos: " + nests[i] + (i == cur ? "   <-- VOCE" : "")).withStyle(ChatFormatting.GRAY));
			p.sendSystemMessage(line);
		}
		double dx = p.getX() - sv.mapX;
		int width = 36;
		StringBuilder bar = new StringBuilder("[");
		int pos = (!ow || dx < 0 || dx >= WorldMap.totalColumns()) ? -1 : (int) (dx / WorldMap.totalColumns() * width);
		for (int k = 0; k < width; k++) bar.append(k == pos ? 'O' : (k % (width / WorldMap.BIOMES) == 0 ? '|' : '-'));
		bar.append("]");
		p.sendSystemMessage(Component.literal("   Hub " + bar + " Cosmico" + (pos < 0 ? "   (voce esta fora da faixa)" : "")).withStyle(ChatFormatting.DARK_GRAY));
	}

	public static void cmdBase(ServerPlayer p) {
		if (!isOverworld(p)) { say(p, "So da pra criar a base no Overworld."); return; }
		Data.PlayerData d = data(p);
		if (d.hasBase) { say(p, "Voce ja tem base! Use /ovo casa."); return; }
		BlockPos o;
		if (save.hasMap) {
			int k = 0;
			for (Data.PlayerData od : save.players.values()) k = Math.max(k, od.plot + 1);
			d.plot = k;
			o = WorldMap.plotOrigin(k);
		} else {
			o = p.blockPosition();
			for (Data.PlayerData od : save.players.values()) {
				if (od.hasBase && Math.abs(od.baseX - o.getX()) <= 2 * R + 8 && Math.abs(od.baseZ - o.getZ()) <= 2 * R + 8) {
					say(p, "Tem outra base muito perto. Va mais longe!");
					return;
				}
			}
		}
		ServerLevel lv = server.overworld();
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				boolean edge = Math.abs(dx) == R || Math.abs(dz) == R;
				lv.setBlockAndUpdate(new BlockPos(o.getX() + dx, o.getY() - 1, o.getZ() + dz),
					(edge ? Blocks.POLISHED_DEEPSLATE : Blocks.STONE_BRICKS).defaultBlockState());
				for (int dy = 0; dy <= 5; dy++) {
					BlockPos bp = new BlockPos(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
					if (!lv.getBlockState(bp).isAir()) lv.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
				}
			}
		}
		d.hasBase = true;
		d.baseX = o.getX();
		d.baseY = o.getY();
		d.baseZ = o.getZ();
		d.slots = new Data.PlayerData().slots;
		for (int sx : new int[] {-(R - 1), R - 1}) {
			for (int sz : new int[] {-(R - 1), R - 1}) {
				lv.setBlockAndUpdate(new BlockPos(o.getX() + sx, o.getY(), o.getZ() + sz), Blocks.LANTERN.defaultBlockState());
			}
		}
		for (int i = 0; i < SLOTS; i++) lv.setBlockAndUpdate(slotPos(d, i), Blocks.QUARTZ_PILLAR.defaultBlockState());
		say(p, "Base criada! Os 5 pedestais de quartzo guardam seus ovos. Veja /ovo mapa para achar ninhos.");
		if (save.hasMap) teleport(p, d.baseX + 0.5, d.baseY, d.baseZ + 3.5);
		save();
	}

	public static void cmdHome(ServerPlayer p) {
		Data.PlayerData d = data(p);
		if (!d.hasBase) { say(p, "Voce ainda nao tem base. Use /ovo base."); return; }
		if (carrying.containsKey(p.getUUID())) { say(p, "Nada de teleporte com ovo na mao! Corra ate a base."); return; }
		teleport(p, d.baseX + 0.5, d.baseY, d.baseZ + 3.5);
	}

	public static void cmdNests(ServerPlayer p) {
		if (!isOverworld(p)) { say(p, "Os ninhos ficam no Overworld."); return; }
		Data.Nest best = null;
		double bd = Double.MAX_VALUE;
		for (Data.Nest n : save.nests) {
			double dx = n.x + 0.5 - p.getX(), dz = n.z + 0.5 - p.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist < bd) { bd = dist; best = n; }
		}
		if (best == null) { say(p, "Nenhum ninho por perto agora. Eles aparecem perto dos jogadores a cada poucos segundos."); return; }
		double ang = Math.toDegrees(Math.atan2(best.z + 0.5 - p.getZ(), best.x + 0.5 - p.getX()));
		String[] dirs = {"Leste", "Sudeste", "Sul", "Sudoeste", "Oeste", "Noroeste", "Norte", "Nordeste"};
		String dir = dirs[(int) Math.round(((ang + 360) % 360) / 45.0) % 8];
		Rarity r = Rarity.values()[best.rarity];
		say(p, Component.literal("Ninho ").withStyle(ChatFormatting.YELLOW).append(rar(r))
			.append(Component.literal(" a " + (int) bd + " blocos para o " + dir + ".").withStyle(ChatFormatting.YELLOW)));
	}

	public static void cmdLock(ServerPlayer p) {
		Data.PlayerData d = data(p);
		long now = System.currentTimeMillis();
		if (!d.hasBase) { say(p, "Voce nao tem base."); return; }
		if (now < d.lockedUntilMs) { say(p, "Sua base ja esta trancada."); return; }
		if (d.coins < 100) { say(p, "Precisa de 100 moedas."); return; }
		d.coins -= 100;
		d.lockedUntilMs = now + 60_000L;
		say(p, "Base trancada por 60s!");
	}

	public static void cmdUpgradeSpeed(ServerPlayer p) {
		Data.PlayerData d = data(p);
		if (d.speedLevel >= MAX_SPEED) { say(p, "Velocidade ja esta no maximo!"); return; }
		long cost = 150L * (d.speedLevel + 1) * (d.speedLevel + 1);
		if (d.coins < cost) { say(p, "Precisa de " + fmt(cost) + " moedas (voce tem " + fmt(d.coins) + ")."); return; }
		d.coins -= cost;
		d.speedLevel++;
		say(p, "Velocidade nivel " + d.speedLevel + "! Proximo: " + (d.speedLevel >= MAX_SPEED ? "maximo" : fmt(150L * (d.speedLevel + 1) * (d.speedLevel + 1)) + " moedas"));
		sound(server.overworld(), p.blockPosition(), SoundEvents.PLAYER_LEVELUP);
	}

	public static void cmdStatus(ServerPlayer p) {
		Data.PlayerData d = data(p);
		long now = System.currentTimeMillis();
		say(p, "Moedas: " + fmt(d.coins) + " | Renda: " + String.format(PT, "%,.1f", d.incomePerSec()) + "/s | Velocidade: " + d.speedLevel + "/" + MAX_SPEED);
		if (!d.hasBase) { say(p, "Sem base ainda. Use /ovo base."); return; }
		for (int i = 0; i < SLOTS; i++) {
			Data.Slot s = d.slots[i];
			String txt;
			if (s.state == 0) txt = "vazio";
			else if (s.state == 1) txt = "ovo " + Rarity.values()[s.rarity].label + " de " + Biome.values()[s.biome].label + " (" + Math.max(0, (s.hatchAtMs - now) / 1000) + "s)";
			else txt = s.petName + " [" + Rarity.values()[s.rarity].label + ", " + Biome.values()[s.biome].label + "]";
			p.sendSystemMessage(Component.literal("  Pedestal " + (i + 1) + ": " + txt).withStyle(ChatFormatting.GRAY));
		}
	}

	public static void cmdTop(ServerPlayer p) {
		List<Data.PlayerData> all = new ArrayList<>(save.players.values());
		all.sort(Comparator.comparingDouble((Data.PlayerData d) -> d.coins).reversed());
		say(p, "=== Mais ricos ===");
		for (int i = 0; i < Math.min(5, all.size()); i++) {
			p.sendSystemMessage(Component.literal("  " + (i + 1) + ". " + all.get(i).name + " - " + fmt(all.get(i).coins)).withStyle(ChatFormatting.GRAY));
		}
	}
}
