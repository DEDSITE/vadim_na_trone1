package ru.vadim.world;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.ZavodBlocks;
import ru.vadim.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Этап 3в — цех изнутри. Снаружи ржавый советский завод, внутри — техника Вадима.
 * Маршрут — петля с завалами (пол y=63, стоят на 64; стены-перегородки 4 блока высотой + барьеры сверху):
 *  A. тамбур x 10..25, z −50..−43 (вход — боковая дверь (16,64,−42)): старые станки, шкафчики, хлам; выход на запад в проём (9, z −46..−45);
 *  B. передний проход x 1..8, z −50..−43: дальше на запад завал (балка и станки x −2..0), поворот на юг;
 *  C. коридор между серверными стойками x 2..4, z −51..−61 (стойки x=1 и x=5, кабели по полу);
 *  D. задний проход z −63..−61, x −17..5, над ним мостки (x −10..−4) — КОНТРОЛЬНАЯ ТОЧКА: падает железка;
 *  E. подход x −17..−12, z −59..−51 к перегородке x=−18 со стеной мониторов и проёмом гермодвери (z −55..−54) — гермодверь, комната и газ — этап 3г.
 */
public final class ZavodInside {
	/** Камеры внутри: x, y, z точки крепления и направление «осмотра» (yaw). */
	static final float[][] CAMS = {{24.55f, 70.25f, -43.55f, 45f}, {-16.55f, 70.25f, -59.55f, -135f}};

	/** Лампы внутри: позиция головы/трубки, режим. */
	enum Mode { STEADY, FLICKER, CYCLE, DEAD }
	private record Lamp(BlockPos pos, Mode mode) {}
	private static final List<Lamp> LAMPS = List.of(
			new Lamp(new BlockPos(17, 70, -47), Mode.FLICKER),   // тамбур, трубка
			new Lamp(new BlockPos(5, 70, -43), Mode.FLICKER),    // передний проход, «кобра» из фасада
			new Lamp(new BlockPos(3, 69, -53), Mode.STEADY),     // коридор стоек, трубка
			new Lamp(new BlockPos(3, 69, -59), Mode.CYCLE),      // коридор стоек, трубка: вкл → горит → выкл
			new Lamp(new BlockPos(-14, 70, -55), Mode.FLICKER),  // подход к гермодвери, трубка
			new Lamp(new BlockPos(-14, 70, -61), Mode.DEAD));    // задний проход, трубка почти мёртвая
	/** Висячие фонари на цепях с мостков (горят ровно). */
	static final BlockPos[] HANGING = {new BlockPos(-7, 68, -62), new BlockPos(-1, 69, -62)};
	/** Оборванные кабели с потолка: концы (там искры и вспышки света). */
	static final BlockPos[] SPARKS = {new BlockPos(3, 67, -61), new BlockPos(-12, 68, -62), new BlockPos(13, 67, -49)};
	/** Контрольная точка: мостки над задним проходом; железка падает с x≈−8.5 впереди идущих на запад. */
	/** Игрок идёт по заднему проходу на запад: дошёл до x ≤ −2.5 — железка падает впереди, на x ≈ −8.5. */
	static final double CHECK_X = -2.5;
	static final Vec3 DEBRIS_FROM = new Vec3(-8.6, 70.45, -62.0), DEBRIS_TO = new Vec3(-8.4, 64.07, -61.6);
	/** Где гудят сервера (центр коридора стоек). */
	public static final Vec3 SERVERS = new Vec3(3.5, 65.5, -56.0);

	private final ServerLevel l;
	private final RandomSource rnd = RandomSource.create(4242);
	private final Map<String, BlockState> cache = new HashMap<>();
	private final List<BlockPos> reshape = new ArrayList<>();

	private ZavodInside(ServerLevel l) { this.l = l; }

	// ======================================================================= блоки

