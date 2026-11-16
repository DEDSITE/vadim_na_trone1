package ru.vadim.world;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.vadim.VadimNaTrone;
import ru.vadim.item.ModItems;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Комната 1 «Стулья» (этап 4а). Трое привязаны к стульям кругом: один верёвками, остальные цепями (случайно).
 * У тех, кто в цепях, у стула лежат маникюрные ножницы — ими режут верёвки соседа (себя нельзя). Освобождённый
 * ищет болторез: он в кладовке за дверью с кодовым замком. Цифры кода кровью видят только сидящие в цепях
 * (каждый — свои, Entity.broadcastToPlayer через EntityVisibilityMixin), они диктуют их голосом. Болторезом
 * перекусывают цепи. Таймер 5 минут на мониторе, «ракеты» на цепях опускаются ко ртам. Проигрыш — всё заново
 * (без вступления), победа — дверь в комнату 2.
 *
 * Фазы: WAIT (проснулись на стульях, ждём остальных) → INTRO (вступление, взгляд ведёт клиент) → GAME → LOST/WON.
 * Стулья, ракеты, путы, цифры — дисплеи с тегом TAG, пересоздаются на каждом старте сервера.
 */
public final class Room1 {
	public static final String TAG = "vadim_r1";
	public static final String SECRET = "vadim_r1_secret";
	public static final int P_NONE = 0, P_WAIT = 1, P_INTRO = 2, P_GAME = 3, P_LOST = 4, P_WON = 5;
	/** Вступление (тики): правила на мониторе → ракеты спускаются → Вадим ржёт → игра. В 4б — по длине мультика. */
	public static final int INTRO_ROCKETS = 724, INTRO_MOCK = 800, INTRO_END = 1040;
	/** Реплики вступления: тик старта, длина (тики), звук, текст (для справки; на мониторе — мультик Room1Video). */
	private record Line(int at, int len, net.minecraft.sounds.SoundEvent snd, String text) {}
	private static final Line[] LINES = {
			new Line(20, 114, ModSounds.R1_01, "Ну здравствуйте, дегенераты.\nУзнали? Это я. Вадим."),
			new Line(146, 158, ModSounds.R1_02, "Помните, как вы ржали над моей музыкой?\nСмешно было, да, уёбки?"),
			new Line(316, 168, ModSounds.R1_03, "Вы привязаны к стульям.\nКто-то верёвкой, кто-то цепью."),
			new Line(496, 124, ModSounds.R1_04, "Рядом ножницы. Маникюрные.\nДругих нет — всё бабло ушло на студию."),
			new Line(632, 84, ModSounds.R1_06, "У вас пять минут.\nНо сначала… посмотрите наверх."),
			new Line(810, 166, ModSounds.R2_01, "ХА-ХА-ХА-ХА…\nТеперь вы будете настоящими пидорами."),
			new Line(986, 46, ModSounds.R2_02, "Время пошло, дегенераты."),
	};
	public static final int TOTAL = 5 * 60 * 20, PENALTY = 15 * 20;
	private static final int WAIT_MIN = 100, WAIT_MAX = 400, LOST_LEN = 150, RESTART_WAIT = 70;
	private static final int ROPE_TICKS = 60, CHAIN_TICKS = 30;

	public static final int ROLE_ROPE = 1, ROLE_CHAIN = 2, ROLE_FREE = 3;

	// ---- геометрия: пол y=63 (стоят на 64), комната x −6..6, z −5..5, центр стульев (0.5, 0.5)
	static final double CX = 0.5, CZ = 0.5, RADIUS = 1.35, SEAT_Y = 64.5;
	/** Высота рта сидящего: ноги на SEAT_Y−0.6, глаза +1.62. */
	static final double MOUTH_Y = SEAT_Y - 0.6 + 1.62 - 0.17;
	static final double ROCKET_START_TIP = 68.25, ROCKET_HIDDEN_TIP = 70.6;
	/** Монитор на северной стене справа, кодовый замок на восточной стене у двери кладовки. */
	public static final Vec3 MONITOR = new Vec3(3.5, 67.15, -4.88);
	static final Vec3 KEYPAD = new Vec3(6.94, 65.3, 0.35);
	static final BlockPos CLOSET_DOOR = new BlockPos(7, 64, -1);
	static final BlockPos EXIT_DOOR = new BlockPos(-3, 64, -6);
	/** Болторез валяется на полу кладовки, в углу за бочками. */
	static final Vec3 CUTTERS_AT = new Vec3(10.4, 64.03, -2.3);
	static final Vec3 AFTER_WIN_SPAWN = new Vec3(-2.5, 64, -3.5);
	/** Лампы дневного света под потолком: [0] моргает сериями, [1] почти мёртвая (редкие вспышки), [2] кладовка — моргает. */
	static final BlockPos[] TUBES = {new BlockPos(-3, 69, -2), new BlockPos(4, 69, 3), new BlockPos(10, 67, -1)};
	private static final int[][] tubeState = {{60, 0}, {120, 0}, {200, 0}};
	private static final net.minecraft.sounds.SoundEvent[] FLICKER = {ModSounds.ZAVOD_LAMP_FLICKER1, ModSounds.ZAVOD_LAMP_FLICKER2,
			ModSounds.ZAVOD_LAMP_FLICKER3, ModSounds.ZAVOD_LAMP_FLICKER4, ModSounds.ZAVOD_LAMP_FLICKER5};
	private static final int[] FLICKER_LEN = {16, 28, 12, 14, 14};
	/** Ракеты опускаются рывками: шаг раз в 15 с, со звоном цепи. */
	private static final int STEP = 15 * 20;

	static final Seat[] SEATS = new Seat[3];
	static {
		for (int i = 0; i < 3; i++) {
			double a = Math.toRadians(-90 + i * 120);
			double x = CX + Math.cos(a) * RADIUS, z = CZ + Math.sin(a) * RADIUS;
			double dx = CX - x, dz = CZ - z;
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			SEATS[i] = new Seat(i, new Vec3(x, SEAT_Y, z), yaw);
		}
	}

	record Seat(int idx, Vec3 pos, float yaw) {
		Vec3 facing() {
			double r = Math.toRadians(yaw);
			return new Vec3(-Math.sin(r), 0, Math.cos(r));
		}
		Vec3 mouth() { return new Vec3(pos.x, MOUTH_Y, pos.z).add(facing().scale(0.28)); }
	}

