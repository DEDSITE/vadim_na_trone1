package ru.vadim.world;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.Room4Blocks;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Комната 4 «Бэкрумс» (этап 7). 7а: лабиринт Level 0, переход из комнаты 3, три сектора со своими спавнами, лампы (часть мёртвых,
 * часть моргает со звуком). Лабиринт — свой генератор по рефу Димы: квадрат CELL·N = 150 блоков, сетка комнат 10×10, стены 1 блок
 * высотой 5 (y 64..68), потолок y 69 (плитка, лампы через 4 блока), пол y 63 (ковёр). Планировка зависит только от сида мира —
 * одинакова при каждой пересборке/рестарте.
 * Зоны: зал с колоннами (запад), тёмный угол со сломанными лампами (юго-восток), выход (лифт) в центре 3×3 клетки.
 */
public final class Room4 {
	public static final int X0 = 200, Z0 = -75, CELL = 10, N = 15, X1 = X0 + CELL * N, Z1 = Z0 + CELL * N;
	public static final int FLOOR = 63, TOP = 69;
	/** Клетки спавнов секторов 0/1/2: северо-запад, северо-восток, юг. ~100 блоков между ними. */
	static final int[][] SPAWN_CELLS = {{2, 2}, {12, 2}, {7, 13}};
	/** Зал с колоннами (клетки, включительно) и тёмный угол. */
	static final int HALL_I0 = 0, HALL_I1 = 3, HALL_J0 = 5, HALL_J1 = 9;
	/** Тёмные зоны с улыбаками (клетки i0, i1, j0, j1 включительно): по одной на сектор + ещё одна. В каждой 2 «островка» света. */
	static final int[][] DARK_ZONES = {{11, 14, 9, 12}, {0, 2, 11, 14}, {5, 7, 0, 2}, {10, 13, 4, 6}};
	/** Центр 3×3 клетки — зал выхода (лифт). */
	static final int MID0 = 6, MID1 = 8;
	public static final Vec3 LIFT = new Vec3(X0 + 75.5, 64, Z0 + 75.5);

	private static Layout layout;
	private static final List<Flicker> flickers = new ArrayList<>();

	private Room4() {}

	public static boolean inRoom4(double x, double z) {
		return x >= X0 - 0.5 && x <= X1 + 1.5 && z >= Z0 - 0.5 && z <= Z1 + 1.5;
	}

	// ======================================================================= планировка

	/** Вся планировка — чистая функция от сида: стены, проходы, лампы, сектора. */
	static final class Layout {
		/** wallV[i][j] — стена на вертикали x = X0+10i для строки клеток j; wallH[i][j] — на горизонтали z = Z0+10j для столбца i. */
		final boolean[][] wallV = new boolean[N + 1][N], wallH = new boolean[N][N + 1];
		/** Проём в снятой стене: начало (1..9) и ширина (9 — нет стены совсем). */
		final int[][] gapVOff = new int[N + 1][N], gapVW = new int[N + 1][N], gapHOff = new int[N][N + 1], gapHW = new int[N][N + 1];
		final List<BlockPos> lamps = new ArrayList<>();
		final List<Integer> lampSector = new ArrayList<>();
		final Set<BlockPos> broken = new HashSet<>(), vents = new HashSet<>();
		/** dark[i][j]: 1 — тёмная зона с улыбаками, 2 — островок света в ней, 3 — просто тёмный угол (без улыбак). */
		final int[][] dark = new int[N][N];
		final List<BlockPos> flicker = new ArrayList<>();
		final long seed;

