package ru.vadim.world;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.Room3Blocks;
import ru.vadim.entity.GovnozavrEntity;
import ru.vadim.entity.ModEntities;
import ru.vadim.entity.RocketSkeleton;
import ru.vadim.item.ModItems;
import ru.vadim.net.HubPayloads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Комната 3 «Мини-мир» (этап 6). После газа студии игроки просыпаются в светлом «майнкрафтовом» мире 40×40:
 * небо и облака из блоков, ручей, два домика деревни, дерево, куча говна в углу, безобидный скелет с ракетой Вадима.
 * У каждого свой незеритовый инструмент без прочности (кирка / меч / топор). За 7 минут надо собрать для машины
 * «ВАДИМ-1» пластинку: 2 стака брёвен + стак говна. Дерево одно — саженцы падают с него, растут от костной муки
 * (из кости скелета, строго одна кость со скелета). Мини-Говнозавры охотятся только на тех, у кого кирка или топор.
 * Игра начинается сама — первым сломанным бревном, первым говном или первым ударом по скелету.
 * Смерть — наблюдатель, его вещи (и инструмент) достаются случайному живому. Все умерли или время вышло — заново.
 * Победа — табло уезжает, 5 с, мир трясётся, пол исчезает, все падают в чёрную пустоту → заглушка «КОМНАТА 4».
 * Реплик и правил Вадима здесь нет.
 */
public final class Room3 {
	public static final String TAG = "vadim_r3";
	public static final int P_WAIT = 0, P_GAME = 1, P_RESET = 2, P_PRINT = 3, P_WON = 4, P_DONE = 5;
	public static final int TOTAL = 7 * 60 * 20;
	public static final int NEED_LOGS = 128, NEED_POOP = 64;
	/** Победа: табло уезжает на 0, мир рушится с COLLAPSE, чернота у клиента к ~WIN_BLACK, перенос — WIN_MOVE. */
	public static final int PRINT_LEN = 60, COLLAPSE = 150, COLLAPSE_LEN = 100, WIN_BLACK = 270, WIN_MOVE = 290;
	public static final int RESET_LEN = 90, RESET_AT = 30;

	// ---- геометрия: мир x 41..80, z −20..19, трава y=63 (стоят на 64), небо-потолок y=90, под полом пустота до y=3
	public static final int X0 = 41, X1 = 80, Z0 = -20, Z1 = 19, FLOOR = 63, SKY = 90, BOTTOM = 3;
	public static final Vec3 SPAWN = new Vec3(60.5, 64, 14.5);
	public static final float SPAWN_YAW = 180f;
	/** Заглушка комнаты 4 (x 120..128, z −4..4). */
	public static final Vec3 ROOM4_SPAWN = new Vec3(124.5, 64, 0.5);
	/** Машина: корпус 3×2×2 (x 59..62, z −13..−11), лицом на +Z (к спавну). */
	public static final Vec3 MACHINE = new Vec3(60.5, 65.5, -12.0);
	static final double FRONT_Z = -11.0;
	/** Табло на северной стене: прячется за небом-потолком, опускается до BOARD_DOWN. */
	public static final double BOARD_X = 60.5, BOARD_Z = -19.4, BOARD_UP = 96.0, BOARD_DOWN = 77.0;
	/** Три источника говна (углы, кроме угла скелета): работает один, 10 говна — и он пуст, на следующем встаёт ракета. */
	static final BlockPos[] SOURCES = {new BlockPos(78, 64, -18), new BlockPos(43, 64, -18), new BlockPos(78, 64, 17)};
	static final int PER_SOURCE = 10;
	/** Скелет после смерти появляется снова через 15 с. */
	static final int SKELETON_RESPAWN = 15 * 20;
	/** …и с ходом таймера реже: к концу 7 минут — раз в 100 с. */
	static final int SKELETON_RESPAWN_END = 100 * 20;
	/** Дерево растёт только тут; ствол 7 блоков — дотянуться: досягаемость по блокам в комнате 3 больше. */
	static final int TRUNK = 7;
	public static final double REACH = 7.0, REACH_DEFAULT = 4.5;
	static final int TREE_MARK_EVERY = 10;
	static final Vec3 SKELETON = new Vec3(42.5, 64, 17.5);
	static final BlockPos TREE = new BlockPos(52, 64, -2);
	static final int STREAM_Z = 8;
	/** Дома: x0, z0 (угол), дверь на южной стене; мобы выходят из дверей. */
	static final int[][] HOUSES = {{66, -4}, {44, -16}};
	static final Vec3 DEAD_VIEW = new Vec3(60.5, 79, 17.5);

	// ---- состояние
	private static int phase = P_WAIT, phaseTick = 0, timeLeft = TOTAL;
	private static int logs = 0, poop = 0;
	private static int nextMob = 0;
	private static int src = 0, srcLeft = PER_SOURCE, skeletonDue = 0;
	private static boolean srcRocket = false;
	private static final Set<UUID> dead = new HashSet<>(), players = new HashSet<>();
	private static String toolsKey = "";
	private static final List<Tree> trees = new ArrayList<>();
	private static List<long[]> columns = null;
	private static int columnPos = 0;
	private static long lastSentKey = -1;

	// ---- сущности
	private static final List<Entity> live = new ArrayList<>();
	private static final List<GovnozavrEntity> mobs = new ArrayList<>();
	private static RocketSkeleton skeleton;
	private static final List<Display> board = new ArrayList<>();
	private static final Display.ItemDisplay[] digits = new Display.ItemDisplay[4];
	private static final Display.ItemDisplay[] lifeLamp = new Display.ItemDisplay[3];
	private static Display.TextDisplay message, panel;
	private static Display.ItemDisplay machine, record;
	private static Interaction machineHit;
	private static double boardY = BOARD_UP;
	/** Сколько тиков ещё едет табло (лязг цепей каждому игроку). */
	private static int boardMoving = 0;
	/** Громкость звука табло: в MC радиус слышимости = 16 × громкость (2.2 → ~35 блоков, линейно затихает). */
	private static final float BOARD_VOL = 2.2f;
	private static double boardFrom = BOARD_UP;

	/** Где сейчас табло по высоте (пока едет) — звук цепей идёт от него; выше неба не поднимаем, чтобы было слышно. */
	private static double boardSoundY() {
		double k = 1.0 - boardMoving / 50.0;
		return Math.min(SKY - 2.0, boardFrom + (boardY - boardFrom) * k);
	}
	private static float recordAngle = 0f;

	private Room3() {}

	private static final class Tree {
		final Set<BlockPos> logs = new HashSet<>(), leaves = new HashSet<>();
		final BlockPos base;
		Tree(BlockPos base) { this.base = base; }
	}

