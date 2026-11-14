package ru.vadim.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import ru.vadim.entity.CarEntity;
import ru.vadim.entity.ModEntities;

/**
 * Машины за окном бара. Пока в хабе кто-то есть — раз в 5–14 секунд по улице проезжает машина:
 * седан, такси или грузовик; изредка пасхалки — буханка «Влог конченных» и опелелёт (летит над улицей).
 * Движение правостороннее: на восток по южной полосе, на запад — по северной.
 */
public final class Traffic {
	private static int next = 100;
	private static int sinceEgg = 0;

	private Traffic() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Traffic::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel hub = Places.hub(server);
		if (hub == null || hub.players().isEmpty()) return;
		if (--next > 0) return;
		RandomSource r = hub.random;
		next = 100 + r.nextInt(180);
		sinceEgg++;
		spawn(hub, pick(r), r.nextBoolean());
	}

	private static CarEntity.Kind pick(RandomSource r) {
		// пасхалки — не чаще чем раз в ~6 машин
		if (sinceEgg > 6 && r.nextFloat() < 0.3f) {
			sinceEgg = 0;
			return r.nextBoolean() ? CarEntity.Kind.VAN : CarEntity.Kind.OPEL;
		}
		float f = r.nextFloat();
		return f < 0.55f ? CarEntity.Kind.SEDAN : f < 0.8f ? CarEntity.Kind.TAXI : CarEntity.Kind.TRUCK;
	}

	/** Пустить машину. east = едет на восток (+X). */
	public static CarEntity spawn(ServerLevel hub, CarEntity.Kind kind, boolean east) {
		EntityType<CarEntity> type = kind == CarEntity.Kind.VAN ? ModEntities.VAN : ModEntities.CAR;
		CarEntity c = type.create(hub, EntitySpawnReason.TRIGGERED);
		if (c == null) return null;
		RandomSource r = hub.random;
		double startX = east ? -Bar.STREET_END : Bar.STREET_END;
		double z = east ? Bar.LANE_EAST : Bar.LANE_WEST;
		double y = Bar.ROAD_Y;
		float speed = 0.38f + r.nextFloat() * 0.2f;
		if (kind == CarEntity.Kind.TRUCK) speed *= 0.8f;
		if (kind == CarEntity.Kind.OPEL) { y = 69.5; z = 13; speed = 0.7f; }
		c.setKind(kind);
		c.setPaint(r.nextInt(6));
		c.setPos(startX, y, z);
		c.endX = -startX;
		c.setSpeed(east ? speed : -speed);
		c.markStart();
		hub.addFreshEntity(c);
		return c;
	}
}