	private BlockState bs(String spec) {
		BlockState c = cache.get(spec);
		if (c != null) return c;
		BlockState s;
		try {
			s = BlockStateParser.parseForBlock(l.holderLookup(Registries.BLOCK), spec, false).blockState();
		} catch (Exception e) {
			VadimNaTrone.LOG.warn("Цех: нет блока {}", spec);
			s = Blocks.AIR.defaultBlockState();
		}
		cache.put(spec, s);
		return s;
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

	private BlockState wallMat() {
		return pick("6:minecraft:bricks", "3:minecraft:cracked_stone_bricks", "3:minecraft:light_gray_concrete", "2:minecraft:tuff", "1:minecraft:mud_bricks");
	}

	/** Низкая перегородка (y 64..67) + барьеры сверху до 70 — через неё не перелезть, но видно, что за ней. */
	private void lowWall(int x0, int z0, int x1, int z1) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int y = 64; y <= 66; y++) set(x, y, z, wallMat());
				set(x, 67, z, rnd.nextInt(4) == 0 ? Blocks.AIR.defaultBlockState() : wallMat());
				for (int y = 68; y <= 70; y++) set(x, y, z, Blocks.BARRIER.defaultBlockState());
				if (rnd.nextInt(3) == 0) set(x, 67, z, Blocks.BARRIER.defaultBlockState());
			}
	}

	private static BlockState facing(Block b, Direction f) { return b.defaultBlockState().setValue(ZavodBlocks.Deco.FACING, f); }

	// ======================================================================= сборка (из Zavod.ensure)

	static void build(ServerLevel l) {
		ZavodInside b = new ZavodInside(l);
		b.partitions();
		b.tambour();
		b.front();
		b.servers();
		b.back();
		b.approach();
		b.junk();
		b.lights();
		for (int pass = 0; pass < 2; pass++)
			for (BlockPos p : b.reshape) {
				BlockState s = l.getBlockState(p);
				BlockState n = Block.updateFromNeighbourShapes(s, l, p);
				if (n != s) l.setBlock(p, n, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
	}

	private void partitions() {
		// пол цеха: бетон в трещинах и пятнах
		for (int x = -25; x <= 25; x++)
			for (int z = -63; z <= -43; z++)
				set(x, 63, z, pick("10:minecraft:polished_andesite", "3:minecraft:smooth_stone", "2:minecraft:andesite", "1:minecraft:gravel", "1:minecraft:packed_mud"));
		// A: тамбур — стены z=−51 (x 9..25) и x=9 (z −51..−43) с проёмом z −46..−45
		lowWall(9, -51, 25, -51);
		lowWall(9, -50, 9, -47);
		lowWall(9, -44, 9, -43);
		// B: завал на запад — x −2..0, z −50..−43 (сделан в front()), юг переднего прохода: z=−51 кроме входа в коридор x 2..4
		lowWall(0, -51, 1, -51);
		lowWall(5, -51, 8, -51);
		// C: стены за стойками x=0 и x=6, z −52..−60
		lowWall(0, -52, 0, -60);
		lowWall(6, -52, 6, -63);
		// D: север заднего прохода z=−60 от x −11 до −1
		lowWall(-11, -60, -1, -60);
		// E: подход — восток x=−11 (z −59..−50), север z=−50 (x −17..−11)
		lowWall(-11, -59, -11, -50);
		lowWall(-17, -50, -12, -50);
		// перегородка гермодвери x=−18 во всю высоту, бетон; проём z −55..−54, y 64..66 (сама дверь — этап 3г)
		for (int z = -63; z <= -43; z++)
			for (int y = 64; y <= 81; y++) {
				if (y <= 66 && (z == -55 || z == -54)) { set(-18, y, z, Blocks.AIR.defaultBlockState()); continue; }
				set(-18, y, z, pick("8:minecraft:light_gray_concrete", "3:minecraft:gray_concrete", "1:minecraft:cracked_stone_bricks"));
			}
		// рама проёма — сталь
		fill(-18, 67, -56, -18, 67, -53, "minecraft:polished_deepslate");
		fill(-18, 64, -56, -18, 66, -56, "minecraft:polished_deepslate");
		fill(-18, 64, -53, -18, 66, -53, "minecraft:polished_deepslate");
		// комната за перегородкой (для 3г): пустая, бетонная, без окон; пока тупик
		for (int x = -25; x <= -19; x++)
			for (int z = -63; z <= -43; z++)
				for (int y = 64; y <= 81; y++) {
					boolean room = z >= -58 && z <= -51 && x >= -24 && y <= 68;
					set(x, y, z, room ? Blocks.AIR.defaultBlockState() : bs("minecraft:gray_concrete"));
				}
		fill(-24, 63, -58, -19, 63, -51, "minecraft:smooth_stone");
		fill(-24, 69, -58, -19, 69, -51, "minecraft:gray_concrete");
		// 3г: выключенный ЭЛТ на западной стене, две решётки вентиляции под потолком (оттуда газ), одна тусклая лампа
		set(-24, 64, -55, "minecraft:barrel[facing=up]");
		set(-24, 65, -55, facing(ZavodBlocks.CRT_OFF, Direction.EAST));
		for (BlockPos v : ZavodFinal.VENTS) set(v.getX(), v.getY(), v.getZ(), "minecraft:iron_trapdoor[facing=north,half=top]");
		set(-22, 68, -54, "minecraft:lantern[hanging=true]");
	}

	private void tambour() {
		// старые станки и шкафчики раздевалки вдоль стен, мусор
		for (int z = -50; z <= -45; z++) {
			set(25, 64, z, "minecraft:iron_block");
			set(25, 65, z, "minecraft:iron_block");
			set(24, 64, z, z % 2 == 0 ? "minecraft:iron_trapdoor[facing=west,half=bottom,open=true]" : "minecraft:iron_trapdoor[facing=west,half=bottom,open=false]");
		}
		set(22, 64, -50, "minecraft:blast_furnace[facing=north]");
		set(21, 64, -50, "minecraft:smoker[facing=north]");
		set(19, 64, -50, "minecraft:grindstone[face=floor,facing=north]");
		set(18, 64, -50, "minecraft:damaged_anvil[facing=east]");
		set(14, 64, -50, "minecraft:smithing_table");
		set(13, 64, -50, "minecraft:cauldron");
		set(12, 64, -44, "minecraft:barrel[facing=up]");
		set(11, 64, -44, "minecraft:barrel[facing=north]");
		set(11, 65, -44, "minecraft:barrel[facing=up]");
		set(20, 64, -46, "minecraft:cobblestone_slab[type=bottom]");
		set(15, 64, -48, "minecraft:gravel");
		set(23, 64, -44, "minecraft:cobweb");
		set(10, 66, -50, "minecraft:cobweb");
		// кабель свисает и искрит у двери
		for (int y = 67; y <= 80; y++) set(13, y, -49, ZavodBlocks.CABLE_HANG.defaultBlockState());
		set(13, 67, -49, ZavodBlocks.CABLE_HANG.defaultBlockState().setValue(ZavodBlocks.CableHang.END, true));
	}

	private void front() {
		// завал на запад: рухнувшая балка (ржавый двутавр) и перевёрнутые станки, кирпичный бой
		for (int z = -50; z <= -43; z++)
			for (int x = -2; x <= 0; x++) {
				int h = 64 + 2 + rnd.nextInt(2);
				for (int y = 64; y <= h; y++)
					set(x, y, z, pick("4:minecraft:bricks", "3:minecraft:cobblestone", "2:minecraft:iron_block", "2:minecraft:waxed_exposed_copper", "1:minecraft:blast_furnace"));
				for (int y = h + 1; y <= 70; y++) set(x, y, z, Blocks.BARRIER.defaultBlockState());
			}
		for (int z = -50; z <= -43; z++) set(1, 67 + (z + 50) / 3, z, "minecraft:polished_basalt[axis=z]");   // балка наискось на завал
		set(1, 64, -44, "minecraft:damaged_anvil[facing=north]");
		set(3, 64, -50, "minecraft:barrel[facing=east]");
		set(7, 64, -44, "minecraft:cobweb");
		// кабели по полу: из-за завала → к коридору стоек
		for (int x = 2; x <= 4; x++) set(x, 64, -50, facing(ZavodBlocks.CABLES, Direction.NORTH));
	}

	private void servers() {
		// две ряда стоек лицом в коридор, сверху кабельный лоток
		for (int z = -60; z <= -52; z++) {
			set(1, 64, z, facing(ZavodBlocks.RACK, Direction.EAST));
			set(1, 65, z, facing(ZavodBlocks.RACK_TOP, Direction.EAST));
			set(5, 64, z, facing(ZavodBlocks.RACK, Direction.WEST));
			set(5, 65, z, facing(ZavodBlocks.RACK_TOP, Direction.WEST));
			set(1, 66, z, "minecraft:iron_trapdoor[facing=east,half=bottom]");
			set(5, 66, z, "minecraft:iron_trapdoor[facing=west,half=bottom]");
			for (int x = 2; x <= 4; x++) set(x, 64, z, facing(ZavodBlocks.CABLES, Direction.NORTH));
		}
		for (int x = 2; x <= 4; x++) set(x, 64, -51, facing(ZavodBlocks.CABLES, Direction.NORTH));
		// барьер над стойками, чтобы не залезть
		for (int z = -60; z <= -52; z++) for (int x : new int[]{1, 5}) fill(x, 67, z, x, 70, z, Blocks.BARRIER.defaultBlockState());
		// оборванный кабель над выходом из коридора
		for (int y = 67; y <= 80; y++) set(3, y, -61, ZavodBlocks.CABLE_HANG.defaultBlockState());
		set(3, 67, -61, ZavodBlocks.CABLE_HANG.defaultBlockState().setValue(ZavodBlocks.CableHang.END, true));
	}

	private void back() {
		// задний проход: кабели вдоль x, хлам у южной стены, мостки над головой
		for (int x = -17; x <= 4; x++)
			for (int z = -62; z <= -61; z++) if (l.getBlockState(new BlockPos(x, 64, z)).isAir()) set(x, 64, z, facing(ZavodBlocks.CABLES, Direction.EAST));
		set(-2, 64, -63, "minecraft:barrel[facing=up]");
		set(-9, 64, -63, "minecraft:cobblestone_slab[type=bottom]");
		set(4, 64, -63, "minecraft:cobweb");
		// мостки: решётчатый настил y=70, поручни, цепи к потолку
		for (int x = -10; x <= -3; x++) {
			for (int z = -63; z <= -61; z++) set(x, 70, z, "minecraft:iron_trapdoor[facing=north,half=top]");
			setShaped(x, 71, -60, bs("minecraft:iron_bars"));
		}
		for (int x : new int[]{-10, -6, -3}) for (int y = 72; y <= 81; y++) set(x, y, -61, "minecraft:iron_chain[axis=y]");
		// висячие фонари с мостков
		for (BlockPos p : HANGING) {
			for (int y = p.getY() + 1; y <= 69; y++) set(p.getX(), y, p.getZ(), "minecraft:iron_chain[axis=y]");
			set(p.getX(), p.getY(), p.getZ(), "minecraft:lantern[hanging=true]");
		}
		// ещё один оборванный кабель
		for (int y = 69; y <= 80; y++) set(-12, y, -62, ZavodBlocks.CABLE_HANG.defaultBlockState());
		set(-12, 68, -62, ZavodBlocks.CABLE_HANG.defaultBlockState().setValue(ZavodBlocks.CableHang.END, true));
	}

	private void approach() {
		// кабели ковром к проёму гермодвери
		for (int z = -60; z <= -51; z++)
			for (int x = -17; x <= -15; x++) set(x, 64, z, facing(ZavodBlocks.CABLES, z == -54 || z == -55 ? Direction.EAST : Direction.NORTH));
		// стена мониторов на перегородке (смотрят на восток): ЭЛТ на ящиках по бокам проёма.
		// все — помехи (живую картинку с камеры убрали по просьбе Димы, 26.11), (−17,65,−52) — «Хочешь сыграть в игру?»
		for (int z : new int[]{-59, -58, -57, -52, -51})
			set(-17, 64, z, "minecraft:barrel[facing=up]");
		for (int z : new int[]{-59, -58, -57, -52, -51}) {
			Block b = z == -52 ? ZavodBlocks.CRT_PLAY : ZavodBlocks.CRT;
			set(-17, 65, z, facing(b, Direction.EAST));
			if (z != -57 && z != -52) set(-17, 66, z, facing(ZavodBlocks.CRT, Direction.EAST));
		}
		// стойка у проёма
		set(-17, 64, -60, facing(ZavodBlocks.RACK, Direction.EAST));
		set(-17, 65, -60, facing(ZavodBlocks.RACK_TOP, Direction.EAST));
		// кронштейн камеры
		set(-17, 71, -60, "minecraft:polished_deepslate");
	}

	private void junk() {
		// недоступные зоны — видно поверх перегородок: ряды старых станков, ящики, поддоны, котёл
		for (int x = -10; x <= -3; x += 3)
			for (int z = -58; z <= -45; z += 4) {
				set(x, 64, z, rnd.nextBoolean() ? "minecraft:blast_furnace[facing=east]" : "minecraft:smoker[facing=west]");
				set(x + 1, 64, z, "minecraft:iron_block");
			}
		for (int i = 0; i < 40; i++) {
			int x = -10 + rnd.nextInt(10), z = -59 + rnd.nextInt(16);
			if (!l.getBlockState(new BlockPos(x, 64, z)).isAir()) continue;
			int k = rnd.nextInt(6);
			set(x, 64, z, k < 2 ? "minecraft:barrel[facing=up]" : k < 3 ? "minecraft:spruce_slab[type=bottom]" : k < 4 ? "minecraft:gravel" : k < 5 ? "minecraft:cobblestone" : "minecraft:cobweb");
		}
		// восточный задний угол: котёл и трубы
		for (int y = 64; y <= 70; y++)
			for (int x = 14; x <= 18; x++)
				for (int z = -61; z <= -55; z++) {
					if (Math.hypot(x - 16, z + 58) <= 2.6 && y <= 69) set(x, y, z, pick("4:minecraft:waxed_exposed_copper", "2:minecraft:waxed_weathered_copper", "1:minecraft:orange_terracotta"));
				}
		for (int x = 7; x <= 13; x++) set(x, 72, -58, "minecraft:waxed_weathered_cut_copper");
		for (int z = -52; z <= -50; z++) set(18, 64, z, "minecraft:barrel[facing=up]");
		// трубы под потолком через весь цех
		for (int x = -17; x <= 25; x++) { set(x, 78, -45, "minecraft:waxed_weathered_cut_copper"); set(x, 77, -62, "minecraft:waxed_exposed_cut_copper"); }
		// кабели свисают гирляндами между стенами (просто украшение)
		for (int x = -10; x <= 25; x += 7) for (int y = 74; y <= 80; y++) set(x, y, -52, ZavodBlocks.CABLE_HANG.defaultBlockState());
	}

	private void lights() {
		for (Lamp lamp : LAMPS) {
			BlockPos p = lamp.pos;
			boolean lit = lamp.mode != Mode.DEAD && lamp.mode != Mode.CYCLE;
			if (p.getZ() == -43) {   // «кобра» у стены фасада, плафон смотрит внутрь цеха (на север)
				set(p.getX(), p.getY(), p.getZ(), ZavodBlocks.LAMP_HEAD.defaultBlockState().setValue(ZavodBlocks.Deco.FACING, Direction.NORTH)
						.setValue(ZavodBlocks.LampHead.LIT, lit));
				continue;
			}
			Direction axis = p.getZ() == -47 ? Direction.EAST : Direction.NORTH;
			set(p.getX(), p.getY(), p.getZ(), facing(ZavodBlocks.TUBE, axis).setValue(ZavodBlocks.Tube.LIT, lit));
			for (int y = p.getY() + 1; y <= 81; y++) set(p.getX(), y, p.getZ(), "minecraft:iron_chain[axis=y]");
		}
		// кронштейн камеры в тамбуре
		set(25, 71, -44, "minecraft:polished_deepslate");
	}

	// ======================================================================= живое: лампы, искры, железка

	private static final RandomSource lr = RandomSource.create();
	private static final Map<BlockPos, int[]> lampState = new HashMap<>();   // [next, burst, cycleT]
	private static final Map<BlockPos, Integer> sparkNext = new HashMap<>();
	private static final List<BlockPos> flashes = new ArrayList<>();
	private static int flashT = 0;
	private static Display.BlockDisplay debris;
	private static int debrisT = -1;

	private static final SoundEvent[] FLICKER = {ModSounds.ZAVOD_LAMP_FLICKER1, ModSounds.ZAVOD_LAMP_FLICKER2, ModSounds.ZAVOD_LAMP_FLICKER3,
			ModSounds.ZAVOD_LAMP_FLICKER4, ModSounds.ZAVOD_LAMP_FLICKER5};
	private static final int[] FLICKER_LEN = {16, 28, 12, 14, 14};

	private static boolean lit(ServerLevel z, BlockPos p) {
		BlockState s = z.getBlockState(p);
		if (s.is(ZavodBlocks.TUBE)) return s.getValue(ZavodBlocks.Tube.LIT);
		if (s.is(ZavodBlocks.LAMP_HEAD)) return s.getValue(ZavodBlocks.LampHead.LIT);
		return false;
	}

	private static void setLit(ServerLevel z, BlockPos p, boolean on) {
		BlockState s = z.getBlockState(p);
		if (s.is(ZavodBlocks.TUBE) && s.getValue(ZavodBlocks.Tube.LIT) != on) z.setBlock(p, s.setValue(ZavodBlocks.Tube.LIT, on), Block.UPDATE_CLIENTS);
		if (s.is(ZavodBlocks.LAMP_HEAD) && s.getValue(ZavodBlocks.LampHead.LIT) != on) z.setBlock(p, s.setValue(ZavodBlocks.LampHead.LIT, on), Block.UPDATE_CLIENTS);
	}

	private static boolean anyoneInside(ServerLevel z) {
		for (ServerPlayer p : z.players()) if (!p.isSpectator() && Zavod.insideShop(p.getX(), p.getY(), p.getZ())) return true;
		return false;
	}

	static void tick(ServerLevel z) {
		// вспышки света от искр гаснут через 3 тика
		if (!flashes.isEmpty() && --flashT <= 0) {
			for (BlockPos p : flashes) if (z.getBlockState(p).is(Blocks.LIGHT)) z.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			flashes.clear();
		}
		debrisTick(z);
		if (!anyoneInside(z)) return;
		for (Lamp lamp : LAMPS) {
			int[] st = lampState.computeIfAbsent(lamp.pos, k -> new int[]{40 + lr.nextInt(200), 0, -1});
			Vec3 at = Vec3.atCenterOf(lamp.pos);
			switch (lamp.mode) {
				case STEADY -> setLit(z, lamp.pos, true);
				case FLICKER, DEAD -> {
					boolean base = lamp.mode == Mode.FLICKER;
					if (st[1] > 0) {
						st[1]--;
						if (st[1] == 0) setLit(z, lamp.pos, base);
						else if (lr.nextInt(100) < 40) setLit(z, lamp.pos, !lit(z, lamp.pos));
					} else if (--st[0] <= 0) {
						int v = lr.nextInt(FLICKER.length);
						st[1] = FLICKER_LEN[v];
						st[0] = base ? 160 + lr.nextInt(420) : 90 + lr.nextInt(200);
						z.playSound(null, at.x, at.y, at.z, FLICKER[v], SoundSource.BLOCKS, 0.45f, 0.9f + lr.nextFloat() * 0.2f);
						setLit(z, lamp.pos, !lit(z, lamp.pos));
					}
				}
				case CYCLE -> {
					if (st[2] >= 0) {
						st[2]++;
						if (st[2] == 55) setLit(z, lamp.pos, true);
						if (st[2] == 285) { setLit(z, lamp.pos, false); st[2] = -1; st[0] = 160 + lr.nextInt(260); }
					} else if (--st[0] <= 0) {
						st[2] = 0;
						z.playSound(null, at.x, at.y, at.z, ModSounds.ZAVOD_LAMP_CYCLE, SoundSource.BLOCKS, 0.5f, 1f);
					}
				}
			}
		}
		// искры с оборванных кабелей: треск, частицы, короткая вспышка света
		for (BlockPos p : SPARKS) {
			int n = sparkNext.getOrDefault(p, 30 + lr.nextInt(120)) - 1;
			if (n > 0) { sparkNext.put(p, n); continue; }
			sparkNext.put(p, 70 + lr.nextInt(220));
			Vec3 at = Vec3.atCenterOf(p).add(0, -0.4, 0);
			z.playSound(null, at.x, at.y, at.z, ModSounds.ZAVOD_SPARK, SoundSource.BLOCKS, 0.35f, 0.85f + lr.nextFloat() * 0.3f);
			z.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16, 0.12, 0.08, 0.12, 0.3);
			z.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 3, 0.05, 0.02, 0.05, 0.0);
			BlockPos f = p.below();
			if (z.getBlockState(f).isAir()) {
				z.setBlock(f, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13), Block.UPDATE_CLIENTS);
				flashes.add(f);
				flashT = 3;
			}
		}
	}

	// ======================================================================= контрольная точка: падает железка

	private static void debrisTick(ServerLevel z) {
		if (debrisT >= 0) {
			debrisT++;
			if (debrisT == 1 && debris != null) {   // со следующего тика — чтобы клиенты увидели, откуда он сорвался
				debris.teleportTo(DEBRIS_TO.x, DEBRIS_TO.y, DEBRIS_TO.z);
				debris.setTransformation(new Transformation(rod(LYING_YAW, 0f)));
			}
			if (debrisT == 1 + FALL_TICKS && debris != null) {   // долетел
				z.playSound(null, DEBRIS_TO.x, DEBRIS_TO.y, DEBRIS_TO.z, ModSounds.ZAVOD_DEBRIS, SoundSource.BLOCKS, 0.6f, 1f);
				z.playSound(null, DEBRIS_TO.x, DEBRIS_TO.y, DEBRIS_TO.z, ModSounds.ZAVOD_DEBRIS_ROD, SoundSource.BLOCKS, 0.7f, 1f);
				z.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()),
						DEBRIS_TO.x, DEBRIS_TO.y + 0.1, DEBRIS_TO.z, 10, 0.5, 0.05, 0.3, 0.1);
				// пыль с пола поднялась облачком вокруг
				z.sendParticles(ParticleTypes.POOF, DEBRIS_TO.x, DEBRIS_TO.y + 0.15, DEBRIS_TO.z, 16, 0.8, 0.08, 0.5, 0.025);
				z.sendParticles(new DustParticleOptions(0x8C867A, 1.6f), DEBRIS_TO.x, DEBRIS_TO.y + 0.25, DEBRIS_TO.z, 40, 1.0, 0.2, 0.7, 0.0);
			}
			if (debrisT == 7 + FALL_TICKS) {   // облачко ещё висит и оседает
				z.sendParticles(new DustParticleOptions(0x7E796E, 1.3f), DEBRIS_TO.x, DEBRIS_TO.y + 0.5, DEBRIS_TO.z, 28, 1.3, 0.3, 0.9, 0.0);
				z.sendParticles(ParticleTypes.WHITE_SMOKE, DEBRIS_TO.x, DEBRIS_TO.y + 0.3, DEBRIS_TO.z, 6, 0.9, 0.1, 0.6, 0.005);
				debrisT = -1;
			}
			return;
		}
		if (debrisFell(z)) return;
		for (ServerPlayer p : z.players()) {
			if (p.isSpectator() || p.getZ() > -60.5 || p.getX() > CHECK_X || !Zavod.insideShop(p.getX(), p.getY(), p.getZ())) continue;
			dropDebris(z);
			return;
		}
	}

	/** Железный прут срывается из-под мостков и падает впереди (тихо, звук только от него, поднимается пыль). */
	private static final int FALL_TICKS = 10;
	private static final float HANG_YAW = 0.5f, HANG_TILT = 0.2f, LYING_YAW = 0.9f;

	private static void dropDebris(ServerLevel z) {
		Debris st = z.getDataStorage().computeIfAbsent(Debris.TYPE);
		st.fell = true;
		st.setDirty();
		if (debris == null || debris.isRemoved()) spawnDebris(z, DEBRIS_FROM, rod(HANG_YAW, HANG_TILT));
		if (debris == null) return;
		debris.setPosRotInterpolationDuration(FALL_TICKS);
		debris.setTransformationInterpolationDelay(0);
		debris.setTransformationInterpolationDuration(FALL_TICKS);
		debrisT = 0;
	}

	/** Железный прут 2 блока × 0.12: поворот вокруг вертикали и наклон. */
	private static Matrix4f rod(float yaw, float tilt) {
		return new Matrix4f().rotateY(yaw).rotateZ(tilt).translate(-1.0f, -0.06f, -0.06f).scale(2.0f, 0.12f, 0.12f);
	}

	private static void spawnDebris(ServerLevel z, Vec3 at, Matrix4f m) {
		if (debris != null && !debris.isRemoved()) debris.discard();
		Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(z, EntitySpawnReason.TRIGGERED);
		if (d == null) return;
		d.setBlockState(Blocks.IRON_BLOCK.defaultBlockState());
		d.setPos(at.x, at.y, at.z);
		d.setTransformation(new Transformation(m));
		d.setViewRange(1f);
		d.addTag(ZavodDoor.TAG);
		ZavodDoor.keep(d);
		z.addFreshEntity(d);
		debris = d;
	}

	static boolean debrisFell(ServerLevel z) { return z.getDataStorage().computeIfAbsent(Debris.TYPE).fell; }

	/** Старт сервера: железка либо лежит на мостках, либо уже на полу. */
	static void setup(ServerLevel z) {
		debris = null;
		debrisT = -1;
		lampState.clear();
		if (debrisFell(z)) spawnDebris(z, DEBRIS_TO, rod(LYING_YAW, 0f));
		else spawnDebris(z, DEBRIS_FROM, rod(HANG_YAW, HANG_TILT));
	}

	static void resetDebris(ServerLevel z) {
		Debris st = z.getDataStorage().computeIfAbsent(Debris.TYPE);
		st.fell = false;
		st.setDirty();
	}

	/** Отладка: можно ли пройти от двери цеха до проёма гермодвери (поиск в ширину по клеткам y=64). */
	public static String checkPath(ServerLevel z) {
		BlockPos start = new BlockPos(16, 64, -43), goal = new BlockPos(-17, 64, -54);
		java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
		Map<BlockPos, BlockPos> from = new HashMap<>();
		q.add(start);
		from.put(start, start);
		while (!q.isEmpty()) {
			BlockPos c = q.poll();
			if (c.equals(goal)) {
				int n = 0;
				for (BlockPos p = goal; !p.equals(start); p = from.get(p)) n++;
				return "путь есть: " + n + " шагов, посещено " + from.size() + " клеток";
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos nx = c.relative(d);
				if (from.containsKey(nx) || nx.getX() < -25 || nx.getX() > 25 || nx.getZ() < -63 || nx.getZ() > -43) continue;
				if (!z.getBlockState(nx).getCollisionShape(z, nx).isEmpty() || !z.getBlockState(nx.above()).getCollisionShape(z, nx.above()).isEmpty()) continue;
				from.put(nx, c);
				q.add(nx);
			}
		}
		return "ПУТИ НЕТ, посещено " + from.size() + " клеток";
	}

	public static final class Debris extends SavedData {
		static final Codec<Debris> CODEC = Codec.BOOL.xmap(Debris::new, s -> s.fell);
		static final SavedDataType<Debris> TYPE = new SavedDataType<>("vadim_zavod_debris", Debris::new, CODEC, null);
		boolean fell;
		Debris() { this(false); }
		Debris(boolean b) { fell = b; }
	}
}