		Layout(long seed) {
			this.seed = seed;
			Random r = new Random(seed ^ 0x4BAC4B00L);
			for (boolean[] a : wallV) java.util.Arrays.fill(a, true);
			for (boolean[] a : wallH) java.util.Arrays.fill(a, true);
			// 1. лабиринт: поиск в глубину из центра
			boolean[][] seen = new boolean[N][N];
			java.util.ArrayDeque<int[]> st = new java.util.ArrayDeque<>();
			st.push(new int[]{7, 7});
			seen[7][7] = true;
			int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			while (!st.isEmpty()) {
				int[] c = st.peek();
				List<int[]> next = new ArrayList<>();
				for (int[] d : dirs) {
					int ni = c[0] + d[0], nj = c[1] + d[1];
					if (ni >= 0 && nj >= 0 && ni < N && nj < N && !seen[ni][nj]) next.add(new int[]{ni, nj, d[0], d[1]});
				}
				if (next.isEmpty()) { st.pop(); continue; }
				int[] n = next.get(r.nextInt(next.size()));
				open(c[0], c[1], n[0], n[1]);
				seen[n[0]][n[1]] = true;
				st.push(new int[]{n[0], n[1]});
			}
			// 2. петли: снимаем часть оставшихся стен, чтобы было много путей
			for (int i = 1; i < N; i++) for (int j = 0; j < N; j++) if (wallV[i][j] && r.nextFloat() < 0.22f) wallV[i][j] = false;
			for (int i = 0; i < N; i++) for (int j = 1; j < N; j++) if (wallH[i][j] && r.nextFloat() < 0.22f) wallH[i][j] = false;
			// 3. зал с колоннами и центр — без внутренних стен; в центр — вход с каждой стороны
			clearBlock(HALL_I0, HALL_I1, HALL_J0, HALL_J1);
			clearBlock(MID0, MID1, MID0, MID1);
			wallH[7][MID0] = false; wallH[7][MID1 + 1] = false; wallV[MID0][7] = false; wallV[MID1 + 1][7] = false;
			// 4. проёмы: в снятой стене — дверной проём 2..4 блока или (35 %) совсем без стены
			for (int i = 0; i <= N; i++) for (int j = 0; j < N; j++) if (!wallV[i][j]) {
				boolean full = inside(i - 1, j, HALL_I0, HALL_I1, HALL_J0, HALL_J1) && inside(i, j, HALL_I0, HALL_I1, HALL_J0, HALL_J1)
						|| inside(i - 1, j, MID0, MID1, MID0, MID1) && inside(i, j, MID0, MID1, MID0, MID1) || r.nextFloat() < 0.35f;
				int w = full ? 9 : 2 + r.nextInt(3);
				gapVW[i][j] = w; gapVOff[i][j] = full ? 1 : 1 + r.nextInt(10 - w);
			}
			for (int i = 0; i < N; i++) for (int j = 0; j <= N; j++) if (!wallH[i][j]) {
				boolean full = inside(i, j - 1, HALL_I0, HALL_I1, HALL_J0, HALL_J1) && inside(i, j, HALL_I0, HALL_I1, HALL_J0, HALL_J1)
						|| inside(i, j - 1, MID0, MID1, MID0, MID1) && inside(i, j, MID0, MID1, MID0, MID1) || r.nextFloat() < 0.35f;
				int w = full ? 9 : 2 + r.nextInt(3);
				gapHW[i][j] = w; gapHOff[i][j] = full ? 1 : 1 + r.nextInt(10 - w);
			}
			// 5. тёмные зоны (с улыбаками) + в каждой 2 островка света; редкие тёмные углы (1 клетка, без улыбак)
			for (int[] z : DARK_ZONES) {
				for (int i = z[0]; i <= z[1]; i++) for (int j = z[2]; j <= z[3]; j++) dark[i][j] = 1;
				for (int k = 0; k < 2; k++) dark[z[0] + r.nextInt(z[1] - z[0] + 1)][z[2] + r.nextInt(z[3] - z[2] + 1)] = 2;
			}
			for (int k = 0, tries = 0; k < 7 && tries < 400; tries++) {
				int i = r.nextInt(N), j = r.nextInt(N);
				if (dark[i][j] != 0 || inside(i, j, MID0, MID1, MID0, MID1) || inside(i, j, HALL_I0, HALL_I1, HALL_J0, HALL_J1)) continue;
				boolean nearSpawn = false;
				for (int[] s : SPAWN_CELLS) if (Math.abs(s[0] - i) <= 1 && Math.abs(s[1] - j) <= 1) nearSpawn = true;
				if (nearSpawn) continue;
				dark[i][j] = 3;
				k++;
			}
			// 6. лампы через 4 блока (не на линиях стен), мёртвые в тёмных зонах/углах и ~6 % по лабиринту, ~6 % моргают
			for (int dx = 2; dx < CELL * N; dx += 4) for (int dz = 2; dz < CELL * N; dz += 4) {
				if (dx % CELL == 0 || dz % CELL == 0) continue;
				BlockPos p = new BlockPos(X0 + dx, TOP, Z0 + dz);
				int ci = dx / CELL, cj = dz / CELL;
				lamps.add(p);
				lampSector.add(sector(ci, cj));
				boolean mid = inside(ci, cj, MID0, MID1, MID0, MID1);
				int dk = dark[ci][cj];
				if (dk == 1 || dk == 3 || dk == 0 && !mid && !inside(ci, cj, HALL_I0, HALL_I1, HALL_J0, HALL_J1) && r.nextFloat() < 0.06f)
					broken.add(p);
				else if (dk == 0 && !mid && r.nextFloat() < 0.065f) flicker.add(p);
			}
			for (int dx = 4; dx < CELL * N; dx += 4) for (int dz = 4; dz < CELL * N; dz += 4)
				if (dx % CELL != 0 && dz % CELL != 0 && r.nextFloat() < 0.04f) vents.add(new BlockPos(X0 + dx, TOP, Z0 + dz));
		}