	// ---- состояние раунда
	private static int phase = P_NONE, phaseTick = 0, timeLeft = TOTAL;
	private static boolean withIntro = true;
	private static final Map<UUID, Integer> seatOf = new HashMap<>();
	private static final Map<UUID, Integer> role = new HashMap<>();
	private static final Map<UUID, Integer> pendingSeat = new HashMap<>();
	private static final int[] code = new int[4];
	private static boolean closetOpen = false;
	private static final java.util.Set<UUID> skipVotes = new java.util.HashSet<>();

	// ---- сущности
	private static final List<Entity> live = new ArrayList<>();
	private static final Display.ItemDisplay[] seatEnt = new Display.ItemDisplay[3];
	private static final Display.ItemDisplay[] bindEnt = new Display.ItemDisplay[3];
	private static final Display.ItemDisplay[] rocketEnt = new Display.ItemDisplay[3];
	private static final List<List<Display.BlockDisplay>> chainEnt = new ArrayList<>();
	private static final Display.ItemDisplay[] scissorsShow = new Display.ItemDisplay[3];
	private static final Interaction[] scissorsHit = new Interaction[3];
	private static Display.ItemDisplay cuttersShow;
	private static Interaction cuttersHit, keypadHit;
	private static final Display.ItemDisplay[] timerDigits = new Display.ItemDisplay[5];
	private static Display.TextDisplay monitorText;
	/** Секретные цифры: сущность → кто её видит. */
	private static final Map<Entity, UUID> secretViewer = new HashMap<>();
	private static double rocketTip = ROCKET_HIDDEN_TIP;

	// ---- резка: кого режут → кто режет, прогресс, последний тик нажатия
	private static final class Cut {
		UUID by;
		int progress;
		long last;
		boolean chain;
	}
	private static final Map<UUID, Cut> cuts = new HashMap<>();
	private static final Map<UUID, Integer> flash = new HashMap<>();
	private static final Map<UUID, Integer> flashLeft = new HashMap<>();
	private static final Map<UUID, Long> lastHint = new HashMap<>();
	private static int wonShow = 0;

	// коды подсказок (совпадают с client/zavod/ZavodHud)
	public static final int H_ROPE = 20, H_TAKE_SC = 21, H_USE_SC = 22, H_CUT = 23, H_CODE = 24, H_FREE = 25, H_PAD = 26,
			H_TAKE_CUT = 27, H_USE_CUT = 28, H_BITE = 29, H_NO_CHAIN = 30, H_NO_SELF = 31, H_NO_ROPE_CUT = 32, H_WRONG = 33,
			H_WON = 34, H_BEING_CUT = 35, H_WAIT = 36, H_HANDS = 37;

	private Room1() {}

