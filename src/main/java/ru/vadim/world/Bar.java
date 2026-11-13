package ru.vadim.world;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.joml.Vector3f;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.ModBlocks;
import ru.vadim.entity.BarmanEntity;
import ru.vadim.entity.ModEntities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Хаб: ночной квартал. Улица вдоль X, в доме на северной стороне — бар «Трон».
 * Всё строится кодом один раз на мир (и заново, если поменялась VERSION).
 *
 * Бар: зал x -9..9, z -10..2, пол y=63, потолок y=69. Стойка на z=-6, за ней бармен.
 * Окна во всю южную стену (z=3) на улицу: тротуар z 4..7, дорога z 8..18, тротуар z 19..22, дома с z=23.
 * Мебель, лампы, гирлянды, шторы — из модов Macaw's (если мода нет, ставится ванильная замена).
 */
public final class Bar {
	/** Поменял планировку — увеличь, и при следующем запуске хаб пересоберётся. */
	public static final int VERSION = 1;
	public static final String TAG = "vadim_hub";

	public static final BlockPos BARMAN = new BlockPos(0, 64, -8);
	public static final BlockPos READY_BUTTON = new BlockPos(6, 65, 2);
	public static final BlockPos TV = new BlockPos(0, 66, -10);
	/** Колонки по углам зала — из них играет джаз (клиент ищет блоки SPEAKER рядом). */
	public static final BlockPos[] SPEAKERS = {
			new BlockPos(-9, 68, -10), new BlockPos(9, 68, -10), new BlockPos(-9, 68, 2), new BlockPos(9, 68, 2)};
	/** Улица: полосы движения (Z) и уровень дороги (Y). */
	public static final double LANE_EAST = 15.0, LANE_WEST = 11.0, ROAD_Y = 63.0, STREET_END = 82;

	private final ServerLevel l;
	private final RandomSource rnd = RandomSource.create(1337);
	private final Map<String, BlockState> cache = new HashMap<>();
	private final List<BlockPos> reshape = new ArrayList<>();

	private Bar(ServerLevel l) { this.l = l; }

	// ======================================================================= сборка

	/** Построить, если ещё не строили (или поменялась версия). Вызывается при старте сервера. */
	public static void ensure(ServerLevel hub, boolean force) {
		Built st = hub.getDataStorage().computeIfAbsent(Built.TYPE);
		if (!force && st.version == VERSION) {
			spawnEntities(hub, false);
			return;
		}
		long t0 = System.currentTimeMillis();
		removeTagged(hub);
		Bar b = new Bar(hub);
		b.clear();
		b.street();
		b.cityNorth();
		b.citySouth();
		b.barBuilding();
		b.barInterior();
		b.fixShapes();
		spawnEntities(hub, true);
		st.version = VERSION;
		st.setDirty();
		VadimNaTrone.LOG.info("Хаб (бар «Трон» и квартал) построен за {} мс", System.currentTimeMillis() - t0);
	}

	// ======================================================================= блоки

	/** Блок по строке вида "mod:id[prop=val]". Нет мода — берём замену (fallback), её тоже строкой. */
	private BlockState bs(String spec, String fallback) {
		String key = spec + "|" + fallback;
		BlockState c = cache.get(key);
		if (c != null) return c;
		BlockState s = parse(spec);
		if (s == null && fallback != null) s = parse(fallback);
		if (s == null) s = Blocks.AIR.defaultBlockState();
		cache.put(key, s);
		return s;
	}

	private BlockState bs(String spec) { return bs(spec, null); }

	private BlockState parse(String spec) {
		try {
			return BlockStateParser.parseForBlock(l.holderLookup(Registries.BLOCK), spec, false).blockState();
		} catch (Exception e) {
			return null;
		}
	}

