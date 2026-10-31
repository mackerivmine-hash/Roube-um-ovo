package com.roubeumovo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class OvoCommands {
	private OvoCommands() {}

	private static int run(CommandContext<CommandSourceStack> ctx, Consumer<ServerPlayer> action) throws CommandSyntaxException {
		action.accept(ctx.getSource().getPlayerOrException());
		return 1;
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("ovo")
			.executes(c -> run(c, Game::cmdStatus))
			.then(Commands.literal("ajuda").executes(c -> run(c, Game::cmdHelp)))
			.then(Commands.literal("status").executes(c -> run(c, Game::cmdStatus)))
			.then(Commands.literal("base").executes(c -> run(c, Game::cmdBase)))
			.then(Commands.literal("casa").executes(c -> run(c, Game::cmdHome)))
			.then(Commands.literal("mapa").executes(c -> run(c, Game::cmdMap))
				.then(Commands.literal("gerar").executes(c -> run(c, Game::cmdMapGenerate))))
			.then(Commands.literal("ninhos").executes(c -> run(c, Game::cmdNests)))
			.then(Commands.literal("trancar").executes(c -> run(c, Game::cmdLock)))
			.then(Commands.literal("top").executes(c -> run(c, Game::cmdTop)))
			.then(Commands.literal("upgrade")
				.then(Commands.literal("velocidade").executes(c -> run(c, Game::cmdUpgradeSpeed)))));
	}
}