	// ======================================================================= регистрация

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Room1::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> leave(h.getPlayer()));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.dimension() != Places.ROOMS) return InteractionResult.PASS;
			boolean ours = entity.getTags().contains(TAG) || entity instanceof Player;
			if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return ours ? InteractionResult.SUCCESS : InteractionResult.PASS;
			if (hand != InteractionHand.MAIN_HAND) return ours ? InteractionResult.SUCCESS : InteractionResult.PASS;
			if (entity instanceof ServerPlayer target) { pressOn(sp, target); return InteractionResult.SUCCESS; }
			for (int i = 0; i < 3; i++) if (entity == scissorsHit[i]) { takeScissors(sp, i); return InteractionResult.SUCCESS; }
			if (entity == cuttersHit) { takeCutters(sp); return InteractionResult.SUCCESS; }
			if (entity == keypadHit) { openKeypad(sp); return InteractionResult.SUCCESS; }
			return ours ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		// ПКМ в воздух ножницами — «на себя»
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.dimension() != Places.ROOMS || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			ItemStack st = player.getItemInHand(hand);
			if (!st.is(ModItems.SCISSORS) && !st.is(ModItems.CUTTERS)) return InteractionResult.PASS;
			if (!level.isClientSide() && player instanceof ServerPlayer sp) pressSelf(sp);
			return InteractionResult.SUCCESS;
		});
		ServerPlayNetworking.registerGlobalReceiver(HubPayloads.Room1Code.TYPE, (p, ctx) -> ctx.server().execute(() -> enterCode(ctx.player(), p.code())));
		ServerPlayNetworking.registerGlobalReceiver(HubPayloads.Room1Skip.TYPE, (p, ctx) -> ctx.server().execute(() -> voteSkip(ctx.player())));
	}

	// ======================================================================= видимость цифр

	/** Видит ли игрок секретную сущность (цифру кода). Вызывается из EntityVisibilityMixin. */
	public static boolean canSee(Entity e, ServerPlayer p) {
		UUID v = secretViewer.get(e);
		return v != null && v.equals(p.getUUID());
	}

	public static boolean isStale(Entity e) {
		return e.getTags().contains(TAG) && !live.contains(e);
	}

	// ======================================================================= сохранение

	public static boolean won(MinecraftServer s) {
		ServerLevel l = Places.rooms(s);
		return l != null && l.getDataStorage().computeIfAbsent(State.TYPE).won;
	}

	private static void setWon(ServerLevel l, boolean w) {
		State st = l.getDataStorage().computeIfAbsent(State.TYPE);
		st.won = w;
		st.setDirty();
	}

	// ======================================================================= старт сервера

	/** После постройки комнаты: стулья, ракеты, монитор, замок, болторез. Раунд начнётся, когда кто-то придёт. */
	public static void setup(ServerLevel l) {
		for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) l.setChunkForced(cx, cz, true);
		List<Entity> old = new ArrayList<>();
		for (Entity e : l.getAllEntities()) if (e.getTags().contains(TAG)) old.add(e);
		old.forEach(Entity::discard);
		live.clear();
		secretViewer.clear();
		chainEnt.clear();
		seatOf.clear();
		role.clear();
		cuts.clear();
		phase = P_NONE;
		boolean w = l.getDataStorage().computeIfAbsent(State.TYPE).won;
		for (int i = 0; i < 3; i++) {
			Seat s = SEATS[i];
			// модель предмета рисуется центром в точке дисплея: стул 0..16 px → низ на 64.0, сиденье ~64.53
			Display.ItemDisplay chair = item(l, "room1_chair", s.pos, new Quaternionf(), 1f);
			chair.setYRot(s.yaw);
			chair.setViewRange(2f);
			seatEnt[i] = chair;
			List<Display.BlockDisplay> col = new ArrayList<>();
			Vec3 m = s.mouth();
			for (int k = 0; k < 7; k++) {
				Display.BlockDisplay c = EntityType.BLOCK_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
				if (c == null) continue;
				c.setBlockState(Blocks.IRON_CHAIN.defaultBlockState());
				c.setPos(m.x - 0.5, ROCKET_START_TIP + 1.875 + k, m.z - 0.5);
				c.setViewRange(2f);
				add(l, c);
				col.add(c);
			}
			chainEnt.add(col);
			rocketEnt[i] = item(l, "room1_rocket", new Vec3(m.x, ROCKET_START_TIP + 1.375, m.z), new Quaternionf(), 1f);
			rocketEnt[i].setViewRange(2f);
		}
		rocketTip = w ? ROCKET_HIDDEN_TIP : ROCKET_START_TIP;
		placeRockets(rocketTip, 0);
		// монитор: экран + 5 знаков таймера + текст вступления
		item(l, "room1_monitor", MONITOR, new Quaternionf(), 2.3f).setBrightnessOverride(new Brightness(15, 15));
		String[] glyph = {"seg_0", "seg_5", "seg_colon", "seg_0", "seg_0"};
		for (int k = 0; k < 5; k++) {
			Display.ItemDisplay d = item(l, "room1_" + glyph[k], MONITOR.add(-0.68 + k * 0.34, 0.1, 0.1),
					new Quaternionf().rotateY((float) Math.PI), 0.42f);
			d.setBrightnessOverride(new Brightness(15, 15));
			timerDigits[k] = d;
		}
		monitorText = EntityType.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		if (monitorText != null) {
			monitorText.setPos(MONITOR.x, MONITOR.y - 0.45, MONITOR.z + 0.11);
			monitorText.setTransformation(new Transformation(null, null, new Vector3f(0.32f), null));   // текст смотрит на +Z (в комнату)
			monitorText.setBackgroundColor(0);
			monitorText.setBrightnessOverride(Brightness.FULL_BRIGHT);
			monitorText.setText(Component.empty());
			add(l, monitorText);
		}
		// кодовый замок
		Display.ItemDisplay pad = item(l, "room1_keypad", KEYPAD, new Quaternionf().rotateY((float) (Math.PI / 2)), 0.62f);
		pad.setBrightnessOverride(new Brightness(9, 0));
		keypadHit = hitbox(l, KEYPAD.add(-0.25, -0.45, 0), 0.5f, 0.85f);
		closetOpen = w;
		setDoor(l, CLOSET_DOOR, w, false);
		setDoor(l, EXIT_DOOR, w, false);
		putCutters(l);
		decals(l);
		VadimNaTrone.LOG.info("[room1] готова (пройдена: {})", w);
	}

	private static void add(ServerLevel l, Entity e) {
		e.addTag(TAG);
		live.add(e);
		l.addFreshEntity(e);
	}

	private static Display.ItemDisplay item(ServerLevel l, String model, Vec3 at, Quaternionf rot, float scale) {
		Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		ItemStack st = new ItemStack(Items.PAPER);
		st.set(DataComponents.ITEM_MODEL, VadimNaTrone.id(model));
		d.setItemStack(st);
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setPos(at.x, at.y, at.z);
		d.setTransformation(new Transformation(null, rot, new Vector3f(scale), null));
		d.setViewRange(1f);
		add(l, d);
		return d;
	}

	private static void setModel(Display.ItemDisplay d, String model) {
		ItemStack st = new ItemStack(Items.PAPER);
		st.set(DataComponents.ITEM_MODEL, VadimNaTrone.id(model));
		d.setItemStack(st);
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

	private static void remove(Entity e) {
		if (e == null) return;
		e.discard();
		live.remove(e);
		secretViewer.remove(e);
	}

	/** Кровь на полу и стенах (видят все). */
	private static void decals(ServerLevel l) {
		java.util.Random r = new java.util.Random(1337);
		double[][] floor = {{0.5, 0.5, 2.6}, {2.2, -1.6, 1.4}, {-1.8, 2.3, 1.6}, {3.4, 3.4, 1.2}, {-4.6, -1.2, 1.8}, {-4.2, -3.8, 1.1},
				{1.0, 3.8, 0.9}, {4.8, -2.8, 1.3}, {9.4, -0.6, 1.0}, {-2.6, -4.2, 0.8}};
		for (double[] f : floor) {
			Quaternionf q = new Quaternionf().rotateY(r.nextFloat() * 6.28f).rotateX((float) (Math.PI / 2));
			item(l, "room1_splat_" + (1 + r.nextInt(4)), new Vec3(f[0], 64.01 + r.nextFloat() * 0.004, f[1]), q, (float) f[2]);
		}
		// стены: (x, y, z, нормаль-ось)
		double[][] wall = {{-5.99, 65.4, -2.0, 0}, {-5.99, 66.8, 2.4, 0}, {6.99, 65.9, 3.0, 1}, {-1.0, 66.2, -4.99, 2}, {4.6, 65.0, 5.99, 3},
				{-4.0, 64.9, 5.99, 3}};
		for (double[] wv : wall) {
			double a = switch ((int) wv[3]) {
				case 0 -> Math.atan2(-1, 0);      // нормаль +X (западная стена)
				case 1 -> Math.atan2(1, 0);       // нормаль −X (восточная)
				case 2 -> Math.atan2(0, -1);      // нормаль +Z (северная)
				default -> Math.atan2(0, 1);      // нормаль −Z (южная)
			};
			item(l, "room1_splat_" + (1 + r.nextInt(4)), new Vec3(wv[0], wv[1], wv[2]), new Quaternionf().rotateY((float) a).rotateZ(r.nextFloat() * 6.28f), 1.1f + r.nextFloat());
		}
	}

	// ======================================================================= ракеты

	/** Остриё ракеты (низ) на высоте tip; interp — за сколько тиков доехать. */
	private static void placeRockets(double tip, int interp) {
		rocketTip = tip;
		for (int i = 0; i < 3; i++) {
			Display.ItemDisplay r = rocketEnt[i];
			if (r == null) continue;
			Vec3 m = SEATS[i].mouth();
			// модель колпаком вверх (0..30 px), дисплей переворачивает: остриё = pos − 1.375, верх = pos + 0.5
			double y = tip + 1.375;
			r.setTransformationInterpolationDelay(0);
			r.setTransformationInterpolationDuration(interp);
			r.setTransformation(new Transformation(new Vector3f(0, (float) (y - r.getY()), 0), new Quaternionf().rotateX((float) Math.PI), new Vector3f(1f), null));
			List<Display.BlockDisplay> col = chainEnt.get(i);
			for (int k = 0; k < col.size(); k++) {
				Display.BlockDisplay c = col.get(k);
				c.setTransformationInterpolationDelay(0);
				c.setTransformationInterpolationDuration(interp);
				c.setTransformation(new Transformation(new Vector3f(0, (float) (y + 0.45 + k - c.getY()), 0), null, null, null));
			}
		}
	}

	private static double gameTip() {
		int steps = TOTAL / STEP;
		int done = Math.min(steps, (TOTAL - Math.max(0, timeLeft)) / STEP);
		double p = done / (double) steps;
		return ROCKET_START_TIP + (MOUTH_Y + 0.02 - ROCKET_START_TIP) * p;
	}

	// ======================================================================= игроки

	/** Игрок попал в комнаты (после газа или при перезаходе). Садим на свободный стул через пару тиков. */
	public static void join(ServerPlayer p) {
		if (won(p.level().getServer())) return;
		pendingSeat.put(p.getUUID(), 3);
	}

	private static void leave(ServerPlayer p) {
		seatOf.remove(p.getUUID());
		role.remove(p.getUUID());
		pendingSeat.remove(p.getUUID());
		cuts.remove(p.getUUID());
	}

	private static int freeSeat() {
		boolean[] used = new boolean[3];
		for (int s : seatOf.values()) used[s] = true;
		for (int i = 0; i < 3; i++) if (!used[i]) return i;
		return -1;
	}

	private static void sit(ServerPlayer p, int i) {
		ServerLevel l = Places.rooms(p.level().getServer());
		if (l == null) return;
		Seat s = SEATS[i];
		seatOf.put(p.getUUID(), i);
		if (p.isPassenger()) p.stopRiding();
		p.teleportTo(l, s.pos.x, s.pos.y - 0.6, s.pos.z, java.util.Set.of(), s.yaw, 10f, true);
		p.startRiding(seatEnt[i], true, true);
	}

	private static void standUp(ServerPlayer p) {
		Integer i = seatOf.get(p.getUUID());
		if (p.isPassenger()) p.stopRiding();
		if (i != null) {
			Seat s = SEATS[i];
			Vec3 to = s.pos.add(s.facing().scale(-0.2)).add(s.facing().scale(0.9).yRot((float) Math.PI / 2));
			p.teleportTo(to.x, 64.0, to.z);
		}
	}

	private static boolean tied(UUID id) {
		Integer r = role.get(id);
		return r != null && (r == ROLE_ROPE || r == ROLE_CHAIN);
	}

	private static List<ServerPlayer> here(MinecraftServer s) {
		List<ServerPlayer> out = new ArrayList<>();
		ServerLevel l = Places.rooms(s);
		if (l == null) return out;
		for (ServerPlayer p : l.players()) if (!p.isSpectator()) out.add(p);
		return out;
	}

	// ======================================================================= раунд

	/** Раздать роли, код, ножницы; ракеты наверх. */
	private static void startRound(MinecraftServer server, boolean intro) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		List<ServerPlayer> ps = here(server);
		for (ServerPlayer p : ps) if (!seatOf.containsKey(p.getUUID())) { int f = freeSeat(); if (f >= 0) sit(p, f); }
		role.clear();
		cuts.clear();
		skipVotes.clear();
		List<ServerPlayer> seated = new ArrayList<>(ps.stream().filter(p -> seatOf.containsKey(p.getUUID())).toList());
		Collections.shuffle(seated);
		for (int k = 0; k < seated.size(); k++) role.put(seated.get(k).getUUID(), k == 0 ? ROLE_ROPE : ROLE_CHAIN);
		for (ServerPlayer p : ps) if (!role.containsKey(p.getUUID())) role.put(p.getUUID(), ROLE_FREE);
		for (ServerPlayer p : ps) clearTools(p);
		java.util.Random r = new java.util.Random();
		for (int k = 0; k < 4; k++) code[k] = r.nextInt(10);
		closetOpen = false;
		setDoor(l, CLOSET_DOOR, false, false);
		setDoor(l, EXIT_DOOR, false, false);
		putCutters(l);
		refreshBindings(l);
		placeScissors(l);
		placeDigits(l, seated);
		timeLeft = TOTAL;
		withIntro = intro;
		setPhase(intro ? P_INTRO : P_GAME);
		placeRockets(intro ? ROCKET_HIDDEN_TIP : ROCKET_START_TIP, 0);
		showTimer();
		VadimNaTrone.LOG.info("[room1] раунд: игроков {}, код {}{}{}{}, вступление {}", seated.size(), code[0], code[1], code[2], code[3], intro);
	}

	private static void setPhase(int ph) {
		phase = ph;
		phaseTick = 0;
	}

	/** Путы на стульях: верёвки / цепи / ничего. */
	private static void refreshBindings(ServerLevel l) {
		for (int i = 0; i < 3; i++) { remove(bindEnt[i]); bindEnt[i] = null; }
		for (var e : seatOf.entrySet()) {
			Integer r = role.get(e.getKey());
			if (r == null || (r != ROLE_ROPE && r != ROLE_CHAIN)) continue;
			int i = e.getValue();
			Display.ItemDisplay b = item(l, r == ROLE_ROPE ? "room1_ropes" : "room1_chains", SEATS[i].pos, new Quaternionf(), 1f);
			b.setYRot(SEATS[i].yaw);
			b.setViewRange(2f);
			bindEnt[i] = b;
		}
	}

	/** Ножницы на полу справа от стульев тех, кто в цепях. */
	private static void placeScissors(ServerLevel l) {
		for (int i = 0; i < 3; i++) { remove(scissorsShow[i]); remove(scissorsHit[i]); scissorsShow[i] = null; scissorsHit[i] = null; }
		for (var e : seatOf.entrySet()) {
			// ножницы у тех, кто в цепях; если в комнате один (тест) — у него
			if (!Integer.valueOf(ROLE_CHAIN).equals(role.get(e.getKey())) && seatOf.size() > 1) continue;
			int i = e.getValue();
			Seat s = SEATS[i];
			Vec3 right = s.facing().yRot((float) (-Math.PI / 2));
			Vec3 at = new Vec3(s.pos.x, 64.03, s.pos.z).add(right.scale(0.75)).add(s.facing().scale(0.25));
			Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
			d.setItemStack(new ItemStack(ModItems.SCISSORS));
			d.setItemTransform(ItemDisplayContext.NONE);
			d.setPos(at.x, at.y, at.z);
			d.setTransformation(new Transformation(null, new Quaternionf().rotateY(i * 1.3f).rotateX((float) (Math.PI / 2)), new Vector3f(0.45f), null));
			add(l, d);
			scissorsShow[i] = d;
			scissorsHit[i] = hitbox(l, at.add(0, -0.03, 0), 0.6f, 0.25f);
		}
	}

	private static void putCutters(ServerLevel l) {
		remove(cuttersShow);
		remove(cuttersHit);
		Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		d.setItemStack(new ItemStack(ModItems.CUTTERS));
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setPos(CUTTERS_AT.x, CUTTERS_AT.y, CUTTERS_AT.z);
		d.setTransformation(new Transformation(null, new Quaternionf().rotateY(2.2f).rotateX((float) (Math.PI / 2)), new Vector3f(0.75f), null));
		add(l, d);
		cuttersShow = d;
		cuttersHit = hitbox(l, CUTTERS_AT.add(0, -0.03, 0), 0.8f, 0.25f);
	}

	/**
	 * Цифры кода кровью: каждому в цепях — свои (при двоих в цепях — по две, при одном — все четыре),
	 * на стенах в поле зрения его стула (±25–65° от взгляда). Рядом — метка порядка.
	 */
	private static void placeDigits(ServerLevel l, List<ServerPlayer> seated) {
		List<Entity> old = new ArrayList<>(secretViewer.keySet());
		old.forEach(Room1::remove);
		List<ServerPlayer> chains = seated.stream().filter(p -> Integer.valueOf(ROLE_CHAIN).equals(role.get(p.getUUID()))).toList();
		if (chains.isEmpty()) chains = seated;   // один в комнате (тест) — видит сам
		if (chains.isEmpty()) return;
		int per = 4 / chains.size();
		int k = 0;
		for (int c = 0; c < chains.size(); c++) {
			ServerPlayer p = chains.get(c);
			Seat s = SEATS[seatOf.get(p.getUUID())];
			int n = c == chains.size() - 1 ? 4 - k : per;
			for (int j = 0; j < n; j++, k++) {
				double off = n == 1 ? 30 : -55 + 110.0 * j / (n - 1);
				Vec3 dir = s.facing().yRot((float) Math.toRadians(off));
				double eye = 65.0 + (j % 2) * 0.9;
				Vec3[] hit = wallHit(s.pos, dir);
				Vec3 at = new Vec3(hit[0].x, eye, hit[0].z);
				Vec3 nrm = hit[1];
				float a = (float) Math.atan2(-nrm.x, -nrm.z);
				float tilt = (float) ((k * 37 % 20 - 10) * Math.PI / 180);
				Vec3 side = new Vec3(nrm.z, 0, -nrm.x);   // вдоль стены
				secret(l, "room1_digit_" + code[k], at, new Quaternionf().rotateY(a).rotateZ(tilt), 0.95f, p.getUUID());
				secret(l, "room1_mark_" + (k + 1), at.add(side.scale(-0.62)).add(0, 0.25, 0), new Quaternionf().rotateY(a), 0.5f, p.getUUID());
			}
		}
	}

	/** Цифра, которую видит только viewer: зритель записан до добавления в мир (видимость проверяется при старте отслеживания). */
	private static void secret(ServerLevel l, String model, Vec3 at, Quaternionf rot, float scale, UUID viewer) {
		Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		ItemStack st = new ItemStack(Items.PAPER);
		st.set(DataComponents.ITEM_MODEL, VadimNaTrone.id(model));
		d.setItemStack(st);
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setPos(at.x, at.y, at.z);
		d.setTransformation(new Transformation(null, rot, new Vector3f(scale), null));
		d.setViewRange(2f);
		d.addTag(SECRET);
		secretViewer.put(d, viewer);
		add(l, d);
	}

	/** Точка на внутренней стене комнаты по лучу из (from) и нормаль стены. */
	private static Vec3[] wallHit(Vec3 from, Vec3 dir) {
		double best = 1e9;
		Vec3 n = new Vec3(0, 0, 1);
		double[][] planes = {{-6.0, 1, 0}, {7.0, -1, 0}, {-5.0, 0, 1}, {6.0, 0, -1}};   // x=-6 (нормаль +X), x=7, z=-5, z=6
		for (double[] pl : planes) {
			double t;
			if (pl[1] != 0) { if (Math.abs(dir.x) < 1e-6) continue; t = (pl[0] - from.x) / dir.x; }
			else { if (Math.abs(dir.z) < 1e-6) continue; t = (pl[0] - from.z) / dir.z; }
			if (t > 0.1 && t < best) { best = t; n = new Vec3(pl[1], 0, pl[2]); }
		}
		Vec3 p = from.add(dir.scale(best)).add(n.scale(0.02));
		// не залезать в дверь кладовки и на монитор
		return new Vec3[]{p, n};
	}

	// ======================================================================= такт

	private static void tick(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null || live.isEmpty()) return;
		long now = l.getGameTime();
		List<ServerPlayer> ps = here(server);

		// отложенная посадка (после межмирового телепорта)
		if (!pendingSeat.isEmpty()) {
			var it = pendingSeat.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				if (e.getValue() > 1) { e.setValue(e.getValue() - 1); continue; }
				it.remove();
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p == null || p.level() != l) continue;
				if (phase == P_WON || won(server)) continue;
				int f = seatOf.containsKey(p.getUUID()) ? seatOf.get(p.getUUID()) : freeSeat();
				if (f < 0) { role.put(p.getUUID(), ROLE_FREE); continue; }
				sit(p, f);
				if (phase == P_GAME || phase == P_INTRO) {
					// опоздавший: в цепи, без цифр
					role.put(p.getUUID(), ROLE_CHAIN);
					refreshBindings(l);
				} else if (phase == P_NONE) setPhase(P_WAIT);
			}
		}
		if (ps.isEmpty()) return;
		lampsTick(l);
		if (phase == P_NONE && !won(server)) {
			boolean anySeated = ps.stream().anyMatch(p -> seatOf.containsKey(p.getUUID()));
			if (anySeated) setPhase(P_WAIT);
		}
		phaseTick++;

		// привязанные не встают: сбросило со стула — обратно
		for (ServerPlayer p : ps) {
			Integer i = seatOf.get(p.getUUID());
			if (i == null) continue;
			boolean mustSit = tied(p.getUUID()) || phase == P_WAIT || phase == P_INTRO || phase == P_LOST;
			if (mustSit && p.getVehicle() != seatEnt[i]) sit(p, i);
		}

		switch (phase) {
			case P_WAIT -> {
				boolean allIn = server.getPlayerList().getPlayers().stream().filter(p -> !p.isSpectator())
						.allMatch(p -> seatOf.containsKey(p.getUUID()));
				if ((phaseTick >= WAIT_MIN && allIn) || phaseTick >= WAIT_MAX) startRound(server, withIntro);
			}
			case P_INTRO -> {
				if (phaseTick == INTRO_ROCKETS) placeRockets(ROCKET_START_TIP, 60);
				if (phaseTick == INTRO_ROCKETS + 4)
					l.playSound(null, CX, 69.5, CZ, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.5f);
				for (Line ln : LINES) if (phaseTick == ln.at) say(l, ln.snd);
				// на мониторе — мультик Вадима (клиент, Room1Video), без субтитров (решение Димы)
				if (phaseTick >= INTRO_END) { setPhase(P_GAME); showTimer(); }
			}
			case P_GAME -> gameTick(l, ps, now);
			case P_LOST -> {
				if (phaseTick >= LOST_LEN) {
					// всех обратно на стулья и заново, без вступления
					for (ServerPlayer p : ps) {
						clearTools(p);
						Integer i = seatOf.get(p.getUUID());
						if (i == null) { int f = freeSeat(); if (f >= 0) sit(p, f); }
						else if (p.getVehicle() != seatEnt[i]) sit(p, i);
					}
					withIntro = false;
					role.clear();
					refreshBindings(l);
					setPhase(P_WAIT);
					phaseTick = WAIT_MIN - RESTART_WAIT;
				}
			}
			case P_WON -> { if (wonShow > 0) wonShow--; }
			default -> {}
		}

		// состояние клиентам: раз в секунду или при смене фазы
		for (ServerPlayer p : ps) sendState(p, now % 20 == 0);
		hints(ps, now);
	}

	private static void lampsTick(ServerLevel l) {
		java.util.Random r = new java.util.Random();
		for (int i = 0; i < TUBES.length; i++) {
			int[] st = tubeState[i];
			BlockPos p = TUBES[i];
			BlockState s = l.getBlockState(p);
			if (!s.is(ru.vadim.block.ZavodBlocks.TUBE)) continue;
			boolean base = i != 1;   // мёртвая по умолчанию выключена
			boolean lit = s.getValue(ru.vadim.block.ZavodBlocks.Tube.LIT);
			if (st[1] > 0) {
				st[1]--;
				boolean want = st[1] == 0 ? base : (r.nextInt(100) < 40) != lit;
				if (want != lit) l.setBlock(p, s.setValue(ru.vadim.block.ZavodBlocks.Tube.LIT, want), Block.UPDATE_CLIENTS);
			} else if (--st[0] <= 0) {
				int v = r.nextInt(FLICKER.length);
				st[1] = FLICKER_LEN[v];
				st[0] = base ? 200 + r.nextInt(500) : 120 + r.nextInt(300);
				l.playSound(null, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, FLICKER[v], SoundSource.BLOCKS, 0.45f, 0.9f + r.nextFloat() * 0.2f);
				l.setBlock(p, s.setValue(ru.vadim.block.ZavodBlocks.Tube.LIT, !lit), Block.UPDATE_CLIENTS);
			} else if (st[1] == 0 && lit != base) l.setBlock(p, s.setValue(ru.vadim.block.ZavodBlocks.Tube.LIT, base), Block.UPDATE_CLIENTS);
		}
	}

	private static final Map<UUID, Integer> sentPhase = new HashMap<>();

	private static void sendState(ServerPlayer p, boolean force) {
		Integer last = sentPhase.get(p.getUUID());
		int key = phase * 2 + (tied(p.getUUID()) ? 1 : 0);
		if (!force && last != null && last == key) return;
		sentPhase.put(p.getUUID(), key);
		Integer i = seatOf.get(p.getUUID());
		boolean sitting = i != null && p.getVehicle() == seatEnt[i];
		float yaw = sitting ? SEATS[i].yaw : Float.NaN;
		boolean tiedNow = tied(p.getUUID()) || (i != null && (phase == P_WAIT || phase == P_INTRO || phase == P_LOST));
		if (ServerPlayNetworking.canSend(p, HubPayloads.Room1State.TYPE))
			ServerPlayNetworking.send(p, new HubPayloads.Room1State(phase, phaseTick, timeLeft, yaw, tiedNow, withIntro));
	}

	private static void say(ServerLevel l, net.minecraft.sounds.SoundEvent snd) {
		l.playSound(null, MONITOR.x, MONITOR.y, MONITOR.z, snd, SoundSource.RECORDS, 1f, 1f);
	}

	/** Пропуск: глушим уже звучащую реплику. */
	private static void hush(ServerLevel l) {
		for (ServerPlayer p : here(l.getServer()))
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, SoundSource.RECORDS));
	}

	private static void gameTick(ServerLevel l, List<ServerPlayer> ps, long now) {
		timeLeft--;
		int elapsed = TOTAL - timeLeft;
		if (elapsed % STEP == 0 && elapsed > 0) {
			placeRockets(gameTip(), 6);
			for (Seat s : SEATS) l.playSound(null, s.mouth().x, rocketTip + 2, s.mouth().z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.9f, 0.55f + l.random.nextFloat() * 0.2f);
		}
		if (timeLeft % 20 == 0) showTimer();
		// резка
		var it = cuts.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			Cut c = e.getValue();
			ServerPlayer target = l.getServer().getPlayerList().getPlayer(e.getKey());
			ServerPlayer by = l.getServer().getPlayerList().getPlayer(c.by);
			if (target == null || by == null || now - c.last > 6) { it.remove(); continue; }
			c.progress++;
			if (c.progress % (c.chain ? 6 : 10) == 2) {
				l.playSound(null, target.getX(), target.getY() + 0.9, target.getZ(), c.chain ? SoundEvents.CHAIN_HIT : SoundEvents.SHEEP_SHEAR,
						SoundSource.PLAYERS, 0.9f, c.chain ? 0.7f : 1.3f + l.random.nextFloat() * 0.3f);
				by.swing(InteractionHand.MAIN_HAND, true);
			}
			if (c.progress >= (c.chain ? CHAIN_TICKS : ROPE_TICKS)) {
				it.remove();
				release(l, target, c.chain);
			}
		}
		// победа: никто не привязан
		boolean anyTied = ps.stream().anyMatch(p -> tied(p.getUUID()));
		if (!anyTied && !ps.isEmpty()) { win(l, ps); return; }
		if (timeLeft <= 0) lose(l, ps);
	}

	/** Цифры таймера на мониторе. Во вступлении спрятаны — там идёт мультик. */
	private static void showTimer() {
		if (phase == P_INTRO) {
			for (Display.ItemDisplay d : timerDigits) if (d != null) d.setItemStack(ItemStack.EMPTY);
			return;
		}
		int sec = Math.max(0, (timeLeft + 19) / 20);
		int m = sec / 60, s = sec % 60;
		String[] g = {"seg_" + (m / 10), "seg_" + (m % 10), "seg_colon", "seg_" + (s / 10), "seg_" + (s % 10)};
		for (int k = 0; k < 5; k++) if (timerDigits[k] != null) setModel(timerDigits[k], "room1_" + g[k]);
	}

	private static void release(ServerLevel l, ServerPlayer p, boolean chain) {
		role.put(p.getUUID(), ROLE_FREE);
		Integer i = seatOf.get(p.getUUID());
		if (i != null) { remove(bindEnt[i]); bindEnt[i] = null; }
		standUp(p);
		if (chain) {
			l.playSound(null, p.getX(), p.getY() + 0.9, p.getZ(), ModSounds.ZAVOD_CHAIN_SNAP, SoundSource.PLAYERS, 0.9f, 1.1f);
			l.playSound(null, p.getX(), p.getY() + 0.9, p.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
		} else l.playSound(null, p.getX(), p.getY() + 0.9, p.getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
		l.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 0.8, p.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
		VadimNaTrone.LOG.info("[room1] {} свободен ({})", p.getName().getString(), chain ? "цепь" : "верёвка");
	}

	private static void win(ServerLevel l, List<ServerPlayer> ps) {
		setPhase(P_WON);
		wonShow = 160;
		setWon(l, true);
		placeRockets(ROCKET_HIDDEN_TIP, 80);
		setDoor(l, EXIT_DOOR, true, true);
		say(l, ModSounds.R_WIN);
		l.playSound(null, CX, 69, CZ, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.6f);
		for (ServerPlayer p : ps) clearTools(p);
		VadimNaTrone.LOG.info("[room1] победа, осталось {} с", timeLeft / 20);
	}

	private static void lose(ServerLevel l, List<ServerPlayer> ps) {
		setPhase(P_LOST);
		say(l, ModSounds.R_LOSE);
		placeRockets(MOUTH_Y + 0.02, 10);
		cuts.clear();
		VadimNaTrone.LOG.info("[room1] проигрыш");
	}

	private static void clearTools(ServerPlayer p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(ModItems.SCISSORS) || st.is(ModItems.CUTTERS)) inv.setItem(i, ItemStack.EMPTY);
		}
	}

	// ======================================================================= действия

	private static void flash(ServerPlayer p, int hint) {
		flash.put(p.getUUID(), hint);
		flashLeft.put(p.getUUID(), 50);
	}

	/** ПКМ по другому игроку (держат — повторяется раз в 4 тика). */
	private static void pressOn(ServerPlayer by, ServerPlayer target) {
		if (phase != P_GAME) return;
		ItemStack st = by.getMainHandItem();
		boolean sc = st.is(ModItems.SCISSORS), cu = st.is(ModItems.CUTTERS);
		Integer tr = role.get(target.getUUID());
		if (tr == null || (tr != ROLE_ROPE && tr != ROLE_CHAIN)) return;
		if (!sc && !cu) { flash(by, H_HANDS); return; }
		if (sc && tr == ROLE_CHAIN) { flash(by, H_NO_CHAIN); return; }
		if (cu && tr == ROLE_ROPE) { flash(by, H_NO_ROPE_CUT); return; }
		if (by.distanceToSqr(target) > 4.2 * 4.2) return;
		Cut c = cuts.get(target.getUUID());
		if (c == null || !c.by.equals(by.getUUID())) {
			c = new Cut();
			c.by = by.getUUID();
			c.chain = tr == ROLE_CHAIN;
			cuts.put(target.getUUID(), c);
		}
		c.last = by.level().getGameTime();
	}

	/** ПКМ ножницами/болторезом в воздух — на себя. Один в комнате и в верёвках — можно (для теста). */
	private static void pressSelf(ServerPlayer p) {
		if (phase != P_GAME) return;
		Integer r = role.get(p.getUUID());
		if (r == null || (r != ROLE_ROPE && r != ROLE_CHAIN)) return;
		boolean alone = here(p.level().getServer()).size() == 1;
		if (alone && r == ROLE_ROPE && p.getMainHandItem().is(ModItems.SCISSORS)) {
			Cut c = cuts.computeIfAbsent(p.getUUID(), k -> { Cut n = new Cut(); n.by = p.getUUID(); return n; });
			c.last = p.level().getGameTime();
			return;
		}
		flash(p, H_NO_SELF);
	}

	private static void takeScissors(ServerPlayer p, int i) {
		if (phase != P_GAME) return;
		Integer my = seatOf.get(p.getUUID());
		if (my == null || my != i) { return; }
		if (p.getInventory().contains(st -> st.is(ModItems.SCISSORS))) return;
		ItemStack s = new ItemStack(ModItems.SCISSORS);
		if (p.getMainHandItem().isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, s);
		else p.getInventory().add(s);
		remove(scissorsShow[i]);
		remove(scissorsHit[i]);
		scissorsShow[i] = null;
		scissorsHit[i] = null;
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.7f, 1.6f);
	}

	private static void takeCutters(ServerPlayer p) {
		if (phase != P_GAME || !closetOpen || cuttersShow == null) return;
		ItemStack s = new ItemStack(ModItems.CUTTERS);
		if (p.getMainHandItem().isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, s);
		else p.getInventory().add(s);
		remove(cuttersShow);
		remove(cuttersHit);
		cuttersShow = null;
		cuttersHit = null;
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1f, 0.8f);
	}

	private static void openKeypad(ServerPlayer p) {
		if (phase != P_GAME) return;
		if (closetOpen) return;
		if (ServerPlayNetworking.canSend(p, HubPayloads.Room1Keypad.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.Room1Keypad(0));
	}

	private static void enterCode(ServerPlayer p, String s) {
		ServerLevel l = Places.rooms(p.level().getServer());
		if (l == null || p.level() != l || phase != P_GAME || closetOpen) return;
		if (p.distanceToSqr(KEYPAD) > 5 * 5) return;
		StringBuilder want = new StringBuilder();
		for (int c : code) want.append(c);
		boolean ok = want.toString().equals(s);
		if (ServerPlayNetworking.canSend(p, HubPayloads.Room1Keypad.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.Room1Keypad(ok ? 1 : 2));
		if (ok) {
			closetOpen = true;
			l.playSound(null, KEYPAD.x, KEYPAD.y, KEYPAD.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 1.4f);
			l.getServer().execute(() -> setDoor(l, CLOSET_DOOR, true, true));
			VadimNaTrone.LOG.info("[room1] кладовка открыта ({})", p.getName().getString());
		} else {
			timeLeft = Math.max(1, timeLeft - PENALTY);
			showTimer();
			placeRockets(gameTip(), 6);
			l.playSound(null, KEYPAD.x, KEYPAD.y, KEYPAD.z, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.2f, 0.5f);
			l.playSound(null, MONITOR.x, MONITOR.y, MONITOR.z, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.BLOCKS, 1f, 0.5f);
			for (ServerPlayer q : here(l.getServer())) flash(q, H_WRONG);
			VadimNaTrone.LOG.info("[room1] неверный код {} от {}", s, p.getName().getString());
		}
	}

	private static void voteSkip(ServerPlayer p) {
		if (phase != P_INTRO || phaseTick >= INTRO_ROCKETS) return;
		skipVotes.add(p.getUUID());
		List<ServerPlayer> ps = here(p.level().getServer());
		if (ps.stream().allMatch(q -> skipVotes.contains(q.getUUID()))) {
			// прыжок к ракетам: их всё равно показываем
			phaseTick = INTRO_ROCKETS - 1;
			hush((ServerLevel) p.level());
			for (ServerPlayer q : ps) sendState(q, true);
		}
	}

	// ======================================================================= подсказки

	private static void hints(List<ServerPlayer> ps, long now) {
		for (ServerPlayer p : ps) {
			UUID id = p.getUUID();
			int hint = 0;
			float prog = 0f;
			Integer fl = flashLeft.get(id);
			if (fl != null && fl > 0) {
				flashLeft.put(id, fl - 1);
				hint = flash.get(id);
			} else if (phase == P_WON) {
				hint = wonShow > 0 ? H_WON : 0;
			} else if (phase == P_GAME) {
				Integer r = role.get(id);
				Cut onMe = cuts.get(id);
				Cut mine = null;
				for (Cut c : cuts.values()) if (c.by.equals(id)) mine = c;
				boolean ropeTied = role.containsValue(ROLE_ROPE);
				if (onMe != null && !onMe.by.equals(id)) { hint = H_BEING_CUT; prog = onMe.progress / (float) (onMe.chain ? CHAIN_TICKS : ROPE_TICKS); }
				else if (mine != null) { hint = mine.chain ? H_BITE : H_CUT; prog = mine.progress / (float) (mine.chain ? CHAIN_TICKS : ROPE_TICKS); }
				// без подсказок «что делать»: только ножницы в руке у соседа верёвочного и замок вблизи (игроки ищут сами)
				else if (r != null && r == ROLE_CHAIN && ropeTied && p.getMainHandItem().is(ModItems.SCISSORS)) hint = H_USE_SC;
				else if (r != null && r == ROLE_FREE) {
					if (p.getMainHandItem().is(ModItems.CUTTERS)) hint = H_USE_CUT;
					else if (!closetOpen && p.distanceToSqr(KEYPAD) < 2.5 * 2.5) hint = H_PAD;
				}
			}
			long key = hint * 1000L + Math.round(prog * 48);
			Long last = lastHint.get(id);
			if (last != null && last == key && now % 40 != 0) continue;
			lastHint.put(id, key);
			if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodHint.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodHint(hint, prog));
		}
	}

	// ======================================================================= предметы на полу, двери

	/** Выброшенные ножницы/болторез не теряются: ножницы — пропадают (у стула не нужны), болторез — обратно на крюк. */
	public static boolean onItemLoad(Entity e) {
		if (!(e instanceof ItemEntity it)) return false;
		boolean cu = it.getItem().is(ModItems.CUTTERS), sc = it.getItem().is(ModItems.SCISSORS);
		if (!cu && !sc) return false;
		ServerLevel l = (ServerLevel) e.level();
		it.discard();
		if (cu) l.getServer().execute(() -> {
			for (ServerPlayer p : l.getServer().getPlayerList().getPlayers())
				if (p.getInventory().contains(st -> st.is(ModItems.CUTTERS))) return;
			if (cuttersShow == null) putCutters(l);
		});
		return true;
	}

	private static void setDoor(ServerLevel l, BlockPos lo, boolean open, boolean sound) {
		BlockState a = l.getBlockState(lo), b = l.getBlockState(lo.above());
		if (a.hasProperty(DoorBlock.OPEN)) l.setBlock(lo, a.setValue(DoorBlock.OPEN, open), Block.UPDATE_CLIENTS);
		if (b.hasProperty(DoorBlock.OPEN)) l.setBlock(lo.above(), b.setValue(DoorBlock.OPEN, open), Block.UPDATE_CLIENTS);
		if (sound) l.playSound(null, lo, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.7f);
	}

	// ======================================================================= отладка

	/** /vadim room1 [skip] — всех в комнату, раунд заново (с вступлением или без). */
	public static void restart(MinecraftServer server, boolean intro) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		setWon(l, false);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) continue;
			if (p.level() != l) {
				if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
				p.teleportTo(l, Places.ROOMS_SPAWN.x, Places.ROOMS_SPAWN.y, Places.ROOMS_SPAWN.z, java.util.Set.of(), 0f, 0f, true);
			}
		}
		for (ServerPlayer p : here(server)) {
			clearTools(p);
			if (!seatOf.containsKey(p.getUUID())) { int f = freeSeat(); if (f >= 0) pendingSeat.put(p.getUUID(), 3); }
			else sit(p, seatOf.get(p.getUUID()));
		}
		role.clear();
		refreshBindings(l);
		withIntro = intro;
		setPhase(P_WAIT);
		phaseTick = WAIT_MIN - 40;
	}

	public static void giveCutters(ServerPlayer p) {
		p.getInventory().add(new ItemStack(ModItems.CUTTERS));
	}

	public static String codeString() {
		return phase == P_NONE || phase == P_WAIT ? "раунд ещё не начался" : "" + code[0] + code[1] + code[2] + code[3];
	}

	public static void forceWin(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		for (ServerPlayer p : here(server)) if (tied(p.getUUID())) release(l, p, true);
		win(l, here(server));
	}

	public static String debug() {
		return "фаза " + phase + ", тик " + phaseTick + ", осталось " + timeLeft / 20 + " с, роли " + role + ", стулья " + seatOf
				+ ", кладовка " + (closetOpen ? "открыта" : "закрыта") + ", ракета " + String.format("%.2f", rocketTip);
	}

	// ======================================================================= сохранение

	public static final class State extends SavedData {
		static final Codec<State> CODEC = Codec.BOOL.xmap(State::new, s -> s.won);
		static final SavedDataType<State> TYPE = new SavedDataType<>("vadim_room1", State::new, CODEC, null);
		boolean won;
		State() { this(false); }
		State(boolean b) { won = b; }
	}
}