	private void set(int x, int y, int z, BlockState s) {
		l.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private void set(int x, int y, int z, String spec) { set(x, y, z, bs(spec)); }

	/** Поставить и потом поправить форму по соседям (диваны, столы, фонари из частей). */
	private void setShaped(int x, int y, int z, BlockState s) {
		set(x, y, z, s);
		reshape.add(new BlockPos(x, y, z));
	}

	private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
	}

	private void fill(int x0, int y0, int z0, int x1, int y1, int z1, String spec) { fill(x0, y0, z0, x1, y1, z1, bs(spec)); }

	private void fixShapes() {
		for (int pass = 0; pass < 2; pass++)
			for (BlockPos p : reshape) {
				BlockState s = l.getBlockState(p);
				BlockState n = Block.updateFromNeighbourShapes(s, l, p);
				if (n != s) l.setBlock(p, n, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
	}

	private void clear() {
		for (Entity e : new ArrayList<Entity>(l.getEntities(EntityType.TEXT_DISPLAY, e -> true))) e.discard();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = -96; x <= 96; x++)
			for (int z = -24; z <= 44; z++)
				for (int y = 54; y <= 100; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!l.getBlockState(p).isAir()) l.setBlock(p, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
	}

	// ======================================================================= улица

	private void street() {
		int X = 92;
		// основание под всем кварталом
		fill(-X, 61, -16, X, 61, 40, "minecraft:stone");
		// дорога: асфальт с крошкой, разметка по оси
		for (int x = -X; x <= X; x++)
			for (int z = 8; z <= 18; z++) {
				int r = rnd.nextInt(10);
				set(x, 62, z, r < 7 ? "minecraft:black_concrete" : r < 9 ? "minecraft:gray_concrete_powder" : "minecraft:blackstone");
			}
		for (int x = -X; x <= X; x++) if (Math.floorMod(x, 6) < 3) set(x, 62, 13, "minecraft:white_concrete");
		// бордюр и тротуары (верх тротуара — на блок выше дороги)
		for (int x = -X; x <= X; x++) {
			for (int z = 4; z <= 7; z++) set(x, 63, z, (x + z) % 2 == 0 ? "minecraft:polished_andesite" : "minecraft:smooth_stone");
			for (int z = 19; z <= 22; z++) set(x, 63, z, (x + z) % 2 == 0 ? "minecraft:polished_andesite" : "minecraft:smooth_stone");
			set(x, 62, 7, "minecraft:stone_bricks");
			set(x, 62, 19, "minecraft:stone_bricks");
		}
		// фонари вдоль улицы: на северном тротуаре и напротив, в шахматку
		for (int x : new int[]{-60, -36, -16, 16, 36, 60}) lamp(x, 7, Direction.SOUTH);
		for (int x : new int[]{-48, -26, 0, 26, 48}) lamp(x, 19, Direction.NORTH);
		// поперечные дома в концах улицы — чтобы не было видно пустоты
		for (int sx : new int[]{-1, 1}) {
			int x0 = sx * 88;
			facade(x0, x0 + sx * 4, 4, 22, 64, 86, Direction.fromAxisAndDirection(Direction.Axis.X,
					sx < 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE), "minecraft:bricks", false, null);
		}
	}

	/** Уличный фонарь Macaw's из четырёх частей (без мода — железная решётка и фонарь). */
	private void lamp(int x, int z, Direction face) {
		BlockState part = bs("mcwlights:double_street_lamp[facing=" + face.getSerializedName() + ",lit=true]", null);
		if (part.isAir()) {
			fill(x, 64, z, x, 67, z, "minecraft:iron_bars");
			set(x, 68, z, "minecraft:lantern");
			return;
		}
		for (int y = 64; y <= 67; y++) setShaped(x, y, z, part);
	}

	// ======================================================================= дома

	private static final String[] WALLS = {"minecraft:bricks", "minecraft:stone_bricks", "minecraft:polished_andesite",
			"minecraft:white_concrete", "minecraft:light_gray_concrete", "minecraft:deepslate_bricks", "minecraft:mud_bricks",
			"minecraft:smooth_sandstone", "minecraft:terracotta", "minecraft:brown_terracotta"};
	private static final String[] LIT = {"minecraft:ochre_froglight", "minecraft:ochre_froglight", "minecraft:glowstone",
			"minecraft:pearlescent_froglight", "minecraft:sea_lantern", "minecraft:shroomlight"};
	private static final String[] CURTAINS = {"mcwwindows:white_curtain", "mcwwindows:red_curtain", "mcwwindows:brown_curtain",
			"mcwwindows:black_curtain", "mcwwindows:blue_curtain"};
	/** Вывески магазинов напротив бара (неон). */
	private static final String[][] SHOPS = {
			{"АПТЕКА 24", "#3dff6e"}, {"ШАУРМА", "#ffb13d"}, {"ЛОМБАРД", "#ff3d3d"}, {"ПРОДУКТЫ", "#3dd5ff"},
			{"ВЛОГ КОНЧЕННЫХ · студия", "#ff3df2"}, {"ГОВНОМЕСЫ & Co", "#c8a24a"}, {"ПИВО РАЗЛИВНОЕ", "#ffd23d"},
			{"СТРИПУХА · бьюти", "#ff6ad5"}, {"ХОЗТОВАРЫ", "#9dff3d"}, {"ЛОПАТЫ ОПТОМ", "#d0d0d0"}};

	private void cityNorth() {
		// северная сторона улицы: фасады по z=3 (бар — x -12..12, его строит barBuilding)
		street(-86, -13, Direction.SOUTH, 3, false);
		street(13, 86, Direction.SOUTH, 3, false);
	}

	private void citySouth() {
		street(-86, 86, Direction.NORTH, 23, true);
	}

	/** Ряд домов от x0 до x1. front — z фасада, face — куда смотрит фасад. */
	private void street(int x0, int x1, Direction face, int front, boolean shops) {
		int x = x0;
		int shop = 0;
		while (x <= x1) {
			int w = 9 + rnd.nextInt(7);
			if (x + w - 1 > x1) w = x1 - x + 1;
			if (w < 5) break;
			int top = 72 + rnd.nextInt(18);
			String wall = WALLS[rnd.nextInt(WALLS.length)];
			boolean hasShop = shops && w >= 8;
			String[] sign = hasShop ? SHOPS[shop++ % SHOPS.length] : null;
			int z0 = front, z1 = face == Direction.SOUTH ? front - 3 : front + 3;
			facade(x, x + w - 1, z0, z1, 64, top, face, wall, hasShop, sign);
			x += w;
			// переулок между некоторыми домами
			if (rnd.nextInt(4) == 0) x += 2;
		}
	}

	/**
	 * Дом-коробка: фасад в плоскости zFront, глубина до zBack. Окна 2×2 через этаж,
	 * за стеклом — шторы и свет (или темнота), первый этаж — витрина с неоновой вывеской.
	 */
	private void facade(int x0, int x1, int zFront, int zBack, int y0, int y1, Direction face, String wallSpec,
	                    boolean shop, String[] sign) {
		BlockState wall = bs(wallSpec);
		int minX = Math.min(x0, x1), maxX = Math.max(x0, x1), minZ = Math.min(zFront, zBack), maxZ = Math.max(zFront, zBack);
		fill(minX, y0 - 1, minZ, maxX, y1, maxZ, wall);
		// карниз и крыша
		for (int x = minX; x <= maxX; x++) set(x, y1 + 1, zFront, "minecraft:stone_brick_slab[type=bottom]");
		if (face.getAxis() == Direction.Axis.X) return;   // торцевые дома в концах улицы — глухие
		int dz = face == Direction.SOUTH ? -1 : 1;        // вглубь дома
		int first = shop ? 69 : 65;
		// витрина первого этажа
		if (shop) {
			for (int x = minX + 1; x <= maxX - 1; x++) {
				for (int y = 64; y <= 66; y++) {
					set(x, y, zFront, "minecraft:glass_pane");
					set(x, y, zFront + dz, Blocks.AIR.defaultBlockState());
				}
				set(x, 64, zFront + 2 * dz, "minecraft:spruce_planks");
				set(x, 65, zFront + 2 * dz, (x % 3 == 0) ? bs("minecraft:sea_lantern") : bs("minecraft:white_concrete"));
				set(x, 67, zFront + dz, "minecraft:glowstone");
			}
			fixPanes(minX + 1, maxX - 1, 64, 66, zFront);
			if (sign != null) neon(new net.minecraft.world.phys.Vec3((minX + maxX + 1) / 2.0, 67.15, zFront + (dz < 0 ? 1.06 : -0.06)),
					face, sign[0], sign[1], 1.2f);
		} else {
			// дверь подъезда
			int dx = minX + (maxX - minX) / 2;
			set(dx, 64, zFront, bs("mcwdoors:metal_windowed_door[facing=" + face.getSerializedName() + ",half=lower]", "minecraft:iron_door[half=lower]"));
			set(dx, 65, zFront, bs("mcwdoors:metal_windowed_door[facing=" + face.getSerializedName() + ",half=upper]", "minecraft:iron_door[half=upper]"));
			set(dx, 66, zFront - dz, bs("minecraft:lantern"));
		}
		// окна этажей
		for (int y = first; y + 1 <= y1 - 1; y += 4)
			for (int x = minX + 1; x + 1 <= maxX - 1; x += 3) {
				boolean lit = rnd.nextFloat() < 0.42f;
				String light = LIT[rnd.nextInt(LIT.length)];
				String curtain = rnd.nextFloat() < 0.5f ? CURTAINS[rnd.nextInt(CURTAINS.length)] : null;
				for (int wx = x; wx <= x + 1; wx++)
					for (int wy = y; wy <= y + 1; wy++) {
						set(wx, wy, zFront, "minecraft:glass_pane");
						BlockState in = Blocks.AIR.defaultBlockState();
						if (curtain != null && lit) in = bs(curtain + "[facing=" + face.getSerializedName() + "]", "minecraft:air");
						set(wx, wy, zFront + dz, in);
						set(wx, wy, zFront + 2 * dz, lit ? bs(light) : bs("minecraft:black_concrete"));
					}
				fixPanes(x, x + 1, y, y + 1, zFront);
				set(x, y - 1, zFront - dz, "minecraft:stone_brick_slab[type=top]");
				set(x + 1, y - 1, zFront - dz, "minecraft:stone_brick_slab[type=top]");
			}
	}

	/** Стёкла ставились без соседей — соединяем их по X. */
	private void fixPanes(int x0, int x1, int y0, int y1, int z) {
		for (int x = x0; x <= x1; x++)
			for (int y = y0; y <= y1; y++) {
				BlockPos p = new BlockPos(x, y, z);
				l.setBlock(p, Block.updateFromNeighbourShapes(l.getBlockState(p), l, p), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
	}

	// ======================================================================= здание бара

	private void barBuilding() {
		String brick = "minecraft:bricks";
		// коробка дома x -12..12, z -14..3, до y=84
		fill(-12, 62, -14, 12, 84, 3, brick);
		for (int x = -12; x <= 12; x++) set(x, 85, 3, "minecraft:brick_slab[type=bottom]");
		// цоколь
		fill(-12, 63, 3, 12, 63, 3, "minecraft:polished_blackstone_bricks");
		// окна верхних этажей (жилые, свет кое-где)
		for (int y = 72; y <= 81; y += 4)
			for (int x = -10; x <= 9; x += 4) {
				boolean lit = rnd.nextFloat() < 0.5f;
				for (int wx = x; wx <= x + 1; wx++)
					for (int wy = y; wy <= y + 1; wy++) {
						set(wx, wy, 3, "minecraft:glass_pane");
						set(wx, wy, 2, lit ? bs("mcwwindows:white_curtain[facing=south]", "minecraft:air") : Blocks.AIR.defaultBlockState());
						set(wx, wy, 1, lit ? "minecraft:ochre_froglight" : "minecraft:black_concrete");
					}
				fixPanes(x, x + 1, y, y + 1, 3);
				set(x, y - 1, 4, "minecraft:brick_slab[type=top]");
				set(x + 1, y - 1, 4, "minecraft:brick_slab[type=top]");
			}
		// вывеска над окнами бара
		neon(new net.minecraft.world.phys.Vec3(-1.0, 69.25, 4.06), Direction.SOUTH, "БАР «ТРОН»", "#ffcf6b", 2.2f);
		neon(new net.minecraft.world.phys.Vec3(-1.0, 68.75, 4.06), Direction.SOUTH, "работаем до последнего", "#ff8a3d", 0.8f);
		// козырёк над входом
		fill(7, 67, 4, 9, 67, 4, "minecraft:dark_oak_slab[type=top]");
	}

	private void barInterior() {
		// пустой зал
		fill(-9, 64, -10, 9, 68, 2, Blocks.AIR.defaultBlockState());
		// пол: тёмный дуб, за стойкой — плитка
		fill(-9, 63, -10, 9, 63, 2, "minecraft:dark_oak_planks");
		for (int x = -9; x <= 9; x++)
			for (int z = -10; z <= -7; z++) set(x, 63, z, (x + z) % 2 == 0 ? "minecraft:polished_deepslate" : "minecraft:polished_blackstone");
		// стены: кирпич, внизу деревянная панель
		for (int x = -10; x <= 10; x += 20) fill(x, 64, -11, x, 64, 3, "minecraft:dark_oak_planks");
		fill(-10, 64, -11, 10, 64, -11, "minecraft:dark_oak_planks");
		// потолок: тёмные доски, балки, над баром выемка под гирлянду (и второй слой сверху)
		fill(-10, 69, -11, 10, 69, 3, "minecraft:dark_oak_planks");
		fill(-10, 70, -11, 10, 70, 3, "minecraft:bricks");
		for (int z : new int[]{-9, -3, 1}) fill(-9, 69, z, 9, 69, z, "minecraft:stripped_dark_oak_log[axis=x]");

		// --- южная стена: большие окна на улицу, дверь, кнопка «Готов»
		fill(-9, 64, 3, 6, 64, 3, "minecraft:dark_oak_planks");
		for (int x = -9; x <= 6; x++) for (int y = 65; y <= 67; y++) set(x, y, 3, "minecraft:glass_pane");
		for (int x : new int[]{-5, -1, 3}) fill(x, 65, 3, x, 67, 3, "minecraft:stripped_dark_oak_log[axis=y]");
		fixPanes(-9, 6, 65, 67, 3);
		fill(-9, 68, 3, 9, 68, 3, "minecraft:dark_oak_planks");
		fill(7, 64, 3, 7, 67, 3, "minecraft:bricks");
		// входная дверь (заперта — открыть нельзя, см. Hub)
		set(8, 64, 3, bs("mcwdoors:store_door[facing=north,half=lower,hinge=left]", "minecraft:dark_oak_door[facing=north,half=lower]"));
		set(8, 65, 3, bs("mcwdoors:store_door[facing=north,half=upper,hinge=left]", "minecraft:dark_oak_door[facing=north,half=upper]"));
		fill(9, 64, 3, 9, 67, 3, "minecraft:bricks");
		set(READY_BUTTON.getX(), READY_BUTTON.getY(), READY_BUTTON.getZ(), ModBlocks.READY.defaultBlockState().setValue(ModBlocks.Facing.FACING, Direction.NORTH));
		// шторы-подвязки по краям окон
		for (int x : new int[]{-9, 6}) set(x, 67, 2, bs("mcwwindows:red_curtain[facing=north,tied=" + (x < 0 ? "left" : "right") + "]", "minecraft:air"));

		// --- задняя стена: шкафы, бутылки, полки, телевизор
		for (int x = -7; x <= 7; x++) {
			set(x, 64, -10, bs("mcwfurnitures:dark_oak_drawer_counter[facing=south]", "minecraft:dark_oak_planks"));
			if (Math.abs(x) <= 1) continue;
			set(x, 65, -10, bottles(x));
			set(x, 66, -10, "minecraft:dark_oak_slab[type=top]");
			set(x, 67, -10, bottles(x + 3));
		}
		set(0, 65, -10, bs("mcwholidays:single_potion[facing=south]", "minecraft:air"));
		set(TV.getX(), TV.getY(), TV.getZ(), ModBlocks.TV.defaultBlockState().setValue(ModBlocks.Facing.FACING, Direction.SOUTH));
		for (int x = -8; x <= 8; x++) set(x, 68, -10, bs("mcwholidays:yellow_tiny_string_lights[facing=south,twinkle=true]", "minecraft:air"));

		// --- барная стойка: буквой Г, слева закрыта, справа проход
		for (int x = -6; x <= 6; x++) set(x, 64, -6, bs("mcwfurnitures:dark_oak_counter[facing=north]", "minecraft:dark_oak_planks"));
		for (int z = -8; z <= -6; z++) set(-7, 64, z, bs("mcwfurnitures:dark_oak_counter[facing=east]", "minecraft:dark_oak_planks"));
		// на стойке — пара бутылок
		set(-5, 65, -6, bs("mcwholidays:pair_of_potions[facing=south]", "minecraft:air"));
		set(4, 65, -6, bs("mcwholidays:single_potion[facing=south]", "minecraft:air"));
		// стулья
		for (int x = -5; x <= 5; x += 2) set(x, 64, -5, bs("mcwfurnitures:dark_oak_stool_chair", "minecraft:dark_oak_trapdoor[half=bottom]"));
		// гирлянда вдоль всей стойки — провисает из выемки в потолке
		for (int x = -7; x <= 7; x++) set(x, 69, -6, ModBlocks.GARLAND.defaultBlockState()
				.setValue(ModBlocks.Garland.AXIS, Direction.Axis.X).setValue(ModBlocks.Garland.LIT, true));
		// подвесные лампы над стойкой
		for (int x : new int[]{-4, 0, 4}) {
			set(x, 68, -6, bs("minecraft:iron_chain[axis=y]", "minecraft:chain[axis=y]"));
			set(x, 67, -6, bs("mcwlights:tavern_lantern[hanging=true]", "minecraft:lantern[hanging=true]"));
		}

		// --- западная стена: диван, столики, стенд записок в гирлянде
		for (int z = -4; z <= 0; z++) setShaped(-9, 64, z, bs("mcwfurnitures:brown_couch[facing=east]", "minecraft:dark_oak_stairs[facing=west]"));
		for (int z : new int[]{-3, -1}) {
			setShaped(-7, 64, z, bs("mcwfurnitures:dark_oak_table", "minecraft:dark_oak_fence"));
			set(-6, 64, z, bs("mcwfurnitures:dark_oak_chair[facing=west]", "minecraft:dark_oak_stairs[facing=east]"));
			set(-7, 65, z, bs(z == -3 ? "mcwholidays:three_potions[facing=east]" : "minecraft:candle[candles=2,lit=true]", "minecraft:air"));
		}
		ModBlocks.placeNotes(l, new BlockPos(-9, 65, 0), Direction.EAST);
		for (int z = -4; z <= 1; z++) set(-9, 68, z, bs("mcwholidays:yellow_string_lights[facing=east,twinkle=true]", "minecraft:air"));
		for (int y = 65; y <= 67; y++) {
			set(-9, y, -4, bs("mcwholidays:yellow_string_lights[facing=east,twinkle=true]", "minecraft:air"));
			set(-9, y, 1, bs("mcwholidays:yellow_string_lights[facing=east,twinkle=true]", "minecraft:air"));
		}

		// --- восточная стена: витрины с лопатами, картина Тимофея, столики
		set(9, 65, -3, ModBlocks.SHOWCASE.defaultBlockState().setValue(ModBlocks.Facing.FACING, Direction.WEST)
				.setValue(ModBlocks.Showcase.EXHIBIT, ModBlocks.Exhibit.DEZHAVU));
		set(9, 65, 0, ModBlocks.SHOWCASE.defaultBlockState().setValue(ModBlocks.Facing.FACING, Direction.WEST)
				.setValue(ModBlocks.Showcase.EXHIBIT, ModBlocks.Exhibit.ANIME));
		for (int z : new int[]{-3, 0}) set(9, 67, z, bs("mcwlights:wall_lamp[facing=west,dye_color=yellow]", "minecraft:air"));
		for (int x : new int[]{5, 7}) {
			setShaped(x, 64, -1, bs("mcwfurnitures:dark_oak_table", "minecraft:dark_oak_fence"));
			set(x, 64, 0, bs("mcwfurnitures:dark_oak_modern_chair[facing=north]", "minecraft:dark_oak_stairs[facing=south]"));
			set(x, 64, -2, bs("mcwfurnitures:dark_oak_modern_chair[facing=south]", "minecraft:dark_oak_stairs[facing=north]"));
		}
		set(5, 65, -1, "minecraft:candle[candles=1,lit=true]");

		// --- у окон: столики на двоих
		for (int x : new int[]{-6, -2, 2}) {
			setShaped(x, 64, 1, bs("mcwfurnitures:dark_oak_table", "minecraft:dark_oak_fence"));
			set(x - 1, 64, 1, bs("mcwfurnitures:dark_oak_chair[facing=east]", "minecraft:dark_oak_stairs[facing=west]"));
			set(x + 1, 64, 1, bs("mcwfurnitures:dark_oak_chair[facing=west]", "minecraft:dark_oak_stairs[facing=east]"));
			set(x, 65, 1, "minecraft:candle[candles=1,lit=true]");
		}

		// --- люстры в зале и колонки по углам
		for (int x : new int[]{-4, 4}) set(x, 68, -1, bs("mcwlights:copper_small_chandelier", "minecraft:lantern[hanging=true]"));
		for (BlockPos p : SPEAKERS)
			set(p.getX(), p.getY(), p.getZ(), ModBlocks.SPEAKER.defaultBlockState()
					.setValue(ModBlocks.Facing.FACING, p.getZ() < -5 ? Direction.SOUTH : Direction.NORTH));
		// ковёр у входа
		fill(6, 63, 1, 8, 63, 2, "minecraft:red_wool");
	}

	private BlockState bottles(int i) {
		String[] k = {"mcwholidays:three_potions", "mcwholidays:pair_of_potions", "mcwholidays:single_potion", "mcwholidays:three_potions"};
		return bs(k[Math.floorMod(i * 7 + 3, k.length)] + "[facing=south]", "minecraft:air");
	}

	// ======================================================================= надписи, картина, бармен

	private void neon(net.minecraft.world.phys.Vec3 pos, Direction face, String text, String color, float scale) {
		Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		if (d == null) return;
		d.setPos(pos.x, pos.y, pos.z);
		d.setYRot(face.toYRot());
		d.addTag(TAG);
		d.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
		d.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		d.setBackgroundColor(0);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.setFlags(Display.TextDisplay.FLAG_SHADOW);
		d.setViewRange(2f);
		d.setText(Component.literal(text).withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.parseColor(color).getOrThrow()).withBold(true)));
		l.addFreshEntity(d);
	}

	private static void label(ServerLevel l, net.minecraft.world.phys.Vec3 pos, Direction face, Component text, float scale, int width) {
		Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		if (d == null) return;
		d.setPos(pos.x, pos.y, pos.z);
		d.setYRot(face.toYRot());
		d.addTag(TAG);
		d.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
		d.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		d.setBackgroundColor(0x00000000);
		d.setLineWidth(width);
		d.setViewRange(0.5f);
		d.setText(text);
		l.addFreshEntity(d);
	}

	/**
	 * Сущности хаба: таблички, картина, бармен. Надписи-неон ставит сборка; тут — то, что
	 * надо пересоздавать на каждом старте (бармен не сохраняется в мире, таблички тоже обновляем).
	 */
	static void spawnEntities(ServerLevel l, boolean fresh) {
		for (Entity e : new ArrayList<Entity>(l.getEntities(ModEntities.BARMAN, e -> true))) e.discard();
		BarmanEntity b = ModEntities.BARMAN.create(l, EntitySpawnReason.TRIGGERED);
		if (b != null) {
			b.snapTo(BARMAN.getX() + 0.5, BARMAN.getY(), BARMAN.getZ() + 0.5, 0f, 0f);
			b.setYHeadRot(0f);
			b.setYBodyRot(0f);
			b.addTag(TAG);
			l.addFreshEntity(b);
		}
		if (!fresh) return;
		// таблички к экспонатам (латунь)
		label(l, new net.minecraft.world.phys.Vec3(9.9, 64.55, -2.5), Direction.WEST,
				Component.literal("ЛОПАТА ДЕЖАВУ\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
						.append(Component.literal("«Говномесы», лимитированная серия.\nКаждый второй удар ×2").withStyle(ChatFormatting.GRAY)), 0.32f, 160);
		label(l, new net.minecraft.world.phys.Vec3(9.9, 64.55, 0.5), Direction.WEST,
				Component.literal("ЛОПАТА АНИМЕ-ПСИХА\n").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
						.append(Component.literal("«Говномесы», 1 экз. в мире.\nРуками не трогать").withStyle(ChatFormatting.GRAY)), 0.32f, 160);
		label(l, new net.minecraft.world.phys.Vec3(9.9, 64.85, -6.0), Direction.WEST,
				Component.literal("Говнозавр Тимофей. Портрет.\n").withStyle(ChatFormatting.GOLD)
						.append(Component.literal("холст, масло, говно").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)), 0.3f, 180);
		// табличка «Закрыто» на двери
		label(l, new net.minecraft.world.phys.Vec3(8.5, 65.6, 2.8), Direction.NORTH,
				Component.literal("ЗАКРЫТО").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), 0.5f, 100);
		// картина
		try {
			Holder<PaintingVariant> v = l.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT)
					.getOrThrow(ResourceKey.create(Registries.PAINTING_VARIANT, VadimNaTrone.id("timofey")));
			Painting p = new Painting(l, new BlockPos(9, 66, -6), Direction.WEST, v);
			p.addTag(TAG);
			l.addFreshEntity(p);
		} catch (Exception e) {
			VadimNaTrone.LOG.warn("Картина Тимофея не повесилась: {}", e.toString());
		}
	}

	/** Удалить старые таблички/картины хаба (перед пересборкой). */
	static void removeTagged(ServerLevel l) {
		List<Entity> old = new ArrayList<>();
		for (Entity e : l.getAllEntities()) if (e.getTags().contains(TAG)) old.add(e);
		old.forEach(Entity::discard);
	}

	// ======================================================================= сохранение версии

	public static final class Built extends SavedData {
		static final Codec<Built> CODEC = Codec.INT.xmap(Built::new, b -> b.version);
		static final SavedDataType<Built> TYPE = new SavedDataType<>("vadim_hub_built", Built::new, CODEC, null);
		int version;
		Built() { this(0); }
		Built(int v) { version = v; }
	}
}
