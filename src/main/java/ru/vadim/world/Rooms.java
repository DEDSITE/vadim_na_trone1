package ru.vadim.world;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
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
	public static final int VERSION = 2;
	public static final String TAG = "vadim_rooms";

	private Rooms() {}

	private static BlockState b(Block x) { return x.defaultBlockState(); }

	private static void set(ServerLevel l, int x, int y, int z, BlockState st) {
		l.setBlock(new BlockPos(x, y, z), st, Block.UPDATE_CLIENTS);
	}

	/**
	 * Комната 1 «Стулья»: бойня/операционная. Пол y=63, внутри x −6..6, z −5..5, y 64..69, потолок 70.
	 * На востоке за железной дверью (7,64,−1) — кладовка x 8..11, z −3..1; на севере дверь (−3,64,−6) → коридор → комната 2 (заглушка).
	 * Над стульями в потолке шахты, из них спускаются «ракеты».
	 */
	public static void ensure(ServerLevel l) {
		Built bt = l.getDataStorage().computeIfAbsent(Built.TYPE);
		if (bt.version == VERSION) return;
		long t0 = System.currentTimeMillis();
		java.util.Random r = new java.util.Random(4242);
		// всё старое — в воздух
		for (int x = -14; x <= 16; x++) for (int z = -22; z <= 12; z++) for (int y = 60; y <= 80; y++) set(l, x, y, z, b(Blocks.AIR));
		for (var e : l.getEntities(EntityType.TEXT_DISPLAY, en -> en.getTags().contains(TAG))) e.discard();
		BlockState black = b(Blocks.BLACK_CONCRETE);
		// оболочка: всё бетоном, потом вырезаем
		for (int x = -8; x <= 13; x++) for (int z = -18; z <= 7; z++) for (int y = 62; y <= 71; y++) set(l, x, y, z, b(Blocks.GRAY_CONCRETE));
		// главная комната
		for (int x = -6; x <= 6; x++) for (int z = -5; z <= 5; z++) {
			boolean dark = ((x + z) & 1) == 0;
			BlockState fl = dark ? b(Blocks.CALCITE) : b(Blocks.WHITE_CONCRETE);
			int k = r.nextInt(100);
			if (k < 9) fl = b(Blocks.WHITE_TERRACOTTA);
			else if (k < 12) fl = b(Blocks.CRACKED_STONE_BRICKS);
			else if (k < 14) fl = b(Blocks.BROWN_TERRACOTTA);
			set(l, x, 63, z, fl);
			for (int y = 64; y <= 69; y++) set(l, x, y, z, b(Blocks.AIR));
			set(l, x, 70, z, b(Blocks.SMOOTH_STONE));
		}
		// стены: плитка до 67, выше — грязная штукатурка
		for (int y = 64; y <= 69; y++) {
			for (int x = -7; x <= 7; x++) for (int z : new int[]{-6, 6}) set(l, x, y, z, wall(r, y));
			for (int z = -6; z <= 6; z++) for (int x : new int[]{-7, 7}) set(l, x, y, z, wall(r, y));
		}
		// шахты над стульями: в потолке дыры, сверху чёрный колодец до 77
		for (int i = 0; i < 3; i++) {
			var m = Room1.SEATS[i].mouth();
			int sx = (int) Math.floor(m.x), sz = (int) Math.floor(m.z);
			for (int y = 70; y <= 77; y++) {
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(l, sx + dx, y, sz + dz, black);
				set(l, sx, y, sz, b(Blocks.AIR));
			}
			set(l, sx, 78, sz, black);
		}
		// хирургическая лампа над стульями
		set(l, 0, 69, 0, b(Blocks.IRON_CHAIN));
		set(l, 0, 68, 0, b(Blocks.LIGHT).setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 13));
		set(l, 1, 68, 1, b(Blocks.LIGHT).setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 9));
		// монитор светит чуть-чуть
		set(l, 3, 67, -4, b(Blocks.LIGHT).setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 5));
		// сток, раковина, операционный стол (кровать), крюки, тряпки, паутина
		set(l, 3, 64, 3, b(Blocks.IRON_TRAPDOOR));
		set(l, -6, 64, -5, b(Blocks.CAULDRON));
		set(l, -6, 65, -5, b(Blocks.TRIPWIRE_HOOK).setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, Direction.EAST));
		set(l, -5, 64, 2, b(Blocks.WHITE_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
				.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
		set(l, -5, 64, 3, b(Blocks.WHITE_BED).setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
				.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		for (int[] h : new int[][]{{-3, 3}, {4, -2}, {-4, -3}, {5, 4}}) {
			set(l, h[0], 69, h[1], b(Blocks.IRON_CHAIN));
			set(l, h[0], 68, h[1], b(Blocks.IRON_CHAIN));
			set(l, h[0], 67, h[1], b(Blocks.TRIPWIRE_HOOK).setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, Direction.NORTH));
		}
		set(l, 5, 64, -4, b(Blocks.RED_CARPET));
		set(l, -2, 64, 4, b(Blocks.WHITE_CARPET));
		set(l, 6, 64, 5, b(Blocks.BARREL));
		set(l, -6, 64, 5, b(Blocks.COBWEB));
		set(l, 6, 69, -5, b(Blocks.COBWEB));
		set(l, -6, 69, 4, b(Blocks.COBWEB));
		// кладовка
		for (int x = 8; x <= 11; x++) for (int z = -3; z <= 1; z++) {
			set(l, x, 63, z, r.nextInt(4) == 0 ? b(Blocks.WHITE_TERRACOTTA) : b(Blocks.CALCITE));
			for (int y = 64; y <= 67; y++) set(l, x, y, z, b(Blocks.AIR));
			set(l, x, 68, z, b(Blocks.SMOOTH_STONE));
		}
		for (int y = 64; y <= 68; y++) {
			for (int x = 8; x <= 12; x++) for (int z : new int[]{-4, 2}) set(l, x, y, z, wall(r, y));
			for (int z = -4; z <= 2; z++) set(l, 12, y, z, wall(r, y));
		}
		set(l, 11, 64, -3, b(Blocks.BARREL));
		set(l, 11, 64, 1, b(Blocks.BARREL));
		set(l, 11, 65, 1, b(Blocks.BARREL));
		set(l, 8, 64, 1, b(Blocks.CHEST).setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.WEST));
		set(l, 11, 66, -1, b(Blocks.TRIPWIRE_HOOK).setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, Direction.WEST));
		set(l, 10, 67, -1, b(Blocks.LIGHT).setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 6));
		door(l, Room1.CLOSET_DOOR, Direction.EAST);
		// выход: коридор на север и комната 2 (заглушка)
		door(l, Room1.EXIT_DOOR, Direction.NORTH);
		for (int z = -10; z <= -7; z++) for (int x = -4; x <= -2; x++) {
			set(l, x, 63, z, b(Blocks.SMOOTH_STONE));
			for (int y = 64; y <= 66; y++) set(l, x, y, z, b(Blocks.AIR));
		}
		for (int x = -6; x <= 0; x++) for (int z = -16; z <= -11; z++) {
			set(l, x, 63, z, b(Blocks.SMOOTH_STONE));
			for (int y = 64; y <= 68; y++) set(l, x, y, z, b(Blocks.AIR));
		}
		set(l, -3, 68, -13, b(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		if (d != null) {
			d.setPos(-2.5, 66.2, -15.45);
			d.setText(Component.literal("КОМНАТА 2\nв разработке").withColor(0x8C8C8C));
			d.setTransformation(new Transformation(new Matrix4f().scale(0.9f)));
			d.addTag(TAG);
			l.addFreshEntity(d);
		}
		bt.version = VERSION;
		bt.setDirty();
		VadimNaTrone.LOG.info("Комната 1 построена за {} мс", System.currentTimeMillis() - t0);
	}

	private static BlockState wall(java.util.Random r, int y) {
		int k = r.nextInt(100);
		if (y <= 67) {
			if (k < 10) return b(Blocks.CRACKED_STONE_BRICKS);
			if (k < 18) return b(Blocks.WHITE_TERRACOTTA);
			if (k < 21) return b(Blocks.BROWN_TERRACOTTA);
			return b(Blocks.QUARTZ_BRICKS);
		}
		if (k < 20) return b(Blocks.WHITE_TERRACOTTA);
		if (k < 30) return b(Blocks.GRAY_CONCRETE);
		return b(Blocks.SMOOTH_STONE);
	}

	private static void door(ServerLevel l, BlockPos at, Direction facing) {
		BlockState lo = b(Blocks.IRON_DOOR).setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
		l.setBlock(at, lo, Block.UPDATE_CLIENTS);
		l.setBlock(at.above(), lo.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
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
		boolean won = Room1.won(p.level().getServer());
		Vec3 s = won ? Room1.AFTER_WIN_SPAWN : Places.ROOMS_SPAWN;
		p.teleportTo(l, s.x, s.y, s.z, Set.of(), 180f, 0f, true);
		p.fallDistance = 0;
		// очнулись: на стуле (elapsed = 1 — пробуждение сидя) или у выхода, если комната уже пройдена
		if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodFinal.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodFinal(3, won ? 0 : 1));
		Room1.join(p);
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
