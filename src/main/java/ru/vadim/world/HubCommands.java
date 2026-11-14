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
 *  /vadim car <тип> [восток] — пустить машину (sedan, taxi, truck, van, opel);
 *  /vadim menushot          — встать в точку съёмки и снять кадр для главного меню;
 *  /vadim news [stop]       — запустить катсцену с новостями без кнопки «Готов» / прервать её;
 *  /vadim zavod             — сразу ко входу в заброшку.
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
				.then(Commands.literal("news")
						.executes(c -> {
							News.start(c.getSource().getServer());
							return 1;
						})
						.then(Commands.literal("stop").executes(c -> {
							News.stop(c.getSource().getServer());
							return 1;
						})))
				.then(Commands.literal("zavod").executes(c -> {
					News.sendToZavod(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("menushot").executes(c -> {
					var p = c.getSource().getPlayerOrException();
					p.teleportTo(Places.hub(c.getSource().getServer()), 0.5, 64, -5.2, java.util.Set.of(), 0f, 4f, true);
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new ru.vadim.net.HubPayloads.MenuShot());
					return 1;
				}))
				.then(Commands.literal("car").then(Commands.argument("kind", StringArgumentType.word())
						.executes(c -> car(c.getSource().getLevel(), StringArgumentType.getString(c, "kind"), true))
						.then(Commands.argument("east", BoolArgumentType.bool())
								.executes(c -> car(c.getSource().getLevel(), StringArgumentType.getString(c, "kind"), BoolArgumentType.getBool(c, "east"))))))));
	}

	private static int car(ServerLevel l, String kind, boolean east) {
		ServerLevel hub = Places.hub(l.getServer());
		if (hub == null) return 0;
		try {
			Traffic.spawn(hub, CarEntity.Kind.valueOf(kind.toUpperCase()), east);
			return 1;
		} catch (IllegalArgumentException e) {
			return 0;
		}
	}
}
