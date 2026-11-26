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
 *  /vadim rebuild [zavod]   — пересобрать бар и квартал (или двор завода);
 *  /vadim hub               — вернуться в бар;
 *  /vadim car <тип> [восток] — пустить машину (sedan, taxi, truck, van, opel);
 *  /vadim menushot          — встать в точку съёмки и снять кадр для главного меню;
 *  /vadim news [stop]       — запустить катсцену с новостями без кнопки «Готов» / прервать её;
 *  /vadim zavod [break|reset] — ко входу в заброшку (break — сразу сбить цепь, reset — вернуть цепь и лом, закрыть дверь);
 *  /vadim room1 [skip|cutters|code|win|reset|info] — комната 1: заново с вступлением / без него, выдать болторез,
 *                                                     код в чат, сразу победа, сбросить «пройдена», состояние.
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
				}).then(Commands.literal("zavod").executes(c -> {
					Hub.rebuildZavod(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Двор завода пересобран"), true);
					return 1;
				})))
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
				}).then(Commands.literal("path").executes(c -> {
					String r = ZavodInside.checkPath(Places.zavod(c.getSource().getServer()));
					c.getSource().sendSuccess(() -> Component.literal(r), false);
					return 1;
				})).then(Commands.literal("break").executes(c -> {
					ZavodDoor.forceBreak(c.getSource().getServer());
					return 1;
				})).then(Commands.literal("gas").executes(c -> {
					ZavodFinal.force(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Гермодверь закрывается, газ через 10 с"), true);
					return 1;
				})).then(Commands.literal("reset").executes(c -> {
					ZavodDoor.reset(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Цепь на месте, лом на столе, дверь закрыта, гермодверь поднята"), true);
					return 1;
				})))
				.then(Commands.literal("room1").executes(c -> {
					Room1.restart(c.getSource().getServer(), true);
					c.getSource().sendSuccess(() -> Component.literal("Комната 1: заново, с вступлением"), true);
					return 1;
				}).then(Commands.literal("skip").executes(c -> {
					Room1.restart(c.getSource().getServer(), false);
					c.getSource().sendSuccess(() -> Component.literal("Комната 1: заново, без вступления"), true);
					return 1;
				})).then(Commands.literal("reset").executes(c -> {
					Room1.restart(c.getSource().getServer(), true);
					c.getSource().sendSuccess(() -> Component.literal("Комната 1 сброшена"), true);
					return 1;
				})).then(Commands.literal("cutters").executes(c -> {
					Room1.giveCutters(c.getSource().getPlayerOrException());
					return 1;
				})).then(Commands.literal("code").executes(c -> {
					String code = Room1.codeString();
					c.getSource().sendSuccess(() -> Component.literal("Код кладовки: " + code), false);
					return 1;
				})).then(Commands.literal("win").executes(c -> {
					Room1.forceWin(c.getSource().getServer());
					return 1;
				})).then(Commands.literal("info").executes(c -> {
					String info = Room1.debug();
					c.getSource().sendSuccess(() -> Component.literal(info), false);
					return 1;
				})))
				.then(Commands.literal("room2").executes(c -> {
					Room2.restart(c.getSource().getServer(), false);
					c.getSource().sendSuccess(() -> Component.literal("Комната 2: все в студии, плита внизу, пластинка на полу"), true);
					return 1;
				}).then(Commands.literal("skip").executes(c -> {
					Room2.restart(c.getSource().getServer(), true);
					c.getSource().sendSuccess(() -> Component.literal("Комната 2: без пластинки, жмите кнопку"), true);
					return 1;
				})).then(Commands.literal("start").executes(c -> {
					Room2.forceStart(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Комната 2: кабинки открыты"), true);
					return 1;
				})).then(Commands.literal("seq").executes(c -> {
					String s = Room2.seqString();
					c.getSource().sendSuccess(() -> Component.literal(s), false);
					return 1;
				})).then(Commands.literal("win").executes(c -> {
					Room2.forceWin(c.getSource().getServer());
					return 1;
				})).then(Commands.literal("reset").executes(c -> {
					Room2.reset(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Комната 2 сброшена: плита наверху, все у выхода из комнаты 1"), true);
					return 1;
				})).then(Commands.literal("info").executes(c -> {
					String info = Room2.debug();
					c.getSource().sendSuccess(() -> Component.literal(info), false);
					return 1;
				})))
				.then(Commands.literal("room3").executes(c -> {
					Room3.restart(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Комната 3: все в мини-мире, ждём первого действия"), true);
					return 1;
				}).then(Commands.literal("start").executes(c -> {
					Room3.forceStart(c.getSource().getServer());
					return 1;
				})).then(Commands.literal("fill").executes(c -> {
					Room3.fill(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Комната 3: в машине 127/128 брёвен и 63/64 говна"), true);
					return 1;
				})).then(Commands.literal("win").executes(c -> {
					Room3.forceWin(c.getSource().getServer());
					return 1;
				})).then(Commands.literal("zombie").executes(c -> {
					Room3.zombie(c.getSource().getServer());
					return 1;
				})).then(Commands.literal("kit").executes(c -> {
					Room3.kit(c.getSource().getPlayerOrException());
					return 1;
				})).then(Commands.literal("time").then(Commands.argument("sec", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 600)).executes(c -> {
					Room3.setTime(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "sec"));
					return 1;
				}))).then(Commands.literal("reset").executes(c -> {
					Room3.reset(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Комната 3 сброшена и отстроена заново"), true);
					return 1;
				})).then(Commands.literal("info").executes(c -> {
					String info = Room3.debug();
					c.getSource().sendSuccess(() -> Component.literal(info), false);
					return 1;
				})))
				.then(Commands.literal("room4").executes(c -> {
					var p = c.getSource().getPlayerOrException();
					Room4.place(Places.rooms(c.getSource().getServer()), p);
					return 1;
				}).then(Commands.literal("tp").then(Commands.argument("sector", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3)).executes(c -> {
					var p = c.getSource().getPlayerOrException();
					Room4.spawnAt(Places.rooms(c.getSource().getServer()), p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "sector") - 1);
					return 1;
				}))).then(Commands.literal("dark").then(Commands.argument("zone", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4)).executes(c -> {
					var p = c.getSource().getPlayerOrException();
					var l = Places.rooms(c.getSource().getServer());
					var at = Room4.darkSpot(l, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "zone"));
					p.teleportTo(l, at.x, at.y, at.z, java.util.Set.of(), p.getYRot(), 0f, true);
					return 1;
				}))).then(Commands.literal("lift").executes(c -> {
					var p = c.getSource().getPlayerOrException();
					p.teleportTo(Places.rooms(c.getSource().getServer()), Room4.LIFT.x, Room4.LIFT.y, Room4.LIFT.z + 4, java.util.Set.of(), 180f, 0f, true);
					return 1;
				})).then(Commands.literal("rebuild").executes(c -> {
					Room4.build(Places.rooms(c.getSource().getServer()));
					c.getSource().sendSuccess(() -> Component.literal("Комната 4 отстроена заново"), true);
					return 1;
				})).then(Commands.literal("reset").executes(c -> {
					Room4.resetSectors(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Комната 4: сектора игроков сброшены"), true);
					return 1;
				})).then(Commands.literal("power").then(Commands.argument("sector", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3)).executes(c -> {
					Room4Power.power(Places.rooms(c.getSource().getServer()), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "sector") - 1, true);
					return 1;
				}))).then(Commands.literal("blackout").then(Commands.argument("sector", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3)).executes(c -> {
					Room4Power.blackout(Places.rooms(c.getSource().getServer()), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "sector") - 1, 200);
					return 1;
				}))).then(Commands.literal("role").then(Commands.argument("role", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3)).executes(c -> {
					Room4Power.setRole(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "role") - 1);
					return 1;
				}))).then(Commands.literal("fuses").executes(c -> {
					c.getSource().getPlayerOrException().getInventory().add(new net.minecraft.world.item.ItemStack(ru.vadim.item.ModItems.FUSE, 3));
					return 1;
				})).then(Commands.literal("box").then(Commands.argument("sector", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3)).executes(c -> {
					var p = c.getSource().getPlayerOrException();
					var l = Places.rooms(c.getSource().getServer());
					var b = Room4Power.box(l, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "sector") - 1);
					if (b != null) p.teleportTo(l, b.getX() + 0.5, Room4.FLOOR + 1, b.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
					return 1;
				}))).then(Commands.literal("resetpower").executes(c -> {
					Room4Power.resetAll(Places.rooms(c.getSource().getServer()));
					c.getSource().sendSuccess(() -> Component.literal("Комната 4: ток, предохранители и роли сброшены"), true);
					return 1;
				})).then(Commands.literal("map").executes(c -> {
					String r = Room4.dumpMap(Places.rooms(c.getSource().getServer()));
					c.getSource().sendSuccess(() -> Component.literal(r), false);
					return 1;
				})).then(Commands.literal("info").executes(c -> {
					String info = Room4.debug(c.getSource().getServer()) + "\n" + Room4Power.debug(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal(info), false);
					return 1;
				})))
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
