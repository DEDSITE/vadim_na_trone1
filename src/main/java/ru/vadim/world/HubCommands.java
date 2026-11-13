package ru.vadim.world;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import ru.vadim.entity.CarEntity;

/**
 * Отладка хаба (нужны права оператора / читы):
 *  /vadim rebuild           — пересобрать бар и квартал;
 *  /vadim hub               — вернуться в бар;
 *  /vadim car <тип> [восток] — пустить машину (sedan, taxi, truck, van, opel).
 */
public final class HubCommands {
	private HubCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("vadim")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("rebuild").executes(c -> {
					Hub.rebuild(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Бар пересобран"), true);
					return 1;
				}))
				.then(Commands.literal("hub").executes(c -> {
					Hub.sendToHub(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("car").then(Commands.argument("kind", StringArgumentType.word())
						.executes(c -> car(c.getSource().getLevel(), StringArgumentType.getString(c, "kind"), true))
						.then(Commands.argument("east", BoolArgumentType.bool())
								.executes(c -> car(c.getSource().getLevel(), StringArgumentType.getString(c, "kind"), BoolArgumentType.getBool(c, "east"))))))));
	}

	private static int car(ServerLevel l, String kind, boolean east) {
		ServerLevel hub = Places.hub(l.getServer());
		try {
			Traffic.spawn(hub, CarEntity.Kind.valueOf(kind.toUpperCase()), east);
			return 1;
		} catch (IllegalArgumentException e) {
			return 0;
		}
	}
}
