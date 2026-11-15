package ru.vadim.world;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.ZavodBlocks;
import ru.vadim.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Этап 3а — двор заброшенного завода «ПРОИЗВОДСТВО ГОВНА» (измерение vadim_na_trone:zavod, вечная ночь).
 * Всё строится кодом один раз на мир (и заново, если поменялась VERSION; /vadim rebuild zavod).
 *
 * Север — это -Z. Игроки появляются у ворот снаружи (0.5, 64, 0.5) и смотрят на север, на завод.
 *  - поверхность земли y=63, стоят на y=64;
 *  - забор: южная стена z=-2 (ворота x -3..3), боковые x=±32, северная z=-66;
 *  - КПП справа от ворот: x 6..11, z -4..-9, дверь на север (8,64,-9);
 *  - камера на столбе слева от ворот: (-6, 64..69, -6), поворачивается за ближайшим игроком;
 *  - цех: x -26..26, z -64..-42, стены y 64..81, крыша y 82; фасад на юг (z=-42), над воротами вывеска;
 *    главные ворота x -4..4 заварены, боковая дверь (16, 64, -42) на цепи (взлом — этап 3б);
 *  - снаружи у ворот — полоса x -12..12, z -1..7, дальше невидимая стена; за дорогой в тумане — город.
 */
public final class Zavod {
	/** Поменял планировку — увеличь, и при следующем запуске двор пересоберётся. */
	public static final int VERSION = 5;
	public static final String TAG = "vadim_zavod";
	public static final String CAM_TAG = "vadim_zavod_cam";
	public static final String LETTER_TAG = "vadim_zavod_letter";
	public static String versionTag() { return "vadim_zavod_v" + VERSION; }

	/** Цех: внутри — свой звук и (дальше) своя атмосфера. */
	public static final int SHOP_X0 = -26, SHOP_X1 = 26, SHOP_Z0 = -64, SHOP_Z1 = -42, SHOP_Y1 = 82;
	public static final BlockPos BOOTH_DOOR = new BlockPos(8, 64, -9);
	public static final BlockPos SIDE_DOOR = new BlockPos(16, 64, -42);
	/** Фонари двора (горят всегда) и фонарь, который сам включается и гаснет. */
	static final BlockPos[] YARD_LAMPS = {new BlockPos(-4, 68, -12), new BlockPos(-4, 68, -30), new BlockPos(4, 68, -21), new BlockPos(9, 68, -31)};
	static final BlockPos CYCLE_LAMP = new BlockPos(20, 68, -31);
	/** Лампы над вывеской (X), голова на y=79 в стене фасада. Последняя — над «НА», почти не работает и искрит. */
	static final int[] SIGN_LAMPS = {-10, -3, 4, 11};

	/** Камера на столбе: точка крепления (вокруг неё она крутится). */
	/** Цепь на боковой двери: высота и Z оси звеньев (проходит через петли замка). */
	static final double CHAIN_Y = 65.0, CHAIN_Z = SHOP_Z1 + 1 - 0.34 + 0.5;
	public static final Vec3 CAM_PIVOT = new Vec3(-5.5, 70.25, -5.5);

	private final ServerLevel l;
	private final RandomSource rnd = RandomSource.create(2077);
	private final Map<String, BlockState> cache = new HashMap<>();
	private final List<BlockPos> reshape = new ArrayList<>();

	private Zavod(ServerLevel l) { this.l = l; }

	// ======================================================================= сборка

	public static void ensure(ServerLevel z, boolean force) {
		// двор и цех всегда загружены: камеры, лампы, железка и сущности взлома живут, даже если никого рядом
		for (int cx = -2; cx <= 1; cx++) for (int cz = -5; cz <= -1; cz++) z.setChunkForced(cx, cz, true);
		Built st = z.getDataStorage().computeIfAbsent(Built.TYPE);
		if (force || st.version != VERSION) {
			long t0 = System.currentTimeMillis();
			removeTagged(z);
			Zavod b = new Zavod(z);
			b.clear();
			b.ground();
			b.fence();
			b.booth();
			b.shop();
			ZavodInside.build(z);
			b.yard();
			b.city();
			b.barriers();
			b.fixShapes();
			spawnStatic(z);
			st.version = VERSION;
			st.setDirty();
			VadimNaTrone.LOG.info("Двор завода построен за {} мс", System.currentTimeMillis() - t0);
		}
		spawnCamera(z);
		lampsInit = false;
		ZavodDoor.setup(z);
		ZavodInside.setup(z);
	}

	// ======================================================================= блоки