		private void open(int i, int j, int ni, int nj) {
			if (ni != i) wallV[Math.max(i, ni)][j] = false;
			else wallH[i][Math.max(j, nj)] = false;
		}

		private void clearBlock(int i0, int i1, int j0, int j1) {
			for (int i = i0 + 1; i <= i1; i++) for (int j = j0; j <= j1; j++) wallV[i][j] = false;
			for (int i = i0; i <= i1; i++) for (int j = j0 + 1; j <= j1; j++) wallH[i][j] = false;
		}

		/** Стена ли в колонке (x, z) на высоте стен. */
		boolean wallAt(int x, int z) {
			int dx = x - X0, dz = z - Z0;
			if (dx < 0 || dz < 0 || dx > CELL * N || dz > CELL * N) return false;
			boolean onV = dx % CELL == 0, onH = dz % CELL == 0;
			int i = dx / CELL, j = dz / CELL;
			if (onV && onH) {   // узел сетки: столб, если к нему подходит хоть одна стена; в зале — колонна
				if (inside(i, j, HALL_I0 + 1, HALL_I1, HALL_J0 + 1, HALL_J1)) return true;
				return (j > 0 && wallV[i][j - 1]) || (j < N && wallV[i][j]) || (i > 0 && wallH[i - 1][j]) || (i < N && wallH[i][j]);
			}
			if (onV) {
				if (wallV[i][j]) return true;
				int off = dz % CELL;
				return off < gapVOff[i][j] || off >= gapVOff[i][j] + gapVW[i][j];
			}
			if (onH) {
				if (wallH[i][j]) return true;
				int off = dx % CELL;
				return off < gapHOff[i][j] || off >= gapHOff[i][j] + gapHW[i][j];
			}
			// колонны посреди клеток зала
			return inside(i, j, HALL_I0, HALL_I1, HALL_J0, HALL_J1) && dx % CELL == 5 && dz % CELL == 5;
		}
	}

	static boolean inside(int i, int j, int i0, int i1, int j0, int j1) { return i >= i0 && i <= i1 && j >= j0 && j <= j1; }

	/** Отладка: центр клетки тёмной зоны n (1..4), не островок света. */
	public static Vec3 darkSpot(ServerLevel l, int n) {
		Layout lay = layout(l);
		int[] z = DARK_ZONES[Math.floorMod(n - 1, DARK_ZONES.length)];
		for (int i = z[0]; i <= z[1]; i++) for (int j = z[2]; j <= z[3]; j++)
			if (lay.dark[i][j] == 1 && i > z[0] && j > z[2]) return new Vec3(X0 + i * CELL + 5.5, FLOOR + 1, Z0 + j * CELL + 5.5);
		return new Vec3(X0 + z[0] * CELL + 5.5, FLOOR + 1, Z0 + z[2] * CELL + 5.5);
	}

