package ru.vadim.world;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import com.mojang.math.Transformation;
import ru.vadim.VadimNaTrone;
import ru.vadim.net.HubPayloads;

import java.util.Set;

/**
 * Комнаты Вадима (измерение vadim_na_trone:rooms). Пока — заглушка первой комнаты: тёмная бетонная коробка
 * 9×9, одна лампа, табличка «скоро». Сюда попадают после газа на заводе (ZavodFinal) и при перезаходе после него.
 */
public final class Rooms {
	public static final int VERSION = 1;
	public static final String TAG = "vadim_rooms";

	private Rooms() {}

	public static void ensure(ServerLevel l) {
		Built b = l.getDataStorage().computeIfAbsent(Built.TYPE);
		if (b.version == VERSION) return;
		long t0 = System.currentTimeMillis();
		for (int x = -6; x <= 6; x++)
			for (int z = -6; z <= 6; z++)
				for (int y = 62; y <= 70; y++) {
					boolean inside = x > -5 && x < 5 && z > -5 && z < 5 && y > 63 && y < 69;
					BlockState s;
					if (inside) s = Blocks.AIR.defaultBlockState();
					else if (y == 63 || y == 62) s = ((x + z) & 3) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
					else if (y >= 69) s = Blocks.GRAY_CONCRETE.defaultBlockState();
					else s = ((x * 7 + y * 3 + z * 5) % 9 == 0) ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
					l.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS);
				}
		l.setBlock(new BlockPos(0, 68, 0), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), Block.UPDATE_ALL);
		l.setBlock(new BlockPos(3, 64, -4), Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		l.setBlock(new BlockPos(-4, 64, 3), Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		// табличка на северной стене
		for (var e : l.getEntities(EntityType.TEXT_DISPLAY, en -> en.getTags().contains(TAG))) e.discard();
		Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		if (d != null) {
			d.setPos(0.5, 66.2, -4.45);
			d.setText(Component.literal("КОМНАТА 1\nв разработке").withColor(0x8C8C8C));
			d.setTransformation(new Transformation(new Matrix4f().scale(0.9f)));
			d.addTag(TAG);
			l.addFreshEntity(d);
		}
		b.version = VERSION;
		b.setDirty();
		VadimNaTrone.LOG.info("Комната-заглушка построена за {} мс", System.currentTimeMillis() - t0);
	}

	/** Перенести в комнату: появляются лёжа (клиент поднимает камеру с пола). */
	public static void send(ServerPlayer p) {
		ServerLevel l = Places.rooms(p.level().getServer());
		if (l == null) {
			VadimNaTrone.LOG.error("Нет измерения комнат — датапак мода не загрузился");
			return;
		}
		ensure(l);
		if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
		if (p.isPassenger()) p.stopRiding();
		Vec3 s = Places.ROOMS_SPAWN;
		double ox = (p.getRandom().nextDouble() - 0.5) * 4, oz = (p.getRandom().nextDouble() - 0.5) * 4;
		p.teleportTo(l, s.x + ox, s.y, s.z + oz, Set.of(), p.getRandom().nextFloat() * 360f, 0f, true);
		p.fallDistance = 0;
		if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodFinal.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodFinal(3, 0));
	}

	static final class Built extends SavedData {
		static final Codec<Built> CODEC = Codec.INT.xmap(v -> {
			Built b = new Built();
			b.version = v;
			return b;
		}, b -> b.version);
		static final SavedDataType<Built> TYPE = new SavedDataType<>("vadim_rooms_built", Built::new, CODEC, null);
		int version;
	}
}