	private BlockState bs(String spec, String fallback) {
		String key = spec + "|" + fallback;
		BlockState c = cache.get(key);
		if (c != null) return c;
		BlockState s = parse(spec);
		if (s == null && fallback != null) {
			VadimNaTrone.LOG.warn("Завод: блок {} не встал, ставлю замену {}", spec, fallback);
			s = parse(fallback);
		}
		if (s == null) {
			VadimNaTrone.LOG.warn("Завод: нет блока {} (и замены {})", spec, fallback);
			s = Blocks.AIR.defaultBlockState();
		}
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

	/** Случайный блок из списка «вес:блок». */
	private BlockState pick(String... weighted) {
		int total = 0;
		for (String w : weighted) total += Integer.parseInt(w.substring(0, w.indexOf(':')));
		int r = rnd.nextInt(total);
		for (String w : weighted) {
			int i = w.indexOf(':');
			r -= Integer.parseInt(w.substring(0, i));
			if (r < 0) return bs(w.substring(i + 1));
		}
		return Blocks.AIR.defaultBlockState();
	}

	private void fixShapes() {
		for (int pass = 0; pass < 2; pass++)
			for (BlockPos p : reshape) {
				BlockState s = l.getBlockState(p);
				BlockState n = Block.updateFromNeighbourShapes(s, l, p);
				if (n != s) l.setBlock(p, n, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
	}

	private void clear() {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = -96; x <= 96; x++)
			for (int z = -84; z <= 72; z++)
				for (int y = 54; y <= 104; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!l.getBlockState(p).isAir()) l.setBlock(p, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
	}

	// ======================================================================= земля

	private static boolean inFence(int x, int z) { return x > -32 && x < 32 && z < -2 && z > -66; }

	private void ground() {
		fill(-90, 62, -80, 90, 62, 68, "minecraft:stone");
		for (int x = -90; x <= 90; x++)
			for (int z = -80; z <= 68; z++) {
				BlockState s;
				if (inFence(x, z)) {
					if (Math.abs(x) <= 2 && z <= -3 && z >= -41)   // бетонная дорожка к цеху, в трещинах
						s = pick("10:minecraft:smooth_stone", "4:minecraft:polished_andesite", "2:minecraft:gravel", "1:minecraft:tuff");
					else
						s = pick("6:minecraft:coarse_dirt", "4:minecraft:gravel", "4:minecraft:andesite", "3:minecraft:packed_mud",
								"2:minecraft:tuff", "2:minecraft:mud", "1:minecraft:cobblestone");
				} else if (z >= 10 && z <= 16) {   // дорога вдоль забора
					int r = rnd.nextInt(10);
					s = bs(r < 7 ? "minecraft:black_concrete" : r < 9 ? "minecraft:gray_concrete_powder" : "minecraft:blackstone");
				} else {
					s = pick("6:minecraft:coarse_dirt", "3:minecraft:dirt", "2:minecraft:gravel", "2:minecraft:podzol", "1:minecraft:packed_mud");
				}
				set(x, 63, z, s);
			}
		// разметка на дороге и обочина
		for (int x = -90; x <= 90; x++) if (Math.floorMod(x, 6) < 3 && rnd.nextInt(5) > 0) set(x, 63, 13, "minecraft:white_concrete");
		// сухая трава и кусты снаружи
		for (int i = 0; i < 520; i++) {
			int x = rnd.nextInt(181) - 90, z = rnd.nextInt(149) - 80;
			if (inFence(x, z) || (z >= 9 && z <= 17) || (Math.abs(x) <= 12 && z >= -2 && z <= 8)) continue;
			if (Math.abs(x) <= 33 && z >= -67 && z <= -1) continue;
			set(x, 64, z, pick("4:minecraft:short_dry_grass", "2:minecraft:tall_dry_grass", "2:minecraft:dead_bush"));
		}
	}

	// ======================================================================= забор

	private void fencePanel(int x, int z, boolean post) {
		for (int y = 64; y <= 66; y++)
			set(x, y, z, post ? bs("minecraft:polished_andesite")
					: pick("10:minecraft:light_gray_concrete", "3:minecraft:smooth_stone", "2:minecraft:tuff", "2:minecraft:cracked_stone_bricks", "1:minecraft:andesite"));
		setShaped(x, 67, z, bs("minecraft:iron_bars"));   // колючка
		if (rnd.nextInt(14) == 0) set(x, 68, z, "minecraft:cobweb");
	}

	private void fence() {
		for (int x = -32; x <= 32; x++) {
			if (Math.abs(x) > 3) fencePanel(x, -2, Math.floorMod(x, 4) == 0);
			fencePanel(x, -66, Math.floorMod(x, 4) == 0);
		}
		for (int z = -65; z <= -3; z++) {
			fencePanel(-32, z, Math.floorMod(z, 4) == 0);
			fencePanel(32, z, Math.floorMod(z, 4) == 0);
		}
		// столбы ворот
		for (int sx : new int[]{-4, 4}) {
			fill(sx, 64, -2, sx, 68, -2, "minecraft:polished_deepslate");
			set(sx, 69, -2, "minecraft:polished_deepslate_slab[type=bottom]");
		}
		// створка ворот распахнута внутрь и прижата к забору (решётка), вторая сорвана и лежит
		for (int x = -10; x <= -5; x++) for (int y = 64; y <= 66; y++) setShaped(x, y, -3, bs("minecraft:iron_bars"));
		for (int x = -9; x <= -6; x++) setShaped(x, 64, -4, bs("minecraft:iron_bars"));
		// шлагбаум: тумба у КПП (стрела — сущности, см. spawnStatic)
		set(5, 64, -4, "minecraft:polished_blackstone_bricks");
		set(5, 65, -4, "minecraft:polished_blackstone_slab[type=bottom]");
	}

	// ======================================================================= КПП

	private void booth() {
		int x0 = 6, x1 = 11, z0 = -9, z1 = -4;
		fill(x0, 63, z0, x1, 63, z1, "minecraft:smooth_stone");
		for (int x = x0; x <= x1; x++)
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (!edge) { fill(x, 64, z, x, 66, z, Blocks.AIR.defaultBlockState()); continue; }
				for (int y = 64; y <= 66; y++)
					set(x, y, z, pick("8:minecraft:light_gray_concrete", "3:minecraft:white_terracotta", "2:minecraft:smooth_stone", "1:minecraft:cracked_stone_bricks"));
			}
		// окна: на ворота (запад) и на улицу (юг) — стёкла грязные, одно выбито
		for (int z = -8; z <= -5; z++) setShaped(x0, 65, z, z == -6 ? Blocks.AIR.defaultBlockState() : bs("minecraft:light_gray_stained_glass_pane"));
		for (int x = 7; x <= 10; x++) setShaped(x, 65, z1, x == 9 ? bs("minecraft:glass_pane") : bs("minecraft:light_gray_stained_glass_pane"));
		// дверь на север, во двор (её можно открыть)
		set(BOOTH_DOOR.getX(), 64, z0, bs("minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]"));
		set(BOOTH_DOOR.getX(), 65, z0, bs("minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false]"));
		// крыша с козырьком
		fill(x0 - 1, 67, z0 - 1, x1 + 1, 67, z1 + 1, "minecraft:smooth_stone_slab[type=bottom]");
		// внутри: стол охранника, стул, тумбочка, лампа, радиатор, мусор
		// стол у южного окна (у столов Macaw's facing только north/east — это ось стола)
		set(9, 64, -5, bs("mcwfurnitures:oak_desk[facing=north]", "minecraft:spruce_slab[type=top]"));
		set(10, 64, -5, bs("mcwfurnitures:oak_desk[facing=north]", "minecraft:spruce_slab[type=top]"));
		reshape.add(new BlockPos(9, 64, -5));
		reshape.add(new BlockPos(10, 64, -5));
		set(9, 64, -6, bs("mcwfurnitures:oak_modern_chair[facing=north]", "minecraft:spruce_stairs[facing=south]"));
		set(10, 64, -8, bs("mcwfurnitures:spruce_drawer[facing=west]", "minecraft:barrel[facing=up]"));
		set(10, 65, -8, "minecraft:flower_pot");
		set(8, 66, -6, "minecraft:lantern[hanging=true]");
		set(7, 64, -8, "minecraft:cauldron");
		set(8, 64, -7, "minecraft:white_carpet");
		set(7, 64, -5, "minecraft:cobweb");
	}

	// ======================================================================= цех

	private BlockState brick() {
		return pick("16:minecraft:bricks", "3:minecraft:mud_bricks", "2:minecraft:packed_mud", "2:minecraft:polished_granite",
				"1:minecraft:terracotta", "1:minecraft:cracked_stone_bricks");
	}

	private void shop() {
		int x0 = SHOP_X0, x1 = SHOP_X1, z0 = SHOP_Z0, z1 = SHOP_Z1, y1 = 81;
		fill(x0, 63, z0, x1, 63, z1, "minecraft:polished_andesite");
		for (int x = x0; x <= x1; x++)
			for (int z = z0; z <= z1; z++) {
				boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
				for (int y = 64; y <= y1; y++) {
					if (!wall) continue;
					BlockState s = brick();
					if (y == 64) s = pick("3:minecraft:stone_bricks", "1:minecraft:cracked_stone_bricks", "1:minecraft:mossy_stone_bricks");
					else if (y == 72 || y == 81) s = pick("4:minecraft:smooth_stone", "1:minecraft:tuff");
					set(x, y, z, s);
				}
			}
		// крыша и парапет
		fill(x0, 82, z0, x1, 82, z1, "minecraft:gray_concrete");
		for (int x = x0; x <= x1; x++) { set(x, 83, z0, brick()); set(x, 83, z1, brick()); }
		for (int z = z0; z <= z1; z++) { set(x0, 83, z, brick()); set(x1, 83, z, brick()); }
		// высокая кирпичная труба — силуэт в тумане
		for (int y = 83; y <= 104; y++)
			for (int dx = -1; dx <= 1; dx++)
				for (int dz = -1; dz <= 1; dz++)
					if (dx != 0 || dz != 0) set(-18 + dx, y, -57 + dz, y >= 101 ? bs("minecraft:polished_blackstone_bricks") : brick());

		// пилястры по фасаду (выступают на блок)
		for (int px : new int[]{-26, -18, 18, 26})
			for (int y = 64; y <= 81; y++) set(px, y, z1 + 1, brick());

		// окна: верхний ряд по всем стенам, кроме центра фасада (там вывеска); нижние — заколочены
		windowsAlongX(z1, true);
		windowsAlongX(z0, false);
		for (int z = z0 + 3; z <= z1 - 3; z += 4) {
			window(x0, z, true);
			window(x1, z, true);
		}

		// главные ворота x -4..4, y 64..71: заварены листами, по центру шов
		fill(-5, 64, z1, -5, 72, z1, "minecraft:polished_blackstone");
		fill(5, 64, z1, 5, 72, z1, "minecraft:polished_blackstone");
		fill(-5, 72, z1, 5, 72, z1, "minecraft:polished_blackstone");
		for (int x = -4; x <= 4; x++)
			for (int y = 64; y <= 71; y++) {
				BlockState s = pick("8:minecraft:iron_block", "4:minecraft:waxed_exposed_copper", "2:minecraft:waxed_weathered_copper", "1:minecraft:waxed_exposed_cut_copper");
				if (x == 0) s = bs("minecraft:polished_basalt[axis=y]");
				if (y == 67 || y == 70) s = bs("minecraft:polished_basalt[axis=x]");
				set(x, y, z1, s);
			}
		// следы под вывеской: буквы «В» отвалились — на стене остались чистые пятна (см. spawnStatic)
		for (float[] g : letterSlots()) {
			if (g[0] != 'В') continue;
			int bx = Mth.floor(g[1]);
			set(bx, 75, z1, "minecraft:mud_bricks");
			set(bx, 76, z1, "minecraft:mud_bricks");
		}

		// боковая дверь (16, 64, -42) — металлическая, на цепи; над ней фонарь
		int dx = SIDE_DOOR.getX();
		fill(dx - 1, 64, z1, dx - 1, 66, z1, "minecraft:polished_blackstone");
		fill(dx + 1, 64, z1, dx + 1, 66, z1, "minecraft:polished_blackstone");
		set(dx, 66, z1, "minecraft:polished_blackstone");
		set(dx, 64, z1, bs("mcwdoors:metal_warning_door[facing=south,half=lower,hinge=left,open=false]",
				"minecraft:iron_door[facing=south,half=lower,hinge=left,open=false]"));
		set(dx, 65, z1, bs("mcwdoors:metal_warning_door[facing=south,half=upper,hinge=left,open=false]",
				"minecraft:iron_door[facing=south,half=upper,hinge=left,open=false]"));
		// над дверью — «кобра» из стены (цепь с замком на двери — сущность, см. spawnStatic)
		set(dx, 67, z1, ZavodBlocks.LAMP_HEAD.defaultBlockState().setValue(ZavodBlocks.Deco.FACING, Direction.SOUTH));
		// лампы над вывеской: торчат из стены и светят вниз на буквы (мигают — см. tick)
		for (int sx : SIGN_LAMPS)
			set(sx, 79, z1, ZavodBlocks.LAMP_HEAD.defaultBlockState().setValue(ZavodBlocks.Deco.FACING, Direction.SOUTH));
		// столбики по бокам двери — на них натянута цепь (сама цепь — сущности, см. spawnStatic)
		for (int px : new int[]{dx - 1, dx + 1}) {
			set(px, 64, z1 + 1, "minecraft:polished_blackstone_wall");
			set(px, 65, z1 + 1, "minecraft:polished_blackstone_wall");
			reshape.add(new BlockPos(px, 64, z1 + 1));
			reshape.add(new BlockPos(px, 65, z1 + 1));
		}
		// под дверью — ступенька и мусор
		set(dx, 63, z1 + 1, "minecraft:smooth_stone");
		set(dx + 3, 64, z1 + 1, "minecraft:barrel[facing=up]");
	}

	private void windowsAlongX(int z, boolean facade) {
		for (int x = SHOP_X0 + 3; x <= SHOP_X1 - 3; x += 4) {
			if (facade && Math.abs(x) <= 15) continue;   // центр фасада: ворота и вывеска
			if (facade && (x == -18 || x == 18)) continue;
			window(x, z, false);
			if (facade && Math.abs(x - SIDE_DOOR.getX()) <= 1) continue;
			boarded(x, z);
		}
	}

	/** Высокое окно цеха (2×5): стекло, частично выбито, местами решётка. */
	private void window(int a, int b, boolean alongZ) {
		for (int i = 0; i < 2; i++)
			for (int y = 74; y <= 78; y++) {
				int x = alongZ ? a : a + i, z = alongZ ? b + i : b;
				int r = rnd.nextInt(10);
				BlockState s = r < 3 ? Blocks.AIR.defaultBlockState()
						: r < 6 ? bs("minecraft:gray_stained_glass_pane")
						: r < 8 ? bs("minecraft:glass_pane") : bs("minecraft:iron_bars");
				setShaped(x, y, z, s);
			}
	}

	/** Нижнее окно, заколоченное досками. */
	private void boarded(int a, int z) {
		for (int i = 0; i < 2; i++)
			for (int y = 66; y <= 68; y++)
				set(a + i, y, z, y == 67 ? bs("minecraft:dark_oak_planks") : pick("3:minecraft:spruce_planks", "1:minecraft:air"));
	}

	// ======================================================================= двор

	private void yard() {
		// цистерна лёжа (ось Z), ржавая, на опорах
		int cx = -19, cy = 67, r = 3;
		for (int z = -26; z <= -14; z++)
			for (int x = cx - r; x <= cx + r; x++)
				for (int y = cy - r; y <= cy + r; y++) {
					double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
					boolean cap = z == -26 || z == -14;
					if (d <= r + 0.3 && (d >= r - 0.9 || cap))
						set(x, y, z, pick("5:minecraft:waxed_exposed_copper", "3:minecraft:waxed_weathered_copper", "3:minecraft:orange_terracotta",
								"2:minecraft:brown_terracotta", "1:minecraft:waxed_oxidized_copper"));
				}
		for (int z : new int[]{-24, -16}) fill(cx - 2, 64, z, cx + 2, 64, z, "minecraft:polished_blackstone_bricks");
		// труба от цистерны в стену цеха, на стойках
		for (int z = -41; z <= -27; z++) set(cx, 70, z, bs("minecraft:waxed_weathered_cut_copper"));
		set(cx, 70, -26, bs("minecraft:waxed_weathered_cut_copper"));
		for (int z : new int[]{-30, -35, -40}) fill(cx, 64, z, cx, 69, z, "minecraft:iron_bars");

		// стоячий бак с лестницей
		int tx = -12, tz = -32;
		for (int y = 64; y <= 73; y++)
			for (int x = tx - 3; x <= tx + 3; x++)
				for (int z = tz - 3; z <= tz + 3; z++) {
					double d = Math.sqrt((x - tx) * (x - tx) + (z - tz) * (z - tz));
					double rr = y >= 72 ? 2.6 - (y - 72) * 1.2 : 2.6;
					if (d <= rr && (d >= rr - 0.9 || y >= 72))
						set(x, y, z, pick("6:minecraft:light_gray_terracotta", "3:minecraft:white_terracotta", "2:minecraft:orange_terracotta", "1:minecraft:brown_terracotta"));
				}
		for (int y = 64; y <= 71; y++) set(tx + 3, y, tz, "minecraft:ladder[facing=east]");

		// козловой кран через двор: две опоры и балка, жёлтый с ржавчиной, крюк на цепи
		for (int lx : new int[]{11, 23})
			for (int y = 64; y <= 76; y++)
				for (int ddx = 0; ddx <= 1; ddx++)
					for (int dz = -19; dz <= -18; dz++)
						set(lx + ddx, y, dz, y % 4 == 0 ? bs("minecraft:black_concrete")
								: pick("5:minecraft:yellow_terracotta", "2:minecraft:orange_terracotta", "1:minecraft:brown_terracotta"));
		for (int x = 10; x <= 25; x++)
			for (int y = 77; y <= 78; y++)
				for (int dz = -19; dz <= -18; dz++) set(x, y, dz, pick("5:minecraft:yellow_terracotta", "2:minecraft:orange_terracotta", "1:minecraft:brown_terracotta"));
		set(17, 76, -18, "minecraft:iron_block");
		for (int y = 70; y <= 75; y++) set(17, y, -18, bs("minecraft:iron_chain[axis=y]", "minecraft:chain[axis=y]"));
		set(17, 69, -18, "minecraft:heavy_core");

		// фонари вдоль дорожки и у крана; один у бочек «моргает» (включается, горит, гаснет); один сломан
		for (BlockPos p : YARD_LAMPS) streetLamp(p.getX(), p.getZ(), p.getX() < 0 ? Direction.EAST : Direction.WEST, true);
		streetLamp(CYCLE_LAMP.getX(), CYCLE_LAMP.getZ(), Direction.WEST, false);
		brokenLamp(4, -36);

		// сгоревшая машина у западного забора
		fill(-27, 64, -8, -24, 64, -6, "minecraft:coal_block");
		fill(-26, 65, -8, -25, 65, -6, "minecraft:black_concrete");
		set(-27, 64, -9, "minecraft:polished_basalt[axis=x]");
		set(-24, 64, -9, "minecraft:polished_basalt[axis=x]");
		set(-27, 64, -5, "minecraft:polished_basalt[axis=x]");
		set(-24, 64, -5, "minecraft:polished_basalt[axis=x]");

		// бочки, поддоны, кучи щебня и песка, паутина
		int[][] barrels = {{14, -28}, {15, -28}, {14, -29}, {-6, -36}, {20, -8}, {21, -8}, {20, -9}};
		for (int[] b : barrels) set(b[0], 64, b[1], rnd.nextBoolean() ? "minecraft:barrel[facing=up]" : "minecraft:barrel[facing=north]");
		set(14, 65, -28, "minecraft:barrel[facing=up]");
		int[][] pallets = {{8, -24}, {9, -24}, {8, -25}, {-8, -14}, {-9, -14}, {24, -36}};
		for (int[] p : pallets) set(p[0], 64, p[1], "minecraft:spruce_slab[type=bottom]");
		set(8, 65, -24, "minecraft:spruce_slab[type=bottom]");
		pile(-25, -40, 3, "minecraft:gravel");
		pile(26, -12, 2, "minecraft:cobblestone");
		pile(6, -38, 2, "minecraft:gravel");
		for (int i = 0; i < 90; i++) {
			int x = rnd.nextInt(62) - 31, z = -3 - rnd.nextInt(38);
			if (Math.abs(x) <= 3 || !l.getBlockState(new BlockPos(x, 64, z)).isAir()) continue;
			if (x >= 5 && x <= 12 && z <= -3 && z >= -10) continue;   // у КПП и шлагбаума чисто
			int k = rnd.nextInt(10);
			String s = k < 4 ? "minecraft:short_dry_grass" : k < 6 ? "minecraft:dead_bush" : k < 8 ? "minecraft:cobblestone_slab[type=bottom]" : "minecraft:tall_dry_grass";
			set(x, 64, z, s);
		}
	}

	private void pile(int x0, int z0, int r, String spec) {
		for (int x = x0 - r; x <= x0 + r; x++)
			for (int z = z0 - r; z <= z0 + r; z++) {
				int d = Math.abs(x - x0) + Math.abs(z - z0);
				int h = r - d;
				for (int y = 0; y < h; y++) set(x, 64 + y, z, spec);
			}
	}

	/** Бетонный столб с «коброй» (из Стрипухоеда): столб y 64..66, верх 67, голова 68, плафон смотрит в face. */
	private void streetLamp(int x, int z, Direction face, boolean lit) {
		for (int y = 64; y <= 66; y++) set(x, y, z, ZavodBlocks.LAMP_POLE.defaultBlockState().setValue(ZavodBlocks.Deco.FACING, face));
		set(x, 67, z, ZavodBlocks.LAMP_POLE_TOP.defaultBlockState().setValue(ZavodBlocks.Deco.FACING, face));
		set(x, 68, z, ZavodBlocks.LAMP_HEAD.defaultBlockState().setValue(ZavodBlocks.Deco.FACING, face).setValue(ZavodBlocks.LampHead.LIT, lit));
	}

	/** Столб без головы — фонарь сломан. */
	private void brokenLamp(int x, int z) {
		for (int y = 64; y <= 65; y++) set(x, y, z, ZavodBlocks.LAMP_POLE.defaultBlockState());
		set(x, 66, z, ZavodBlocks.LAMP_POLE_TOP.defaultBlockState());
	}

	// ======================================================================= город вдали

	private void city() {
		RandomSource cr = RandomSource.create(404);
		// ряд домов за дорогой на юге и вдоль восточного края — в тумане видно только окна
		List<int[]> lots = new ArrayList<>();
		for (int x = -84; x <= 70; x += 14 + cr.nextInt(6)) lots.add(new int[]{x, 30 + cr.nextInt(10), 9 + cr.nextInt(5), 8 + cr.nextInt(5), 10 + cr.nextInt(18)});
		for (int z = -70; z <= 20; z += 15 + cr.nextInt(6)) lots.add(new int[]{52 + cr.nextInt(8), z, 9 + cr.nextInt(4), 10 + cr.nextInt(4), 12 + cr.nextInt(16)});
		for (int z = -74; z <= 0; z += 18 + cr.nextInt(6)) lots.add(new int[]{-66 - cr.nextInt(10), z, 10, 10, 8 + cr.nextInt(12)});
		String[] walls = {"minecraft:gray_concrete", "minecraft:black_concrete", "minecraft:deepslate_tiles", "minecraft:polished_deepslate", "minecraft:gray_terracotta"};
		String[] lit = {"minecraft:ochre_froglight", "minecraft:pearlescent_froglight", "minecraft:glowstone", "minecraft:shroomlight"};
		for (int[] b : lots) {
			int bx = b[0], bz = b[1], w = b[2], d = b[3], h = b[4];
			String wall = walls[cr.nextInt(walls.length)];
			for (int x = bx; x < bx + w; x++)
				for (int z = bz; z < bz + d; z++)
					for (int y = 64; y < 64 + h; y++) {
						boolean shell = x == bx || x == bx + w - 1 || z == bz || z == bz + d - 1;
						if (!shell && y < 63 + h) continue;
						boolean corner = (x == bx || x == bx + w - 1) && (z == bz || z == bz + d - 1);
						boolean win = shell && !corner && y < 63 + h && (y - 64) % 3 == 1 && Math.floorMod(x + z, 2) == 0;
						if (win) {
							set(x, y, z, cr.nextInt(100) < 22 ? lit[cr.nextInt(lit.length)] : "minecraft:black_stained_glass");
						} else {
							set(x, y, z, wall);
						}
					}
		}
		// фонари вдоль дороги за забором (снаружи)
		for (int x = -60; x <= 60; x += 30) streetLamp(x, 18, Direction.NORTH, true);
	}

	// ======================================================================= невидимые стены

	private void barriers() {
		BlockState b = Blocks.BARRIER.defaultBlockState();
		for (int x = -12; x <= 12; x++) fill(x, 64, 8, x, 67, 8, b);
		for (int z = -1; z <= 8; z++) { fill(-12, 64, z, -12, 67, z, b); fill(12, 64, z, 12, 67, z, b); }
		// поверх забора — чтобы не перепрыгнуть с бочек/куч
		for (int x = -32; x <= 32; x++) { if (Math.abs(x) > 3) set(x, 68, -2, b); set(x, 68, -66, b); set(x, 69, -66, b); }
		for (int z = -66; z <= -2; z++) { set(-32, 68, z, b); set(32, 68, z, b); set(-32, 69, z, b); set(32, 69, z, b); }
		// с крана и цистерны на стену не залезть: они далеко от забора; на крышу КПП — можно, это не страшно
	}

	// ======================================================================= сущности: вывеска, шлагбаум

	private static final String WORD = "ПРОИЗВОДСТВО ГОВНА";

	/** Буквы вывески и их X (центр буквы): шаг 1.5, пробел 1.2, по центру фасада. */
	static List<float[]> letterSlots() {
		float step = 1.5f, space = 1.2f, width = 0;
		for (char c : WORD.toCharArray()) width += c == ' ' ? space : step;
		float x = -width / 2f + step / 2f;
		List<float[]> out = new ArrayList<>();
		for (char c : WORD.toCharArray()) {
			if (c == ' ') { x += space; continue; }
			out.add(new float[]{c, x});
			x += step;
		}
		return out;
	}

	private static String letterId(char c) {
		return switch (c) {
			case 'П' -> "p"; case 'Р' -> "r"; case 'О' -> "o"; case 'И' -> "i"; case 'З' -> "z"; case 'В' -> "v";
			case 'Д' -> "d"; case 'С' -> "s"; case 'Т' -> "t"; case 'Г' -> "g"; case 'Н' -> "n"; case 'А' -> "a";
			default -> null;
		};
	}

	private static Display.ItemDisplay letter(ServerLevel l, char c, Vec3 pos, Quaternionf rot, float scale, int light) {
		Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		if (d == null) return null;
		ItemStack st = new ItemStack(Items.PAPER);
		st.set(DataComponents.ITEM_MODEL, VadimNaTrone.id("zavod_letter_" + letterId(c)));
		d.setItemStack(st);
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setPos(pos.x, pos.y, pos.z);
		d.setTransformation(new Transformation(null, rot, new Vector3f(scale, scale, scale), null));
		d.setBrightnessOverride(new Brightness(light, 0));
		d.setViewRange(2f);
		d.addTag(TAG);
		d.addTag(versionTag());
		l.addFreshEntity(d);
		return d;
	}

	/** Буква на стене: модель предмета смотрит лицом на -Z, разворачиваем к улице (+Z) и наклоняем. */
	private static Quaternionf wallRot(float tiltDeg) {
		return new Quaternionf().rotateZ((float) Math.toRadians(tiltDeg)).rotateY((float) Math.PI);
	}

	private static void block(ServerLevel l, Vec3 pivot, String tag, BlockState s, Matrix4f m, Brightness br) {
		Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		if (d == null) return;
		d.setBlockState(s);
		d.setPos(pivot.x, pivot.y, pivot.z);
		d.setTransformation(new Transformation(m));
		if (br != null) d.setBrightnessOverride(br);
		d.setViewRange(2f);
		d.addTag(tag);
		d.addTag(TAG);
		d.addTag(versionTag());
		l.addFreshEntity(d);
	}

	/** Вывеска над воротами, упавшие «В», стрела шлагбаума. Сохраняются в мире; при новой версии — пересоздаются. */
	static void spawnStatic(ServerLevel l) {
		// вывеска: по центру фасада, низ букв на y≈75.1; все «В» отвалились
		double z = SHOP_Z1 + 1 + 0.12;
		float scale = 2.2f;
		for (float[] g : letterSlots()) {
			char c = (char) g[0];
			if (c == 'В') continue;
			float tilt = 0;
			double y = 76.2, xx = g[1];
			if (c == 'Н') { tilt = -12; y -= 0.15; }
			if (c == 'А') { tilt = 28; y -= 0.45; xx += 0.15; }
			Display.ItemDisplay d = letter(l, c, new Vec3(xx, y, z), wallRot(tilt), scale, 3);
			if (d != null) { d.addTag(LETTER_TAG); track(d); }
		}
		// упавшие «В»: две лежат у стены, одна прислонена
		// (лёжа: лицом вверх — поворот X на +90°; прислонённая: лицом на юг и чуть вверх)
		letter(l, 'В', new Vec3(-9.4, 64.08, -39.6), new Quaternionf().rotateY(0.6f).rotateX((float) Math.toRadians(90)), scale, 6);
		letter(l, 'В', new Vec3(3.6, 64.08, -37.9), new Quaternionf().rotateY(-1.9f).rotateX((float) Math.toRadians(90)), scale, 6);
		letter(l, 'В', new Vec3(11.2, 65.05, -40.75), new Quaternionf().rotateZ(0.12f).rotateX((float) Math.toRadians(-14)).rotateY((float) Math.PI), scale, 6);

		// цепь с навесным замком на боковой двери (модель из Стрипухоеда, замком на улицу)
		Display.BlockDisplay ch = EntityType.BLOCK_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		if (ch != null) {
			ch.setBlockState(ZavodBlocks.CHAIN.defaultBlockState().setValue(ZavodBlocks.Deco.FACING, Direction.SOUTH));
			ch.setPos(SIDE_DOOR.getX(), 64.6, SHOP_Z1 + 1 - 0.34);
			ch.setViewRange(1f);
			ch.addTag(TAG);
			ch.addTag(versionTag());
			ch.addTag("vadim_zavod_chain");
			l.addFreshEntity(ch);
		}
		// сама цепь: от столбика слева через замок к столбику справа (звенья — ванильная цепь, по блоку на кусок)
		for (int i = 0; i < 2; i++) {
			Matrix4f m = new Matrix4f().translate(i, -0.5f, -0.5f);
			block(l, new Vec3(SIDE_DOOR.getX() - 0.5, CHAIN_Y, CHAIN_Z), "vadim_zavod_chain",
					Blocks.IRON_CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS, Direction.Axis.X), m, null);
		}

		// стрела шлагбаума: от тумбы (5,65,-4) через ворота на запад, задрана вверх и застряла (~70°)
		Vec3 pivot = new Vec3(5.5, 65.55, -3.5);
		float ang = (float) Math.toRadians(-70);
		for (int i = 0; i < 6; i++) {
			Matrix4f m = new Matrix4f().rotateZ(ang).translate(-(i + 1), -0.09f, -0.09f).scale(1f, 0.18f, 0.18f);
			block(l, pivot, TAG, (i % 2 == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE).defaultBlockState(), m, null);
		}
	}

	static void removeTagged(ServerLevel l) {
		List<Entity> old = new ArrayList<>();
		for (Entity e : l.getAllEntities())
			if (e.getTags().contains(TAG) || e.getTags().contains(CAM_TAG) || e.getTags().contains("vadim_zavod_stub")) old.add(e);
		old.forEach(Entity::discard);
	}

	// ======================================================================= камера на столбе

	/** Части камеры: [x0,y0,z0, sx,sy,sz] в локальных координатах (смотрит в -Z), блок, светится ли. */
	private record Part(float x, float y, float z, float sx, float sy, float sz, BlockState block, boolean glow) {}

	/** Камера: точка крепления, части, огоньки, куда смотрит; idle — направление «осмотра», когда никого нет. */
	private static final class Cam {
		final Vec3 pivot;
		final float idle;
		final List<Display.BlockDisplay> parts = new ArrayList<>(), leds = new ArrayList<>();
		float yaw, pitch = -20f;
		Cam(Vec3 pivot, float idle) { this.pivot = pivot; this.idle = idle; this.yaw = idle; }
	}

	private static final List<Cam> cams = new ArrayList<>();
	private static int camTick = 0;

	private static List<Part> camParts() {
		return List.of(
				new Part(-0.16f, -0.14f, -0.62f, 0.32f, 0.28f, 0.62f, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), false),   // корпус
				new Part(-0.19f, 0.14f, -0.72f, 0.38f, 0.04f, 0.74f, Blocks.GRAY_CONCRETE.defaultBlockState(), false),          // козырёк
				new Part(-0.10f, -0.09f, -0.66f, 0.20f, 0.18f, 0.05f, Blocks.BLACK_CONCRETE.defaultBlockState(), false),        // объектив
				new Part(-0.05f, -0.04f, -0.67f, 0.10f, 0.08f, 0.02f, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), true),// блик стекла
				new Part(0.04f, 0.18f, -0.30f, 0.10f, 0.08f, 0.10f, Blocks.RED_CONCRETE.defaultBlockState(), true),           // огонёк записи (сверху)
				new Part(-0.05f, -0.30f, -0.05f, 0.10f, 0.20f, 0.10f, Blocks.GRAY_CONCRETE.defaultBlockState(), false),         // шарнир
				new Part(0.07f, 0.04f, -0.70f, 0.07f, 0.07f, 0.04f, Blocks.RED_CONCRETE.defaultBlockState(), true));           // огонёк записи (спереди)
	}

	private static Matrix4f camMatrix(Part p, float yaw, float pitch) {
		return new Matrix4f().rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch))
				.translate(p.x, p.y, p.z).scale(p.sx, p.sy, p.sz);
	}

	/** Все камеры: во дворе на столбе + внутри цеха (ZavodInside.CAMS). */
	static void spawnCamera(ServerLevel l) {
		List<Entity> old = new ArrayList<>();
		for (Entity e : l.getAllEntities()) if (e.getTags().contains(CAM_TAG)) old.add(e);
		old.forEach(Entity::discard);
		cams.clear();
		// столб во дворе
		for (int y = 64; y <= 69; y++) l.setBlock(new BlockPos(-6, y, -6), Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_CLIENTS);
		l.setBlock(new BlockPos(-6, 70, -6), Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState(), Block.UPDATE_CLIENTS);
		addCam(l, new Cam(CAM_PIVOT, 30f));
		for (float[] c : ZavodInside.CAMS) addCam(l, new Cam(new Vec3(c[0], c[1], c[2]), c[3]));
	}

	private static void addCam(ServerLevel l, Cam c) {
		cams.add(c);   // до сущностей: ENTITY_LOAD удаляет части камер, которых нет в списке
		List<Part> parts = camParts();
		for (int i = 0; i < parts.size(); i++) {
			Part p = parts.get(i);
			Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
			if (d == null) continue;
			d.setBlockState(p.block);
			d.setPos(c.pivot.x, c.pivot.y, c.pivot.z);
			d.setTransformation(new Transformation(camMatrix(p, c.yaw, c.pitch)));
			if (p.glow) d.setBrightnessOverride(Brightness.FULL_BRIGHT);
			d.setViewRange(2f);
			d.addTag(CAM_TAG);
			c.parts.add(d);   // до добавления: ENTITY_LOAD удаляет «чужие» части камеры
			l.addFreshEntity(d);
			if (i == 4 || i == 6) c.leds.add(d);
		}
	}

	// ======================================================================= лампы: вывеска, «моргающий» фонарь

	/** Буквы вывески (для подсветки): из сохранения подхватываются в ENTITY_LOAD. */
	private static final List<Display.ItemDisplay> letters = new ArrayList<>();
	private static final boolean[] signLit = new boolean[SIGN_LAMPS.length];
	private static final int[] signNext = new int[SIGN_LAMPS.length], signBurst = new int[SIGN_LAMPS.length];
	private static final RandomSource lr = RandomSource.create();
	private static int cycleT = -1, cycleNext = 200;
	private static boolean lampsInit = false;

	public static void track(Entity e) {
		if (e instanceof Display.ItemDisplay d && e.getTags().contains(LETTER_TAG) && !letters.contains(d)) {
			letters.add(d);
			int i = nearestLamp(d.getX());
			d.setBrightnessOverride(new Brightness(signLit[i] ? 11 : 3, 0));
		}
	}

	private static int nearestLamp(double x) {
		int best = 0;
		for (int i = 1; i < SIGN_LAMPS.length; i++)
			if (Math.abs(SIGN_LAMPS[i] + 0.5 - x) < Math.abs(SIGN_LAMPS[best] + 0.5 - x)) best = i;
		return best;
	}

	private static boolean broken(int i) { return i == SIGN_LAMPS.length - 1; }

	private static void setSignLamp(ServerLevel z, int i, boolean lit) {
		if (signLit[i] == lit) return;
		signLit[i] = lit;
		BlockPos p = new BlockPos(SIGN_LAMPS[i], 79, SHOP_Z1);
		BlockState s = z.getBlockState(p);
		if (s.is(ZavodBlocks.LAMP_HEAD)) z.setBlock(p, s.setValue(ZavodBlocks.LampHead.LIT, lit), Block.UPDATE_CLIENTS);
		letters.removeIf(Entity::isRemoved);
		for (Display.ItemDisplay d : letters)
			if (nearestLamp(d.getX()) == i) d.setBrightnessOverride(new Brightness(lit ? 11 : 3, 0));
	}

	private static final SoundEvent[] FLICKER = {ModSounds.ZAVOD_LAMP_FLICKER1, ModSounds.ZAVOD_LAMP_FLICKER2, ModSounds.ZAVOD_LAMP_FLICKER3,
			ModSounds.ZAVOD_LAMP_FLICKER4, ModSounds.ZAVOD_LAMP_FLICKER5};
	/** Длина всплесков (в тиках) — под звуки zavod_lamp_flicker1..5. */
	private static final int[] FLICKER_LEN = {16, 28, 12, 14, 14};

	private static void lamps(ServerLevel z) {
		if (!lampsInit) {
			lampsInit = true;
			for (int i = 0; i < SIGN_LAMPS.length; i++) {
				signLit[i] = !broken(i);
				signNext[i] = 60 + lr.nextInt(300);
				BlockPos p = new BlockPos(SIGN_LAMPS[i], 79, SHOP_Z1);
				BlockState s = z.getBlockState(p);
				if (s.is(ZavodBlocks.LAMP_HEAD)) z.setBlock(p, s.setValue(ZavodBlocks.LampHead.LIT, signLit[i]), Block.UPDATE_CLIENTS);
			}
			letters.removeIf(Entity::isRemoved);
			for (Display.ItemDisplay d : letters) d.setBrightnessOverride(new Brightness(signLit[nearestLamp(d.getX())] ? 11 : 3, 0));
		}
		if (z.players().isEmpty()) return;
		// вывеска: лампы горят, иногда «моргают» сериями под звук; последняя почти мёртвая и искрит
		for (int i = 0; i < SIGN_LAMPS.length; i++) {
			Vec3 at = new Vec3(SIGN_LAMPS[i] + 0.5, 79.3, SHOP_Z1 + 1.2);
			if (signBurst[i] > 0) {
				signBurst[i]--;
				if (signBurst[i] == 0) setSignLamp(z, i, !broken(i));
				else if (lr.nextInt(100) < 40) setSignLamp(z, i, !signLit[i]);
				continue;
			}
			if (--signNext[i] > 0) continue;
			int v = lr.nextInt(FLICKER.length);
			signBurst[i] = FLICKER_LEN[v];
			signNext[i] = broken(i) ? 80 + lr.nextInt(220) : 240 + lr.nextInt(600);
			z.playSound(null, at.x, at.y, at.z, FLICKER[v], SoundSource.BLOCKS, 1f, 0.9f + lr.nextFloat() * 0.2f);
			setSignLamp(z, i, !signLit[i]);
			if (broken(i) && lr.nextInt(3) > 0) {
				z.playSound(null, at.x, at.y, at.z, ModSounds.ZAVOD_SPARK, SoundSource.BLOCKS, 0.4f, 0.9f + lr.nextFloat() * 0.25f);
				z.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y - 0.2, at.z, 14, 0.15, 0.1, 0.15, 0.25);
				z.sendParticles(ParticleTypes.LAVA, at.x, at.y - 0.2, at.z, 4, 0.1, 0.05, 0.1, 0.0);
			}
		}
		// фонарь у бочек: щелчок → загорается (звук 2.75 с) → горит → гаснет (14.25 с) → пауза
		if (cycleT >= 0) {
			cycleT++;
			if (cycleT == 55) setLamp(z, CYCLE_LAMP, true);
			if (cycleT == 285) { setLamp(z, CYCLE_LAMP, false); cycleT = -1; cycleNext = 300 + lr.nextInt(500); }
		} else if (--cycleNext <= 0) {
			cycleT = 0;
			z.playSound(null, CYCLE_LAMP.getX() + 0.5, CYCLE_LAMP.getY(), CYCLE_LAMP.getZ() + 0.5, ModSounds.ZAVOD_LAMP_CYCLE, SoundSource.BLOCKS, 1f, 1f);
		}
	}

	private static void setLamp(ServerLevel z, BlockPos p, boolean lit) {
		BlockState s = z.getBlockState(p);
		if (s.is(ZavodBlocks.LAMP_HEAD)) z.setBlock(p, s.setValue(ZavodBlocks.LampHead.LIT, lit), Block.UPDATE_CLIENTS);
	}

	/** Старые части камеры из сохранения (после перезапуска) — убрать. */
	public static boolean isStaleCamera(Entity e) {
		if (!e.getTags().contains(CAM_TAG)) return false;
		for (Cam c : cams) if (c.parts.contains(e)) return false;
		return true;
	}

	public static boolean isStale(Entity e) {
		return (e.getTags().contains(TAG) && !e.getTags().contains(versionTag())) || e.getTags().contains("vadim_zavod_stub");
	}

	public static void tick(MinecraftServer server) {
		ServerLevel z = Places.zavod(server);
		if (z == null || cams.isEmpty()) return;
		camTick++;
		// огонёк записи мигает раз в секунду
		if (camTick % 10 == 0)
			for (Cam c : cams)
				for (Display.BlockDisplay led : c.leds)
					led.setBlockState((camTick / 10) % 2 == 0 ? Blocks.RED_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
		lamps(z);
		ZavodInside.tick(z);
		if (camTick % 2 != 0) return;
		List<Part> parts = camParts();
		for (Cam c : cams) {
			ServerPlayer target = null;
			double best = 40 * 40;
			for (ServerPlayer p : z.players()) {
				if (p.isSpectator()) continue;
				// камера в цеху видит только тех, кто в цеху, и наоборот
				if (insideShop(p.getX(), p.getY(), p.getZ()) != insideShop(c.pivot.x, c.pivot.y, c.pivot.z)) continue;
				double d = p.distanceToSqr(c.pivot);
				if (d < best) { best = d; target = p; }
			}
			float wantYaw, wantPitch;
			if (target != null) {
				Vec3 to = target.getEyePosition().subtract(c.pivot);
				wantYaw = (float) Math.toDegrees(Math.atan2(-to.x, -to.z));
				wantPitch = (float) Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
			} else {
				wantYaw = c.idle + 55f * Mth.sin(camTick / 90f);   // никого нет — медленно осматривается
				wantPitch = -18f;
			}
			float dy = Mth.wrapDegrees(wantYaw - c.yaw);
			float maxStep = 7f;
			c.yaw += Mth.clamp(dy, -maxStep, maxStep);
			c.pitch += Mth.clamp(Mth.clamp(wantPitch, -60f, 15f) - c.pitch, -maxStep, maxStep);
			for (int i = 0; i < c.parts.size() && i < parts.size(); i++) {
				Display.BlockDisplay d = c.parts.get(i);
				d.setTransformationInterpolationDelay(0);
				d.setTransformationInterpolationDuration(2);
				d.setTransformation(new Transformation(camMatrix(parts.get(i), c.yaw, c.pitch)));
			}
		}
	}

	// ======================================================================= правила двора

	/** Что можно трогать ПКМ во дворе (не в креативе). */
	public static boolean usable(BlockPos pos) {
		return pos.getX() == BOOTH_DOOR.getX() && pos.getZ() == BOOTH_DOOR.getZ() && (pos.getY() == 64 || pos.getY() == 65);
	}

	/** Игрок внутри цеха (для звука и атмосферы). */
	public static boolean insideShop(double x, double y, double z) {
		return x > SHOP_X0 && x < SHOP_X1 + 1 && z > SHOP_Z0 && z < SHOP_Z1 + 1 && y < SHOP_Y1;
	}

	// ======================================================================= сохранение версии

	public static final class Built extends SavedData {
		static final Codec<Built> CODEC = Codec.INT.xmap(Built::new, b -> b.version);
		static final SavedDataType<Built> TYPE = new SavedDataType<>("vadim_zavod_built", Built::new, CODEC, null);
		int version;
		Built() { this(0); }
		Built(int v) { version = v; }
	}
}