	/** Тёмная зона с улыбаками в точке (x, z)? (островки света и тёмные углы — нет). */
	public static boolean smilerZone(ServerLevel l, double x, double z) {
		int i = (int) Math.floor((x - X0) / CELL), j = (int) Math.floor((z - Z0) / CELL);
		if (i < 0 || j < 0 || i >= N || j >= N) return false;
		return layout(l).dark[i][j] == 1;
	}

	/** Сектор клетки: ближайший спавн; центр (выход) — −1. */
	public static int sector(int i, int j) {
		if (inside(i, j, MID0, MID1, MID0, MID1)) return -1;
		int best = 0, bd = Integer.MAX_VALUE;
		for (int s = 0; s < 3; s++) {
			int di = i - SPAWN_CELLS[s][0], dj = j - SPAWN_CELLS[s][1], d = di * di + dj * dj;
			if (d < bd) { bd = d; best = s; }
		}
		return best;
	}

	static Layout layout(ServerLevel l) {
		long seed = l.getServer().overworld().getSeed();
		if (layout == null || layout.seed != seed) {
			layout = new Layout(seed);
			flickers.clear();
			Random r = new Random(seed);
			for (BlockPos p : layout.flicker) flickers.add(new Flicker(p, 100 + r.nextInt(800)));
		}
		return layout;
	}

	// ======================================================================= постройка

