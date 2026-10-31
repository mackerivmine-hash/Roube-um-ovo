package com.roubeumovo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoubeUmOvoMod implements ModInitializer {
	public static final String MOD_ID = "roubeumovo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(Game::onStart);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Game.save());
		ServerTickEvents.END_SERVER_TICK.register(Game::onTick);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> OvoCommands.register(dispatcher));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Game.onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Game.onLeave(handler.getPlayer()));

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) Game.dropCarry(player);
		});

		// Clique direito: ninhos e pedestais
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (level.dimension() != Level.OVERWORLD || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			return Game.onUse(sp, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});

		// Impede quebrar/teleportar ovos e blocos da base
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide() || level.dimension() != Level.OVERWORLD) return InteractionResult.PASS;
			return Game.isProtected(pos) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
			level.dimension() != Level.OVERWORLD || !Game.isProtected(pos));

		LOGGER.info("Roube um Ovo carregado!");
	}
}