	// ======================================================================= регистрация

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Room3::tick);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.dimension() != Places.ROOMS || !entity.getTags().contains(TAG) || !(entity instanceof Interaction)) return InteractionResult.PASS;
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
			if (entity == machineHit) deposit((ServerLevel) level, sp);
			return InteractionResult.SUCCESS;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (level.dimension() != Places.ROOMS || player.isCreative()) return true;
			if (!inRoom3(pos.getX() + 0.5, pos.getZ() + 0.5)) return false;
			if (phase != P_WAIT && phase != P_GAME) return false;
			return state.is(Blocks.OAK_LOG) || state.is(Blocks.OAK_LEAVES) || state.is(Blocks.OAK_SAPLING) || state.is(Room3Blocks.GOVNO) || state.is(Room3Blocks.ROCKET);
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level.dimension() != Places.ROOMS || !(level instanceof ServerLevel l) || !inRoom3(pos.getX() + 0.5, pos.getZ() + 0.5)) return;
			if (state.is(Room3Blocks.GOVNO)) {
				govnoBroken(l, pos);
				startIfWaiting(l, "говно");
			} else if (state.is(Room3Blocks.ROCKET)) {
				rocketBroken(l, pos);
			} else if (state.is(Blocks.OAK_LOG)) {
				logBroken(l, pos);
				startIfWaiting(l, "бревно");
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, e, hit) -> {
			if (level.dimension() == Places.ROOMS && e instanceof RocketSkeleton && level instanceof ServerLevel l && !player.isCreative() && hasSword(player))
				startIfWaiting(l, "скелет");
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((e, src, amt) -> {
			if (!(e instanceof ServerPlayer p) || e.level().dimension() != Places.ROOMS || !inRoom3(p.getX(), p.getZ())) return true;
			die(p);
			return false;
		});
	}

	public static boolean inRoom3(double x, double z) {
		return x > X0 - 4 && x < X1 + 5 && z > Z0 - 4 && z < Z1 + 5;
	}

	public static boolean inRoom3(Entity e) {
		return e.level().dimension() == Places.ROOMS && inRoom3(e.getX(), e.getZ());
	}

	/**
	 * Урон в комнате 3 — только во время игры. Бить (мобов и игроков, ПвП) может только тот, у кого меч;
	 * с киркой и топором — никого. Меч перешёл после смерти — теперь бьёт новый хозяин.
	 */
	public static boolean damageAllowed(Entity e, net.minecraft.world.damagesource.DamageSource src) {
		if (phase != P_GAME || !inRoom3(e)) return false;
		if (src.getEntity() instanceof Player p && !p.isCreative()) return hasSword(p);
		return true;
	}

	public static boolean hasSword(Player p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.NETHERITE_SWORD)) return true;
		return false;
	}

	/** Сытость без регена: еда 17 (бегать можно, здоровье само не растёт). */
	public static boolean managesFood(ServerPlayer p) {
		return inRoom3(p) && !p.isSpectator();
	}

	public static boolean isStale(Entity e) {
		return e.getTags().contains(TAG) && !live.contains(e) && !mobs.contains(e) && e != skeleton;
	}

	public static State state(MinecraftServer s) {
		ServerLevel l = Places.rooms(s);
		return l == null ? new State() : l.getDataStorage().computeIfAbsent(State.TYPE);
	}

	public static boolean won(MinecraftServer s) { return state(s).won; }

	/** ПКМ по блоку в комнатах: в комнате 3 можно сажать саженцы на траву и растить их костной мукой. */
	public static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (!inRoom3(pos.getX() + 0.5, pos.getZ() + 0.5) || (phase != P_WAIT && phase != P_GAME)) return InteractionResult.FAIL;
		ItemStack st = player.getItemInHand(hand);
		BlockState s = level.getBlockState(pos);
		if (st.is(Items.BONE_MEAL)) {
			if (!s.is(Blocks.OAK_SAPLING) || !pos.equals(TREE)) return InteractionResult.FAIL;
			if (level instanceof ServerLevel l) {
				if (!player.isCreative()) st.shrink(1);
				l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				growTree(l, pos);
				l.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 1.0, pos.getY() + 1.5, pos.getZ() + 1.0, 30, 1.2, 1.2, 1.2, 0.0);
				l.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1f, 1f);
				l.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1f, 0.7f);
			}
			return InteractionResult.SUCCESS;
		}
		// сажать — только на одно место, где стоит дерево (и если его сейчас нет)
		if (st.is(Items.OAK_SAPLING) && hit.getDirection() == Direction.UP && pos.equals(TREE.below()) && level.getBlockState(TREE).isAir())
			return InteractionResult.PASS;
		return InteractionResult.FAIL;
	}

	// ======================================================================= постройка (из Rooms.ensure)

	private static BlockState b(Block x) { return x.defaultBlockState(); }

	private static void set(ServerLevel l, int x, int y, int z, BlockState st) {
		l.setBlock(new BlockPos(x, y, z), st, Block.UPDATE_CLIENTS);
	}

	/** Мир целиком: коробка с небом, трава, ручей, дома, тропинки, цветы; заглушка комнаты 4. Динамика (дерево, говно) — reset. */
	public static void build(ServerLevel l) {
		long t0 = System.currentTimeMillis();
		Random r = new Random(3333);
		BlockState air = b(Blocks.AIR), black = b(Blocks.BLACK_CONCRETE), sky = b(Room3Blocks.SKY);
		for (int x = X0 - 6; x <= X1 + 6; x++) for (int z = Z0 - 6; z <= Z1 + 6; z++) for (int y = BOTTOM - 1; y <= SKY + 8; y++) {
			boolean wall = x < X0 || x > X1 || z < Z0 || z > Z1;
			BlockState st;
			if (x < X0 - 1 || x > X1 + 1 || z < Z0 - 1 || z > Z1 + 1) st = air;
			else if (y > SKY) st = air;
			else if (y == SKY) st = sky;
			else if (y <= BOTTOM) st = black;
			else if (wall) st = y >= FLOOR + 1 ? sky : black;
			else if (y == FLOOR) st = b(Blocks.GRASS_BLOCK);
			else if (y >= FLOOR - 3 && y < FLOOR) st = b(Blocks.DIRT);
			else st = air;
			set(l, x, y, z, st);
		}
		// облака на потолке и высоко на стенах, солнце
		for (int k = 0; k < 14; k++) {
			int cx = X0 + 2 + r.nextInt(X1 - X0 - 4), cz = Z0 + 2 + r.nextInt(Z1 - Z0 - 4), w = 3 + r.nextInt(5), d = 2 + r.nextInt(3);
			for (int x = cx - w; x <= cx + w; x++) for (int z = cz - d; z <= cz + d; z++)
				if (x >= X0 && x <= X1 && z >= Z0 && z <= Z1 && (Math.abs(x - cx) < w || Math.abs(z - cz) < d || r.nextBoolean()))
					set(l, x, SKY, z, b(Room3Blocks.CLOUD));
		}
		for (int k = 0; k < 18; k++) {
			int side = r.nextInt(4), y = 80 + r.nextInt(7), c = r.nextInt(36), w = 2 + r.nextInt(4);
			for (int i = c - w; i <= c + w; i++) for (int dy = 0; dy <= 1 + (r.nextInt(3) == 0 ? 1 : 0); dy++) {
				if (Math.abs(i - c) == w && dy > 0) continue;
				int t = Math.max(0, Math.min(39, i));
				switch (side) {
					case 0 -> set(l, X0 - 1, y + dy, Z0 + t, b(Room3Blocks.CLOUD));
					case 1 -> set(l, X1 + 1, y + dy, Z0 + t, b(Room3Blocks.CLOUD));
					case 2 -> set(l, X0 + t, y + dy, Z0 - 1, b(Room3Blocks.CLOUD));
					default -> set(l, X0 + t, y + dy, Z1 + 1, b(Room3Blocks.CLOUD));
				}
			}
		}
		for (int x = 70; x <= 73; x++) for (int z = 4; z <= 7; z++) set(l, x, SKY, z, b(Room3Blocks.SUN));
		// ручей с запада на восток, мост перед спавном
		for (int x = X0; x <= X1; x++) {
			int zz = STREAM_Z + (int) Math.round(Math.sin((x - X0) * 0.35) * 0.8);
			for (int z = zz; z <= zz + 1; z++) {
				set(l, x, FLOOR - 1, z, b(Blocks.DIRT));
				set(l, x, FLOOR, z, b(Blocks.WATER));
			}
		}
		for (int x = 59; x <= 62; x++) for (int z = STREAM_Z - 1; z <= STREAM_Z + 2; z++)
			if (l.getBlockState(new BlockPos(x, FLOOR, z)).is(Blocks.WATER)) set(l, x, FLOOR, z, b(Blocks.OAK_PLANKS));
		// тропинки: от моста к машине и к домам
		for (int z = -10; z <= STREAM_Z - 2; z++) for (int x = 60; x <= 61; x++) path(l, x, z);
		for (int x = 48; x <= 69; x++) path(l, x, 4);
		for (int z = -8; z <= 4; z++) path(l, 47, z);
		for (int z = 3; z <= 4; z++) path(l, 69, z);
		// место под саженец: подзол в кольце из тропинки — видно издалека
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
			if (dx != 0 || dz != 0) set(l, TREE.getX() + dx, FLOOR, TREE.getZ() + dz, b(Blocks.DIRT_PATH));
		set(l, TREE.getX(), FLOOR, TREE.getZ(), b(Blocks.PODZOL));
		// дома деревни
		for (int i = 0; i < HOUSES.length; i++) house(l, HOUSES[i][0], HOUSES[i][1], i == 0);
		// трава и цветы
		for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
			if (!l.getBlockState(new BlockPos(x, FLOOR, z)).is(Blocks.GRASS_BLOCK) || !l.getBlockState(new BlockPos(x, FLOOR + 1, z)).isAir()) continue;
			if (Math.abs(x - 60.5) < 4 && z > -15 && z < -9) continue;   // у машины чисто
			boolean nearSrc = false;
			for (BlockPos sp : SOURCES) if (Math.abs(x - sp.getX()) < 3 && Math.abs(z - sp.getZ()) < 3) nearSrc = true;
			if (nearSrc) continue;
			if (Math.abs(x - SKELETON.x) < 2 && Math.abs(z - SKELETON.z) < 2) continue;
			if (Math.abs(x - TREE.getX()) < 4 && Math.abs(z - TREE.getZ()) < 4) continue;
			int k = r.nextInt(100);
			if (k < 9) set(l, x, FLOOR + 1, z, b(Blocks.SHORT_GRASS));
			else if (k < 11) set(l, x, FLOOR + 1, z, b(Blocks.POPPY));
			else if (k < 13) set(l, x, FLOOR + 1, z, b(Blocks.DANDELION));
			else if (k < 14) set(l, x, FLOOR + 1, z, b(Blocks.OXEYE_DAISY));
		}
		// пара кустов и забор у машины для уюта
		for (int[] a : new int[][]{{52, 18}, {75, -6}, {64, -18}}) set(l, a[0], FLOOR + 1, a[1], b(Blocks.AZALEA));
		// заглушка комнаты 4
		for (int x = 118; x <= 130; x++) for (int z = -6; z <= 6; z++) for (int y = 62; y <= 70; y++)
			set(l, x, y, z, (x == 118 || x == 130 || z == -6 || z == 6 || y == 62 || y == 70) ? b(Blocks.GRAY_CONCRETE) : air);
		for (int x = 119; x <= 129; x++) for (int z = -5; z <= 5; z++) {
			set(l, x, 63, z, b(Blocks.SMOOTH_STONE));
			if (x == 119 || x == 129 || z == -5 || z == 5) for (int y = 64; y <= 69; y++) set(l, x, y, z, b(Blocks.GRAY_CONCRETE));
		}
		set(l, 124, 68, 0, b(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		set(l, 124, 69, 0, b(Blocks.IRON_CHAIN));
		for (var e : l.getEntities(EntityType.TEXT_DISPLAY, en -> en.getTags().contains("vadim_r4_stub"))) e.discard();
		Display.TextDisplay t = EntityType.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		t.setPos(124.5, 66.2, -4.4);
		t.setText(Component.literal("КОМНАТА 4\n").withColor(0xB0B0B0).append(Component.literal("в разработке").withColor(0x808080)));
		t.setBackgroundColor(0);
		t.setTransformation(new Transformation(null, null, new Vector3f(1.6f), null));
		t.addTag("vadim_r4_stub");
		l.addFreshEntity(t);
		VadimNaTrone.LOG.info("Комната 3 построена за {} мс", System.currentTimeMillis() - t0);
	}

	private static void path(ServerLevel l, int x, int z) {
		BlockPos p = new BlockPos(x, FLOOR, z);
		if (l.getBlockState(p).is(Blocks.GRASS_BLOCK)) set(l, x, FLOOR, z, b(Blocks.DIRT_PATH));
	}

	/** Домик деревни равнин 7×7: булыжник понизу, доски, брёвна по углам, окна, дверь на юг, крыша ступеньками. */
	private static void house(ServerLevel l, int x0, int z0, boolean northDoor) {
		int x1 = x0 + 6, z1 = z0 + 6, doorX = x0 + 3;
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			set(l, x, FLOOR, z, b(Blocks.COBBLESTONE));
			boolean edge = x == x0 || x == x1 || z == z0 || z == z1, corner = (x == x0 || x == x1) && (z == z0 || z == z1);
			for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
				BlockState st = Blocks.AIR.defaultBlockState();
				if (corner) st = b(Blocks.SPRUCE_LOG);
				else if (edge) {
					st = y == FLOOR + 1 ? b(Blocks.COBBLESTONE) : b(Blocks.OAK_PLANKS);
					boolean mid = (x == x0 + 3 || z == z0 + 3);
					if (y == FLOOR + 2 && mid) st = b(Blocks.GLASS_PANE);
				}
				set(l, x, y, z, st);
			}
			set(l, x, FLOOR + 5, z, b(Blocks.OAK_PLANKS));
		}
		// крыша: два ската ступеньками вдоль X
		for (int k = 0; k <= 3; k++) for (int z = z0 - 1; z <= z1 + 1; z++) {
			int y = FLOOR + 5 + k;
			if (k < 3) {
				set(l, x0 - 1 + k, y, z, b(Blocks.SPRUCE_STAIRS).setValue(StairBlock.FACING, Direction.EAST));
				set(l, x1 + 1 - k, y, z, b(Blocks.SPRUCE_STAIRS).setValue(StairBlock.FACING, Direction.WEST));
				for (int x = x0 + k; x <= x1 - k; x++) if (z == z0 - 1 || z == z1 + 1) continue; else if (k > 0) set(l, x, y, z, b(Blocks.SPRUCE_PLANKS));
			} else for (int x = x0 + 2; x <= x1 - 2; x++) set(l, x, y, z, b(Blocks.SPRUCE_SLAB));
		}
		// торцы крыши (фронтоны) — доски
		for (int k = 1; k < 3; k++) for (int x = x0 + k; x <= x1 - k; x++) for (int z : new int[]{z0, z1}) set(l, x, FLOOR + 5 + k, z, b(Blocks.OAK_PLANKS));
		// дверь на юг — открыта
		BlockState lo = b(Blocks.OAK_DOOR).setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.OPEN, true)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		set(l, doorX, FLOOR + 1, z1, lo);
		set(l, doorX, FLOOR + 2, z1, lo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		// правый дом (со спавна, лицом к машине) — ещё дверь на северной стене: оттуда тоже выходят мобы
		if (northDoor) {
			BlockState n = lo.setValue(DoorBlock.FACING, Direction.NORTH);
			set(l, doorX, FLOOR + 1, z0, n);
			set(l, doorX, FLOOR + 2, z0, n.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
			set(l, doorX, FLOOR, z0 - 1, b(Blocks.COBBLESTONE_SLAB));
		}
		// внутри: кровать, верстак, бочка
		set(l, x0 + 1, FLOOR + 1, z0 + 1, b(Blocks.CRAFTING_TABLE));
		set(l, x1 - 1, FLOOR + 1, z0 + 1, b(Blocks.BARREL));
		set(l, x0 + 1, FLOOR + 1, z1 - 2, b(Blocks.HAY_BLOCK));
		// стёкла соединить
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			BlockPos p = new BlockPos(x, FLOOR + 2, z);
			BlockState st = l.getBlockState(p);
			if (st.is(Blocks.GLASS_PANE)) l.setBlock(p, Block.updateFromNeighbourShapes(st, l, p), Block.UPDATE_CLIENTS);
		}
		// ступенька у двери
		set(l, doorX, FLOOR, z1 + 1, b(Blocks.COBBLESTONE_SLAB));
	}

	static Vec3 houseDoor(int i) {
		return new Vec3(HOUSES[i][0] + 3.5, FLOOR + 1, HOUSES[i][1] + 8.5);
	}

	// ======================================================================= старт сервера

	public static void setup(ServerLevel l) {
		// чанки мини-мира всегда загружены (табло, машина, мобы живут и без игроков рядом)
		for (int cx = 2; cx <= 5; cx++) for (int cz = -2; cz <= 1; cz++) l.setChunkForced(cx, cz, true);
		List<Entity> old = new ArrayList<>();
		for (Entity e : l.getAllEntities()) if (e.getTags().contains(TAG)) old.add(e);
		old.forEach(Entity::discard);
		live.clear();
		mobs.clear();
		skeleton = null;
		board.clear();
		dead.clear();
		players.clear();
		toolsKey = "";
		columns = null;
		spawnFixtures(l);
		if (state(l.getServer()).won) {
			setPhase(P_DONE);
			VadimNaTrone.LOG.info("[room3] пройдена");
			return;
		}
		resetWorld(l);
		setPhase(P_WAIT);
		VadimNaTrone.LOG.info("[room3] готова");
	}

	/** Машина, её панель и пластинка в окошке, табло на северной стене. */
	private static void spawnFixtures(ServerLevel l) {
		// машина: модель ровно на корпус, барьеры — чтобы не проходить насквозь
		machine = item(l, "room3_machine", MACHINE, new Quaternionf(), 1f);
		machine.setBrightnessOverride(new Brightness(15, 15));
		machine.setViewRange(3f);
		for (int x = 59; x <= 61; x++) for (int y = 64; y <= 65; y++) for (int z = -13; z <= -12; z++)
			l.setBlock(new BlockPos(x, y, z), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
		machineHit = hitbox(l, new Vec3(MACHINE.x, 64.0, MACHINE.z), 3.3f, 2.5f);
		record = item(l, "record_vadim", recordHome(), recordRot(0f), 0.72f);
		record.setBrightnessOverride(new Brightness(15, 15));
		panel = EntityType.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		panel.setPos(MACHINE.x, 66.95, FRONT_Z + 0.05);
		panel.setTransformation(new Transformation(null, null, new Vector3f(0.85f), null));
		panel.setBackgroundColor(0xFFC6C6C6);
		panel.setBrightnessOverride(Brightness.FULL_BRIGHT);
		panel.setLineWidth(240);
		panel.setViewRange(3f);
		add(l, panel);
		// табло (как в студии), смотрит на юг: для зрителя +X — справа
		boardY = BOARD_UP;
		Display.ItemDisplay face = bpart(item(l, "room2_board", new Vec3(BOARD_X, BOARD_UP, BOARD_Z - 0.1), new Quaternionf(), 7.6f));
		face.setBrightnessOverride(new Brightness(15, 15));
		face.setViewRange(4f);
		for (double x : new double[]{BOARD_X - 3.0, BOARD_X + 2.0}) for (int k = 0; k < 22; k++) {
			Display.BlockDisplay c = bpart(block(l, Blocks.IRON_CHAIN.defaultBlockState(), new Vec3(x, BOARD_UP + 1.0 + k, BOARD_Z - 0.5), new Vector3f(1f)));
			c.setViewRange(4f);
		}
		double[] dx = {-0.93, -0.33, 0.27, 0.87};
		Quaternionf flip = new Quaternionf().rotateY((float) Math.PI);
		for (int k = 0; k < 4; k++) {
			Display.ItemDisplay d = bpart(item(l, "room1_seg_0", new Vec3(BOARD_X + 0.07 + dx[k], BOARD_UP - 0.02, BOARD_Z + 0.02), flip, 0.85f));
			d.setBrightnessOverride(new Brightness(15, 15));
			d.setViewRange(4f);
			digits[k] = d;
		}
		message = bpart(text(l, new Vec3(BOARD_X + 0.07, BOARD_UP - 0.42, BOARD_Z + 0.03), 1.7f));
		for (int k = 0; k < 3; k++) {
			Display.ItemDisplay d = bpart(item(l, "room2_bulb_on", new Vec3(BOARD_X + 2.30 + k * 0.505, BOARD_UP - 0.15, BOARD_Z + 0.08), flip, 0.9f));
			d.setBrightnessOverride(new Brightness(15, 15));
			d.setViewRange(4f);
			lifeLamp[k] = d;
		}
	}

	private static Vec3 recordHome() { return new Vec3(MACHINE.x, 65.31, FRONT_Z + 0.03); }

	private static Quaternionf recordRot(float a) { return new Quaternionf().rotateY((float) Math.PI).rotateZ(a); }

	/** Попытка заново: дерево, говно, скелет на месте; мобы, вещи на земле и саженцы убраны; машина пустая. */
	private static void resetWorld(ServerLevel l) {
		AABB box = area();
		for (Entity e : l.getEntitiesOfClass(Entity.class, box, e -> e instanceof ItemEntity || e instanceof GovnozavrEntity || e instanceof RocketSkeleton
				|| e instanceof net.minecraft.world.entity.ExperienceOrb)) e.discard();
		mobs.clear();
		skeleton = null;
		for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) for (int y = FLOOR + 1; y <= FLOOR + 16; y++) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState s = l.getBlockState(p);
			if (s.is(Blocks.OAK_LOG) || s.is(Blocks.OAK_LEAVES) || s.is(Blocks.OAK_SAPLING)) l.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		trees.clear();
		growTree(l, TREE);
		for (BlockPos sp : SOURCES) l.setBlock(sp, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		src = 0;
		srcLeft = PER_SOURCE;
		srcRocket = false;
		l.setBlock(SOURCES[0], Room3Blocks.GOVNO.defaultBlockState(), Block.UPDATE_ALL);
		spawnSkeleton(l);
		logs = poop = 0;
		timeLeft = TOTAL;
		if (record != null) {
			Vec3 h = recordHome();
			record.setPosRotInterpolationDuration(0);
			record.teleportTo(h.x, h.y, h.z);
		}
		updatePanel();
	}

	private static AABB area() {
		return new AABB(X0 - 2, BOTTOM, Z0 - 2, X1 + 3, SKY, Z1 + 3);
	}

	// ======================================================================= дерево

	/**
	 * Классический дуб, всегда одинаковый: ствол 7 блоков, крона как у ванильного дуба — два слоя 5×5 без углов,
	 * слой 3×3 без углов и «плюс» на верхушке.
	 */
	private static void growTree(ServerLevel l, BlockPos at) {
		Tree t = new Tree(at);
		BlockState log = b(Blocks.OAK_LOG), leaf = b(Blocks.OAK_LEAVES).setValue(LeavesBlock.PERSISTENT, true);
		for (int dy = 0; dy < TRUNK; dy++) {
			BlockPos p = at.above(dy);
			BlockState s = l.getBlockState(p);
			if (s.isAir() || s.canBeReplaced() || s.is(Blocks.OAK_SAPLING) || s.is(Blocks.OAK_LEAVES)) {
				l.setBlock(p, log, Block.UPDATE_ALL);
				t.logs.add(p);
			}
		}
		int top = at.getY() + TRUNK - 1;
		int[][] layers = {{top - 2, 2}, {top - 1, 2}, {top, 1}, {top + 1, 0}};
		for (int[] ly : layers) {
			int y = ly[0], r = ly[1];
			for (int dx = -Math.max(1, r); dx <= Math.max(1, r); dx++) for (int dz = -Math.max(1, r); dz <= Math.max(1, r); dz++) {
				boolean corner = Math.abs(dx) == Math.max(1, r) && Math.abs(dz) == Math.max(1, r);
				if (corner) continue;   // углы срезаны
				if (r == 0 && Math.abs(dx) + Math.abs(dz) > 1) continue;   // верхушка — плюс
				BlockPos p = new BlockPos(at.getX() + dx, y, at.getZ() + dz);
				if (!l.getBlockState(p).isAir()) continue;
				l.setBlock(p, leaf, Block.UPDATE_ALL);
				t.leaves.add(p);
			}
		}
		trees.add(t);
	}

	/** Срубили бревно: если у дерева брёвен не осталось — листва осыпается, падают 2 саженца. */
	private static void logBroken(ServerLevel l, BlockPos pos) {
		for (Tree t : new ArrayList<>(trees)) {
			if (!t.logs.remove(pos)) continue;
			if (!t.logs.isEmpty()) return;
			for (BlockPos p : t.leaves) if (l.getBlockState(p).is(Blocks.OAK_LEAVES)) {
				l.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				if (l.getRandom().nextInt(3) == 0)
					l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
			}
			l.playSound(null, t.base, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 1.2f, 0.8f);
			for (int k = 0; k < 2; k++) {
				ItemEntity it = new ItemEntity(l, t.base.getX() + 1.0, t.base.getY() + 0.3, t.base.getZ() + 1.0, new ItemStack(Items.OAK_SAPLING));
				it.setDeltaMovement((l.getRandom().nextDouble() - 0.5) * 0.15, 0.2, (l.getRandom().nextDouble() - 0.5) * 0.15);
				l.addFreshEntity(it);
			}
			trees.remove(t);
			return;
		}
	}

	// ======================================================================= источники говна

	/** Сломали говно: на рабочем источнике — новое сразу, пока не кончатся 10; потом на следующем встаёт ракета. */
	private static void govnoBroken(ServerLevel l, BlockPos pos) {
		if (srcRocket || !pos.equals(SOURCES[src])) return;
		if (--srcLeft > 0) {
			l.setBlock(pos, Room3Blocks.GOVNO.defaultBlockState(), Block.UPDATE_ALL);
			return;
		}
		src = (src + 1) % SOURCES.length;
		srcRocket = true;
		BlockPos next = SOURCES[src];
		l.setBlock(next, Room3Blocks.ROCKET.defaultBlockState(), Block.UPDATE_ALL);
		l.sendParticles(ParticleTypes.POOF, next.getX() + 0.5, next.getY() + 1, next.getZ() + 0.5, 15, 0.3, 0.6, 0.3, 0.02);
		l.playSound(null, next, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.2f, 0.6f);
		VadimNaTrone.LOG.info("[room3] источник говна кончился, ракета на источнике {}", src + 1);
	}

	/** Сломали ракету — источник заработал: 10 говна. */
	private static void rocketBroken(ServerLevel l, BlockPos pos) {
		if (!srcRocket || !pos.equals(SOURCES[src])) return;
		srcRocket = false;
		srcLeft = PER_SOURCE;
		l.setBlock(pos, Room3Blocks.GOVNO.defaultBlockState(), Block.UPDATE_ALL);
		l.playSound(null, pos, SoundEvents.MUD_PLACE, SoundSource.BLOCKS, 1.2f, 0.8f);
		VadimNaTrone.LOG.info("[room3] источник {} заработал", src + 1);
	}

	// ======================================================================= ход игры

	private static void setPhase(int p) {
		phase = p;
		phaseTick = 0;
		lastSentKey = -1;
	}

	private static void startIfWaiting(ServerLevel l, String why) {
		if (phase != P_WAIT) return;
		players.clear();
		for (ServerPlayer p : alive(l)) players.add(p.getUUID());
		timeLeft = TOTAL;
		nextMob = 7 * 20;
		scanDoors(l);
		setPhase(P_GAME);
		moveBoard(l, true);
		shakeAll(l);
		showBoard();
		VadimNaTrone.LOG.info("[room3] старт ({}), игроков {}", why, players.size());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null || machine == null) return;
		phaseTick++;
		List<ServerPlayer> here = here(l);
		List<ServerPlayer> alive = alive(l);
		// сытость без регена, наблюдатели — в пределах комнаты
		if (server.getTickCount() % 20 == 0) for (ServerPlayer p : here) if (!p.isSpectator() && !p.isCreative()) {
			p.getFoodData().setFoodLevel(17);
			p.getFoodData().setSaturation(0f);
		}
		for (ServerPlayer p : here) if (p.isSpectator() && phase != P_WON) clampSpectator(p);
		// досягаемость по блокам: в комнате 3 длиннее (верх ствола), везде — обычная
		if (server.getTickCount() % 10 == 0) for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			var a = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
			if (a == null) continue;
			double want = p.level() == l && inRoom3(p.getX(), p.getZ()) && phase != P_DONE ? REACH : REACH_DEFAULT;
			if (a.getBaseValue() != want && (a.getBaseValue() == REACH || a.getBaseValue() == REACH_DEFAULT)) a.setBaseValue(want);
		}
		spinRecord();
		// табло едет: цепи лязгают, в конце — удар
		if (boardMoving > 0) {
			boardMoving--;
			// весь звук — один файл room3_board на старте (moveBoard); ванильные цепи убраны
		}
		// место под саженец, пока дерева нет, — зелёные искорки
		if (TREE_MARK_EVERY > 0 && phaseTick % TREE_MARK_EVERY == 0 && (phase == P_WAIT || phase == P_GAME) && l.getBlockState(TREE).isAir())
			l.sendParticles(ParticleTypes.HAPPY_VILLAGER, TREE.getX() + 0.5, TREE.getY() + 0.3, TREE.getZ() + 0.5, 6, 0.35, 0.2, 0.35, 0.0);
		switch (phase) {
			case P_WAIT -> waitTick(l, alive);
			case P_GAME -> gameTick(l, here, alive);
			case P_PRINT -> printTick(l);
			case P_RESET -> {
				if (phaseTick == RESET_AT) resetAll(l);
				if (phaseTick >= RESET_LEN) setPhase(P_WAIT);
			}
			case P_WON -> wonTick(l, server);
			default -> {}
		}
		if (phase == P_WAIT || phase == P_GAME) keepSkeleton(l);
		send(here);
	}

	private static List<ServerPlayer> here(ServerLevel l) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : l.players()) if (inRoom3(p.getX(), p.getZ())) out.add(p);
		return out;
	}

	private static List<ServerPlayer> alive(ServerLevel l) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : l.players())
			if (inRoom3(p.getX(), p.getZ()) && !p.isSpectator() && !p.isCreative() && !dead.contains(p.getUUID())) out.add(p);
		return out;
	}

	private static void waitTick(ServerLevel l, List<ServerPlayer> alive) {
		for (ServerPlayer p : alive) if (p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) p.setGameMode(GameType.SURVIVAL);
		List<String> ids = new ArrayList<>();
		for (ServerPlayer p : alive) ids.add(p.getUUID().toString());
		Collections.sort(ids);
		String key = String.join(",", ids);
		if (!key.equals(toolsKey)) {
			toolsKey = key;
			giveTools(alive);
		}
		if (phaseTick % 40 == 0) showBoard();
	}

	private static void gameTick(ServerLevel l, List<ServerPlayer> here, List<ServerPlayer> alive) {
		// пришёл посреди попытки — смотрит до следующей
		for (ServerPlayer p : here) if (!p.isSpectator() && !p.isCreative() && !players.contains(p.getUUID()) && !dead.contains(p.getUUID())) {
			dead.add(p.getUUID());
			p.getInventory().clearContent();
			p.setGameMode(GameType.SPECTATOR);
			p.teleportTo(l, DEAD_VIEW.x, DEAD_VIEW.y, DEAD_VIEW.z, Set.of(), 180f, 30f, true);
		}
		timeLeft--;
		if (timeLeft % 20 == 0) {
			showBoard();
			int sec = timeLeft / 20;
			if (sec <= 10 && sec > 0) l.playSound(null, BOARD_X, boardY, BOARD_Z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 3f, 1.2f);
		}
		mobs.removeIf(m -> !m.isAlive());
		float prog = 1f - timeLeft / (float) TOTAL;
		if (--nextMob <= 0) {
			// втрое больше, чем было: каждые 10 → 4 с, до 9 (после половины — 12) одновременно
			int max = prog < 0.5f ? 9 : 12;
			if (mobs.size() < max) spawnMob(l);
			nextMob = (int) (20 * (30 - 18 * prog) / 3) + l.getRandom().nextInt(20);
		}
		if (alive.isEmpty() && !here.isEmpty()) { startReset(l, "все умерли"); return; }
		if (timeLeft <= 0) startReset(l, "время вышло");
	}

	/** Где выходят мобы: перед каждой дверью в мире (свои двери домов + любые, что поставлены руками), снаружи дома. */
	private static final List<Vec3> doorSpots = new ArrayList<>();

	private static void scanDoors(ServerLevel l) {
		doorSpots.clear();
		for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState s = l.getBlockState(p);
			if (!(s.getBlock() instanceof DoorBlock) || s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
			Direction f = s.getValue(DoorBlock.FACING);
			BlockPos a = p.relative(f), b = p.relative(f.getOpposite());
			// снаружи — та сторона, где в 3 блоках от двери уже небо (внутри дома там ещё крыша)
			boolean aOut = !roofed(l, p.relative(f, 3)), bOut = !roofed(l, p.relative(f.getOpposite(), 3));
			BlockPos out = aOut && !bOut ? a : bOut && !aOut ? b : open(l, a) ? a : b;
			doorSpots.add(new Vec3(out.getX() + 0.5, out.getY(), out.getZ() + 0.5));
		}
		VadimNaTrone.LOG.info("[room3] дверей для мобов: {}", doorSpots.size());
	}

	private static boolean open(ServerLevel l, BlockPos p) {
		return !l.getBlockState(p).isSolid() && !l.getBlockState(p.above()).isSolid();
	}

	private static boolean roofed(ServerLevel l, BlockPos p) {
		for (int y = p.getY() + 2; y < SKY; y++) if (!l.getBlockState(new BlockPos(p.getX(), y, p.getZ())).isAir()) return true;
		return false;
	}

	private static void spawnMob(ServerLevel l) {
		Vec3 d = doorSpots.isEmpty() ? houseDoor(l.getRandom().nextInt(HOUSES.length)) : doorSpots.get(l.getRandom().nextInt(doorSpots.size()));
		GovnozavrEntity m = ModEntities.GOVNOZAVR.create(l, EntitySpawnReason.MOB_SUMMONED);
		if (m == null) return;
		m.snapTo(d.x, d.y, d.z, 0f, 0f);
		m.addTag(TAG);
		mobs.add(m);   // до addFreshEntity: ENTITY_LOAD иначе сочтёт его старым
		l.addFreshEntity(m);
		l.sendParticles(ParticleTypes.POOF, d.x, d.y + 1, d.z, 12, 0.3, 0.6, 0.3, 0.02);
		VadimNaTrone.LOG.info("[room3] Говнозавр вышел ({} шт.)", mobs.size());
	}

	private static void keepSkeleton(ServerLevel l) {
		if (skeleton != null && skeleton.isAlive() && !skeleton.isRemoved()) { skeletonDue = 0; return; }
		if (skeletonDue == 0) {   // только что убит: 15 с в начале → 100 с к концу таймера
			float prog = phase == P_GAME ? 1f - timeLeft / (float) TOTAL : 0f;
			skeletonDue = Math.round(SKELETON_RESPAWN + (SKELETON_RESPAWN_END - SKELETON_RESPAWN) * prog);
		}
		if (--skeletonDue > 0) return;
		spawnSkeleton(l);
		l.sendParticles(ParticleTypes.POOF, SKELETON.x, SKELETON.y + 1, SKELETON.z, 12, 0.3, 0.6, 0.3, 0.02);
	}

	private static void spawnSkeleton(ServerLevel l) {
		RocketSkeleton s = ModEntities.ROCKET_SKELETON.create(l, EntitySpawnReason.MOB_SUMMONED);
		if (s == null) return;
		s.snapTo(SKELETON.x, SKELETON.y, SKELETON.z, -135f, 0f);
		s.setYHeadRot(-135f);
		s.setYBodyRot(-135f);
		s.addTag(TAG);
		skeleton = s;
		skeletonDue = 0;
		l.addFreshEntity(s);
	}

	/** Смерть: здоровье назад, вещи — случайному живому, сам — наблюдателем. */
	private static void die(ServerPlayer p) {
		ServerLevel l = (ServerLevel) p.level();
		p.setHealth(p.getMaxHealth());
		p.clearFire();
		p.removeAllEffects();
		dead.add(p.getUUID());
		List<ServerPlayer> others = alive(l);
		others.remove(p);
		if (!others.isEmpty()) {
			ServerPlayer to = others.get(l.getRandom().nextInt(others.size()));
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (!s.isEmpty()) to.getInventory().placeItemBackInInventory(s.copy());
			}
			to.displayClientMessage(Component.literal("Вещи " + p.getGameProfile().name() + " теперь у тебя").withColor(0xE0E0E0), true);
			VadimNaTrone.LOG.info("[room3] {} умер, вещи — {}", p.getGameProfile().name(), to.getGameProfile().name());
		} else VadimNaTrone.LOG.info("[room3] {} умер, живых нет", p.getGameProfile().name());
		p.getInventory().clearContent();
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1f, 1f);
		l.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		p.setGameMode(GameType.SPECTATOR);
		p.teleportTo(l, DEAD_VIEW.x, DEAD_VIEW.y, DEAD_VIEW.z, Set.of(), 180f, 30f, true);
		showBoard();
	}

	private static void clampSpectator(ServerPlayer p) {
		double x = Math.max(X0 + 0.3, Math.min(X1 + 0.7, p.getX()));
		double y = Math.max(FLOOR + 0.2, Math.min(SKY - 1.9, p.getY()));
		double z = Math.max(Z0 + 0.3, Math.min(Z1 + 0.7, p.getZ()));
		if (x != p.getX() || y != p.getY() || z != p.getZ()) p.teleportTo(x, y, z);
	}

	private static void startReset(ServerLevel l, String why) {
		setPhase(P_RESET);
		for (GovnozavrEntity m : mobs) m.setTarget(null);
		VadimNaTrone.LOG.info("[room3] заново: {}", why);
	}

	/** В черноте: мир назад, все живы на спавне, инструменты заново. */
	private static void resetAll(ServerLevel l) {
		resetWorld(l);
		moveBoardInstant(BOARD_UP);
		message.setText(Component.empty());
		dead.clear();
		players.clear();
		List<ServerPlayer> all = new ArrayList<>();
		for (ServerPlayer p : here(l)) if (!p.isCreative()) all.add(p);
		int i = 0;
		for (ServerPlayer p : all) {
			p.getInventory().clearContent();
			p.setGameMode(GameType.SURVIVAL);
			p.setHealth(p.getMaxHealth());
			p.removeAllEffects();
			p.clearFire();
			double dx = (i - (all.size() - 1) / 2.0) * 1.5;
			p.teleportTo(l, SPAWN.x + dx, SPAWN.y, SPAWN.z, Set.of(), SPAWN_YAW, 0f, true);
			p.fallDistance = 0;
			i++;
		}
		toolsKey = "";
		showBoard();
	}

	/** Инструменты: трое — по одному; двое — у одного два; один — все три. Незерит, без прочности. */
	private static void giveTools(List<ServerPlayer> alive) {
		for (ServerPlayer p : alive) {
			var inv = p.getInventory();
			for (int k = 0; k < inv.getContainerSize(); k++) {
				ItemStack s = inv.getItem(k);
				if (s.is(Items.NETHERITE_PICKAXE) || s.is(Items.NETHERITE_SWORD) || s.is(Items.NETHERITE_AXE)) inv.setItem(k, ItemStack.EMPTY);
			}
		}
		if (alive.isEmpty()) return;
		List<ServerPlayer> ps = new ArrayList<>(alive);
		Collections.shuffle(ps);
		List<ItemStack> tools = new ArrayList<>(List.of(tool(Items.NETHERITE_PICKAXE), tool(Items.NETHERITE_SWORD), tool(Items.NETHERITE_AXE)));
		Collections.shuffle(tools);
		for (int k = 0; k < tools.size(); k++) {
			ServerPlayer p = ps.get(k % ps.size());
			p.getInventory().placeItemBackInInventory(tools.get(k));
		}
		VadimNaTrone.LOG.info("[room3] инструменты розданы ({} игр.)", ps.size());
	}

	private static ItemStack tool(net.minecraft.world.item.Item it) {
		ItemStack s = new ItemStack(it);
		s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		return s;
	}

	// ======================================================================= машина

	private static void deposit(ServerLevel l, ServerPlayer p) {
		if (phase != P_GAME) return;
		ItemStack st = p.getMainHandItem();
		int n = 0;
		if (st.is(Items.OAK_LOG) && logs < NEED_LOGS) {
			n = Math.min(st.getCount(), NEED_LOGS - logs);
			logs += n;
			l.playSound(null, MACHINE.x, MACHINE.y, MACHINE.z, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 1f, 0.8f);
			l.playSound(null, MACHINE.x, MACHINE.y, MACHINE.z, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 0.9f);
		} else if (st.is(Room3Blocks.GOVNO_ITEM) && poop < NEED_POOP) {
			n = Math.min(st.getCount(), NEED_POOP - poop);
			poop += n;
			l.playSound(null, MACHINE.x, MACHINE.y, MACHINE.z, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 1f, 0.6f);
			l.playSound(null, MACHINE.x, MACHINE.y, MACHINE.z, SoundEvents.MUD_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
		}
		if (n <= 0) return;
		st.shrink(n);
		p.swing(InteractionHand.MAIN_HAND, true);
		updatePanel();
		VadimNaTrone.LOG.info("[room3] машина: брёвна {}/{}, говно {}/{}", logs, NEED_LOGS, poop, NEED_POOP);
		if (logs >= NEED_LOGS && poop >= NEED_POOP) startPrint(l);
	}

	private static void startPrint(ServerLevel l) {
		setPhase(P_PRINT);
		for (GovnozavrEntity m : mobs) {
			l.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1, m.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
			m.discard();
		}
		mobs.clear();
		l.playSound(null, MACHINE.x, MACHINE.y, MACHINE.z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2f, 0.6f);
		updatePanel();
		showBoard();
		VadimNaTrone.LOG.info("[room3] всё собрано — печать");
	}

	private static void printTick(ServerLevel l) {
		// корпус трясётся, из трубы дым, гул
		float j = 0.025f;
		machine.setTransformationInterpolationDelay(0);
		machine.setTransformationInterpolationDuration(1);
		machine.setTransformation(new Transformation(new Vector3f((l.getRandom().nextFloat() - 0.5f) * j, (l.getRandom().nextFloat() - 0.5f) * j,
				(l.getRandom().nextFloat() - 0.5f) * j), null, new Vector3f(1f), null));
		if (phaseTick % 2 == 0) l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, MACHINE.x, 67.0, MACHINE.z - 0.5, 1, 0.05, 0.05, 0.05, 0.01);
		if (phaseTick % 10 == 1) l.playSound(null, MACHINE.x, MACHINE.y, MACHINE.z, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 0.6f, 0.6f);
		if (phaseTick % 5 == 0) updatePanel();
		if (phaseTick >= PRINT_LEN) {
			machine.setTransformation(new Transformation(null, null, new Vector3f(1f), null));
			// пластинка выезжает в лоток
			record.setPosRotInterpolationDuration(6);
			record.teleportTo(MACHINE.x, 64.72, FRONT_Z + 0.3);
			l.playSound(null, MACHINE.x, MACHINE.y, MACHINE.z, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.5f, 0.8f);
			l.playSound(null, MACHINE.x, MACHINE.y, MACHINE.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.5f, 1.0f);
			l.playSound(null, MACHINE.x, MACHINE.y, MACHINE.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.5f, 1.5f);
			setPhase(P_WON);
			updatePanel();
			moveBoard(l, false);
			shakeAll(l);
			VadimNaTrone.LOG.info("[room3] пластинка готова — победа");
		}
	}

	private static void spinRecord() {
		if (record == null) return;
		if (phase == P_WON || phase == P_DONE) return;
		float speed = phase == P_PRINT ? 0.9f : 0.12f;
		recordAngle += speed;
		if (machine.level().getGameTime() % 2 == 0) {
			record.setTransformationInterpolationDelay(0);
			record.setTransformationInterpolationDuration(2);
			record.setTransformation(new Transformation(null, recordRot(recordAngle), new Vector3f(0.72f), null));
		}
	}

	private static void updatePanel() {
		if (panel == null) return;
		int dark = 0x3F3F3F;
		MutableComponent c = Component.literal("ПЛАСТИНКА ДЛЯ ВАДИМА  ").withColor(dark);
		int lamp = phase == P_PRINT ? 0xE8B020 : (phase == P_WON || phase == P_DONE) ? 0x3FBF3F : 0xD42A1E;
		c.append(Component.literal("■").withColor(lamp));
		if (phase == P_PRINT) {
			int f = Math.min(16, phaseTick * 16 / PRINT_LEN);
			c.append(Component.literal("\n\nПЕЧАТЬ...\n").withColor(0x1F1F1F));
			c.append(Component.literal("█".repeat(f)).withColor(0x4FAF2F)).append(Component.literal("█".repeat(16 - f)).withColor(0x555555));
			c.append(Component.literal("\n").withColor(dark));
		} else if (phase == P_WON || phase == P_DONE) {
			c.append(Component.literal("\n\nГОТОВО\n").withColor(0x1F1F1F));
		} else {
			c.append(Component.literal("\n"));
			c.append(Component.object(new AtlasSprite(AtlasSprite.DEFAULT_ATLAS, Identifier.withDefaultNamespace("block/oak_log"))));
			c.append(Component.literal(" БРЁВНА  " + logs + " / " + NEED_LOGS + "\n").withColor(dark));
			int fl = Math.round(16 * logs / (float) NEED_LOGS);
			c.append(Component.literal("█".repeat(fl)).withColor(0xA2834F)).append(Component.literal("█".repeat(16 - fl)).withColor(0x555555));
			c.append(Component.literal("\n"));
			c.append(Component.object(new AtlasSprite(AtlasSprite.DEFAULT_ATLAS, VadimNaTrone.id("block/room3/govno"))));
			c.append(Component.literal(" ГОВНО  " + poop + " / " + NEED_POOP + "\n").withColor(dark));
			int fp = Math.round(16 * poop / (float) NEED_POOP);
			c.append(Component.literal("█".repeat(fp)).withColor(0x6B4826)).append(Component.literal("█".repeat(16 - fp)).withColor(0x555555));
		}
		panel.setText(c);
	}

	// ======================================================================= победа

	private static void wonTick(ServerLevel l, MinecraftServer server) {
		int t = phaseTick;
		if (t == COLLAPSE) {
			columns = new ArrayList<>();
			for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) columns.add(new long[]{x, z});
			Collections.shuffle(columns, new Random());
			columnPos = 0;
			l.playSound(null, 60.5, 70, 0, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.BLOCKS, 3f, 0.5f);
			VadimNaTrone.LOG.info("[room3] мир рушится");
		}
		if (t >= COLLAPSE && t % 12 == 0 && t < WIN_MOVE) shakeAll(l);
		if (t >= COLLAPSE && columns != null && columnPos < columns.size()) {
			int per = (int) Math.ceil(columns.size() / (double) COLLAPSE_LEN);
			for (int k = 0; k < per && columnPos < columns.size(); k++) {
				long[] c = columns.get(columnPos++);
				int x = (int) c[0], z = (int) c[1];
				for (int y = FLOOR - 3; y < SKY; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!l.getBlockState(p).isAir()) l.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
				if (l.getRandom().nextInt(6) == 0) l.sendParticles(ParticleTypes.POOF, x + 0.5, FLOOR + 0.5, z + 0.5, 3, 0.3, 0.2, 0.3, 0.01);
			}
			if (t % 4 == 0) {
				long[] c = columns.get(Math.max(0, columnPos - 1));
				l.playSound(null, c[0] + 0.5, FLOOR, c[1] + 0.5, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 2f, 0.6f);
			}
			// всё, под чем пропал пол, — исчезает (машина, табло остаётся на стене)
			for (Entity e : new ArrayList<>(live)) {
				if (board.contains(e) || e.isRemoved()) continue;
				BlockPos under = BlockPos.containing(e.getX(), FLOOR, e.getZ());
				if (l.getBlockState(under).isAir() && l.getBlockState(under.below()).isAir()) remove(e);
			}
		}
		if (t == WIN_MOVE) {
			State st = state(server);
			st.won = true;
			st.setDirty();
			for (ServerPlayer p : here(l)) {
				if (p.isCreative()) continue;
				p.getInventory().clearContent();
				p.setGameMode(GameType.ADVENTURE);
				Room4.place(l, p);   // в бэкрумс: каждому свой сектор, просыпается лёжа
			}
			dead.clear();
			setPhase(P_DONE);
			VadimNaTrone.LOG.info("[room3] пройдена — все в бэкрумсе (комната 4)");
		}
	}

	/** Звук «у уха» конкретному игроку (табло видно издалека — слышно всем одинаково). */
	private static void hear(ServerPlayer p, net.minecraft.sounds.SoundEvent snd, float vol, float pitch) {
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(snd),
				SoundSource.BLOCKS, p.getX(), p.getEyeY(), p.getZ(), vol, pitch, p.getRandom().nextLong()));
	}

	private static void shakeAll(ServerLevel l) {
		for (ServerPlayer p : here(l))
			if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodFinal.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodFinal(1, 0));
	}

	// ======================================================================= табло

	private static void moveBoard(ServerLevel l, boolean down) {
		double to = down ? BOARD_DOWN : BOARD_UP;
		double dy = to - boardY;
		if (Math.abs(dy) < 0.01) return;
		boardY = to;
		for (Display d : board) {
			d.setPosRotInterpolationDuration(50);
			d.teleportTo(d.getX(), d.getY() + dy, d.getZ());
		}
		boardMoving = 50;
		boardFrom = boardY - dy;
		// звук Димы от табло: громкость 2.2 → слышно ~35 блоков, вблизи громко, в дальнем углу тихо
		l.playSound(null, BOARD_X, boardSoundY(), BOARD_Z + 1, ru.vadim.sound.ModSounds.ROOM3_BOARD, SoundSource.BLOCKS, BOARD_VOL, 1f);
	}

	private static void moveBoardInstant(double to) {
		double dy = to - boardY;
		boardY = to;
		if (Math.abs(dy) < 0.01) return;
		for (Display d : board) {
			d.setPosRotInterpolationDuration(0);
			d.teleportTo(d.getX(), d.getY() + dy, d.getZ());
		}
	}

	private static void showBoard() {
		if (digits[0] == null) return;
		int sec = Math.max(0, (timeLeft + 19) / 20);
		String[] g = {"seg_" + (sec / 60 % 10), "seg_colon", "seg_" + (sec % 60 / 10), "seg_" + (sec % 10)};
		for (int k = 0; k < 4; k++) setModel(digits[k], "room1_" + g[k]);
		int n = 0;
		if (machine != null) for (ServerPlayer p : here((ServerLevel) machine.level()))
			if (!p.isSpectator() && !p.isCreative() && !dead.contains(p.getUUID())) n++;
		for (int k = 0; k < 3; k++) if (lifeLamp[k] != null) {
			boolean on = k < n;
			setModel(lifeLamp[k], on ? "room2_bulb_on" : "room2_bulb_off");
			lifeLamp[k].setBrightnessOverride(on ? new Brightness(15, 15) : new Brightness(6, 6));
		}
	}

	// ======================================================================= клиентам

	private static void send(List<ServerPlayer> here) {
		long key = phase * 1000L + (phase == P_GAME ? timeLeft / 100 : 0);
		boolean periodic = phaseTick % 40 == 0;
		if (key == lastSentKey && !periodic) return;
		lastSentKey = key;
		for (ServerPlayer p : here)
			if (ServerPlayNetworking.canSend(p, HubPayloads.Room3State.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.Room3State(phase, phaseTick, timeLeft));
	}

	// ======================================================================= отладка (/vadim room3 …)

	/** /vadim room3 — все в комнату 3, всё заново (ждём первого действия). */
	public static void restart(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		Room1.markWon(server);
		Room2.markWon(server);
		State st = state(server);
		if (st.won) {   // пол уже обрушен — отстроить заново
			st.won = false;
			st.setDirty();
			build(l);
		}
		setup(l);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isCreative()) continue;
			if (p.isPassenger()) p.stopRiding();
			p.getInventory().clearContent();
			p.setGameMode(GameType.SURVIVAL);
			p.teleportTo(l, SPAWN.x, SPAWN.y, SPAWN.z, Set.of(), SPAWN_YAW, 0f, true);
		}
	}

	/** /vadim room3 start — сразу таймер. */
	public static void forceStart(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l != null) startIfWaiting(l, "команда");
	}

	/** /vadim room3 fill — машина почти полная (127 брёвен, 63 говна). */
	public static void fill(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		if (phase == P_WAIT) startIfWaiting(l, "команда fill");
		logs = NEED_LOGS - 1;
		poop = NEED_POOP - 1;
		updatePanel();
	}

	/** /vadim room3 win — сразу печать и победа. */
	public static void forceWin(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null || phase == P_DONE || phase == P_WON) return;
		if (phase == P_WAIT) startIfWaiting(l, "команда win");
		logs = NEED_LOGS;
		poop = NEED_POOP;
		startPrint(l);
	}

	/** /vadim room3 time <сек> — оставить столько времени. */
	public static void setTime(int sec) {
		if (phase == P_GAME) timeLeft = sec * 20;
		showBoard();
	}

	/** /vadim room3 zombie — выпустить Говнозавра. */
	public static void zombie(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l != null) spawnMob(l);
	}

	/** /vadim room3 kit — выдать себе брёвна, говно и кости (тест машины). */
	public static void kit(ServerPlayer p) {
		p.getInventory().placeItemBackInInventory(new ItemStack(Items.OAK_LOG, 64));
		p.getInventory().placeItemBackInInventory(new ItemStack(Room3Blocks.GOVNO_ITEM, 64));
		p.getInventory().placeItemBackInInventory(new ItemStack(Items.BONE, 8));
		p.getInventory().placeItemBackInInventory(new ItemStack(Items.OAK_SAPLING, 4));
	}

	/** /vadim room3 reset — комната 3 не пройдена, мир отстроен заново. */
	public static void reset(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		State st = state(server);
		st.won = false;
		st.setDirty();
		build(l);
		setup(l);
	}

	public static String debug() {
		return "фаза " + phase + ", тик " + phaseTick + ", осталось " + timeLeft / 20 + " с, брёвна " + logs + "/" + NEED_LOGS + ", говно " + poop + "/" + NEED_POOP
				+ ", источник " + (src + 1) + (srcRocket ? " (ракета)" : " (осталось " + srcLeft + ")")
				+ ", мобов " + mobs.size() + ", мёртвых " + dead.size() + ", деревьев " + trees.size();
	}

	// ======================================================================= сущности

	private static void add(ServerLevel l, Entity e) {
		e.addTag(TAG);
		live.add(e);
		l.addFreshEntity(e);
	}

	private static void remove(Entity e) {
		if (e == null) return;
		live.remove(e);
		e.discard();
	}

	private static <T extends Display> T bpart(T d) {
		board.add(d);
		return d;
	}

	private static Display.ItemDisplay item(ServerLevel l, String model, Vec3 at, Quaternionf rot, float scale) {
		Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		ItemStack st = new ItemStack(Items.PAPER);
		st.set(DataComponents.ITEM_MODEL, VadimNaTrone.id(model));
		d.setItemStack(st);
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setPos(at.x, at.y, at.z);
		d.setTransformation(new Transformation(null, rot, new Vector3f(scale), null));
		d.setViewRange(2f);
		add(l, d);
		return d;
	}

	private static void setModel(Display.ItemDisplay d, String model) {
		if (d == null) return;
		ItemStack st = new ItemStack(Items.PAPER);
		st.set(DataComponents.ITEM_MODEL, VadimNaTrone.id(model));
		d.setItemStack(st);
	}

	private static Display.BlockDisplay block(ServerLevel l, BlockState s, Vec3 corner, Vector3f size) {
		Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		d.setBlockState(s);
		d.setPos(corner.x, corner.y, corner.z);
		d.setTransformation(new Transformation(null, null, size, null));
		d.setViewRange(2f);
		add(l, d);
		return d;
	}

	private static Display.TextDisplay text(ServerLevel l, Vec3 at, float scale) {
		Display.TextDisplay t = EntityType.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		t.setPos(at.x, at.y, at.z);
		t.setTransformation(new Transformation(null, null, new Vector3f(scale), null));
		t.setBackgroundColor(0);
		t.setBrightnessOverride(Brightness.FULL_BRIGHT);
		t.setText(Component.empty());
		t.setViewRange(4f);
		add(l, t);
		return t;
	}

	private static Interaction hitbox(ServerLevel l, Vec3 at, float w, float h) {
		Interaction i = EntityType.INTERACTION.create(l, EntitySpawnReason.TRIGGERED);
		i.setPos(at.x, at.y, at.z);
		i.setWidth(w);
		i.setHeight(h);
		i.setResponse(true);
		add(l, i);
		return i;
	}

	public static final class State extends SavedData {
		static final Codec<State> CODEC = Codec.INT.xmap(v -> {
			State s = new State();
			s.won = (v & 1) != 0;
			return s;
		}, s -> s.won ? 1 : 0);
		static final SavedDataType<State> TYPE = new SavedDataType<>("vadim_room3", State::new, CODEC, null);
		boolean won;
	}
}