	/** Строит лабиринт целиком (из Rooms.ensure и /vadim room4 rebuild). */
	public static void build(ServerLevel l) {
		long t0 = System.currentTimeMillis();
		Layout lay = layout(l);
		Random r = new Random(lay.seed * 31 + 7);
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = X0 - 1; x <= X1 + 1; x++) for (int z = Z0 - 1; z <= Z1 + 1; z++) {
			boolean in = x >= X0 && x <= X1 && z >= Z0 && z <= Z1;
			set(l, x, FLOOR - 1, z, Blocks.BLACK_CONCRETE.defaultBlockState());
			set(l, x, TOP + 1, z, Blocks.BLACK_CONCRETE.defaultBlockState());
			if (!in) {   // внешняя рубашка: чтобы никто не вылез за периметр
				for (int y = FLOOR; y <= TOP; y++) set(l, x, y, z, Blocks.BLACK_CONCRETE.defaultBlockState());
				continue;
			}
			set(l, x, FLOOR, z, r.nextFloat() < 0.12f ? Room4Blocks.CARPET_DIRTY[r.nextInt(4)].defaultBlockState() : Room4Blocks.CARPET.defaultBlockState());
			set(l, x, TOP, z, (r.nextFloat() < 0.2f ? Room4Blocks.CEILING2 : Room4Blocks.CEILING).defaultBlockState());
			boolean wall = lay.wallAt(x, z);
			int ci = Math.min(N - 1, (x - X0) / CELL), cj = Math.min(N - 1, (z - Z0) / CELL);
			boolean stripes = ((ci * 7 + cj * 13) % 5) == 0;   // часть комнат — обои в полоску
			for (int y = FLOOR + 1; y < TOP; y++) {
				BlockState s = air;
				if (wall) {
					if (y == FLOOR + 1) s = (stripes ? Room4Blocks.WALL_S_BASE : Room4Blocks.WALL_BASE).defaultBlockState();
					else if (!stripes && y <= FLOOR + 3 && r.nextFloat() < 0.04f) s = Room4Blocks.WALL_STAIN[r.nextInt(3)].defaultBlockState();
					else s = (stripes ? Room4Blocks.WALL_S : Room4Blocks.WALL).defaultBlockState();
				}
				set(l, x, y, z, s);
			}
		}
		for (BlockPos v : lay.vents) l.setBlock(v, Room4Blocks.VENT.defaultBlockState(), Block.UPDATE_CLIENTS);
		for (BlockPos p : lay.lamps) l.setBlock(p, Room4Blocks.LAMP.defaultBlockState().setValue(Room4Blocks.Lamp.LIT, !lay.broken.contains(p)), Block.UPDATE_CLIENTS);
		buildLift(l);
		Room4Power.placeBoxes(l);
		VadimNaTrone.LOG.info("[room4] лабиринт построен за {} мс: ламп {}, мёртвых {}, моргают {}", System.currentTimeMillis() - t0,
				lay.lamps.size(), lay.broken.size(), lay.flicker.size());
	}

	/** Лифт в центре (пока закрыт): коробка 5×5 из гладкого камня, железные двери на юг. */
	private static void buildLift(ServerLevel l) {
		int cx = (int) Math.floor(LIFT.x), cz = (int) Math.floor(LIFT.z);
		for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) for (int y = FLOOR + 1; y < TOP; y++) {
			boolean edge = x == cx - 2 || x == cx + 2 || z == cz - 2 || z == cz + 2;
			set(l, x, y, z, edge ? (y == FLOOR + 5 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE).defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		BlockState lo = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		for (int x : new int[]{cx - 1, cx}) {
			BlockState d = lo.setValue(DoorBlock.HINGE, x == cx ? net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT
					: net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT);
			l.setBlock(new BlockPos(x, FLOOR + 1, cz + 2), d, Block.UPDATE_CLIENTS);
			l.setBlock(new BlockPos(x, FLOOR + 2, cz + 2), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
		}
	}

	private static void set(ServerLevel l, int x, int y, int z, BlockState st) {
		l.setBlock(new BlockPos(x, y, z), st, Block.UPDATE_CLIENTS);
	}

	// ======================================================================= игроки

	/** Перенос в бэкрумс (после победы в комнате 3 и при перезаходе): каждому — свой сектор, просыпается лёжа. */
	public static void place(ServerLevel l, ServerPlayer p) {
		State st = state(l.getServer());
		String id = p.getUUID().toString();
		Integer s = st.sectors.get(id);
		if (s == null) {
			Set<Integer> used = new HashSet<>(st.sectors.values());
			s = 0;
			while (used.contains(s) && s < 2) s++;
			if (used.contains(s)) s = new Random().nextInt(3);
			st.sectors.put(id, s);
			st.setDirty();
		}
		spawnAt(l, p, s);
		Room4Power.assignRole(p);
	}

	public static void spawnAt(ServerLevel l, ServerPlayer p, int sector) {
		Vec3 sp = spawn(sector);
		double yaw = Math.toDegrees(Math.atan2(-(LIFT.x - sp.x), LIFT.z - sp.z));
		if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
		if (p.isPassenger()) p.stopRiding();
		p.teleportTo(l, sp.x, sp.y, sp.z, Set.of(), (float) yaw, 0f, true);
		p.fallDistance = 0;
		if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodFinal.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodFinal(3, 0));
	}

	public static Vec3 spawn(int sector) {
		int[] c = SPAWN_CELLS[Math.floorMod(sector, 3)];
		return new Vec3(X0 + c[0] * CELL + 5.5, FLOOR + 1, Z0 + c[1] * CELL + 5.5);
	}

	// ======================================================================= тик: моргающие лампы

	private static final class Flicker {
		final BlockPos pos;
		int wait, step = -1;
		int[] pattern;
		Flicker(BlockPos pos, int wait) { this.pos = pos; this.wait = wait; }
	}

	/** Узоры мигания (тики, когда лампа переключается); под каждый — свой звук мигания с завода. */
	private static final int[][] PATTERNS = {{0, 2, 4, 7, 9, 14}, {0, 3, 5, 6, 8, 18, 20, 27}, {0, 1, 3, 11}, {0, 4, 6, 9, 10, 13}, {0, 2, 5, 8, 11, 13}};
	private static final SoundEvent[] FLICK_SOUNDS = {ModSounds.ZAVOD_LAMP_FLICKER1, ModSounds.ZAVOD_LAMP_FLICKER2, ModSounds.ZAVOD_LAMP_FLICKER3,
			ModSounds.ZAVOD_LAMP_FLICKER4, ModSounds.ZAVOD_LAMP_FLICKER5};

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Room4::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		List<ServerPlayer> here = new ArrayList<>();
		for (ServerPlayer p : l.players()) if (inRoom4(p.getX(), p.getZ())) here.add(p);
		if (here.isEmpty()) return;
		layout(l);
		Random r = new Random();
		for (Flicker f : flickers) {
			int sec = Room4.sector(Math.min(N - 1, (f.pos.getX() - X0) / CELL), Math.min(N - 1, (f.pos.getZ() - Z0) / CELL));
			if (f.step < 0 && (Room4Power.powered(server, sec) || Room4Power.blackedOut(server, sec))) continue;   // с током — ровный свет
			if (f.step < 0) {
				if (--f.wait > 0) continue;
				boolean near = false;
				for (ServerPlayer p : here) if (p.distanceToSqr(f.pos.getX(), f.pos.getY(), f.pos.getZ()) < 30 * 30) { near = true; break; }
				if (!near) { f.wait = 60; continue; }
				int k = r.nextInt(PATTERNS.length);
				f.pattern = PATTERNS[k];
				f.step = 0;
			}
			int t = f.step++;
			for (int tt : f.pattern) if (tt == t) {
				toggle(l, f.pos);
				l.playSound(null, f.pos.getX() + 0.5, f.pos.getY() - 0.5, f.pos.getZ() + 0.5, ModSounds.ROOM4_BLINK, SoundSource.BLOCKS, 0.7f, 0.9f + r.nextFloat() * 0.2f);
			}
			if (t >= f.pattern[f.pattern.length - 1]) {
				BlockState s = l.getBlockState(f.pos);
				if (s.is(Room4Blocks.LAMP) && !s.getValue(Room4Blocks.Lamp.LIT)) toggle(l, f.pos);
				f.step = -1;
				f.wait = 120 + r.nextInt(600);
			}
		}
	}

	private static void toggle(ServerLevel l, BlockPos p) {
		BlockState s = l.getBlockState(p);
		if (s.is(Room4Blocks.LAMP)) l.setBlock(p, s.cycle(Room4Blocks.Lamp.LIT), Block.UPDATE_CLIENTS);
	}

	public static String debug(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return "нет измерения комнат";
		Layout lay = layout(l);
		State st = state(server);
		return "комната 4: ламп " + lay.lamps.size() + ", мёртвых " + lay.broken.size() + ", моргают " + lay.flicker.size() + ", сектора игроков " + st.sectors;
	}

	/** Отладка: карта лабиринта в файл room4_map.txt (# стена, . пол, o лампа, x мёртвая, ~ моргает, L лифт, 1-3 спавны). */
	public static String dumpMap(ServerLevel l) {
		Layout lay = layout(l);
		Set<Long> lit = new HashSet<>(), dead = new HashSet<>(), fl = new HashSet<>();
		for (BlockPos p : lay.lamps) (lay.broken.contains(p) ? dead : lay.flicker.contains(p) ? fl : lit).add(BlockPos.asLong(p.getX(), 0, p.getZ()));
		StringBuilder sb = new StringBuilder();
		for (int z = Z0; z <= Z1; z++) {
			for (int x = X0; x <= X1; x++) {
				long k = BlockPos.asLong(x, 0, z);
				char c = l.getBlockState(new BlockPos(x, FLOOR + 2, z)).isAir() ? '.' : '#';
				if (c == '.' && lit.contains(k)) c = 'o';
				if (c == '.' && dead.contains(k)) c = 'x';
				if (c == '.' && fl.contains(k)) c = '~';
				if (Math.abs(x + 0.5 - LIFT.x) <= 2.5 && Math.abs(z + 0.5 - LIFT.z) <= 2.5) c = 'L';
				for (int s = 0; s < 3; s++) { Vec3 sp = spawn(s); if ((int) Math.floor(sp.x) == x && (int) Math.floor(sp.z) == z) c = (char) ('1' + s); }
				sb.append(c);
			}
			sb.append('\n');
		}
		try { java.nio.file.Files.writeString(java.nio.file.Path.of("room4_map.txt"), sb); } catch (java.io.IOException e) { return "ошибка: " + e; }
		return "карта в room4_map.txt";
	}

	public static void resetSectors(MinecraftServer server) {
		State st = state(server);
		st.sectors.clear();
		st.setDirty();
	}

	// ======================================================================= сохранение

	static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	static final class State extends SavedData {
		static final Codec<State> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(m -> {
			State s = new State();
			s.sectors.putAll(m);
			return s;
		}, s -> s.sectors);
		static final SavedDataType<State> TYPE = new SavedDataType<>("vadim_room4", State::new, CODEC, null);
		final Map<String, Integer> sectors = new HashMap<>();
	}
}
