package ru.vadim.world;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Комната 2 «Студия» (этап 5а). Из коридора комнаты 1 — в старую студию звукозаписи; когда все внутри, за спиной
 * падает железная плита. На полу пластинка Вадима: её ставят в проигрыватель, Вадим «по рации» читает правила.
 * Потом большая кнопка «НАЧАТЬ ИГРУ» → открываются три стеклянные кабинки, каждый садится в любую, двери закрываются.
 * В кабинках случайно появляется своё: у «Уха» — магнитофон с рычагом (запись из 8 звуков слышит только он),
 * у «Рук» — 8 цветных клавиш, у «Глаз» — плакат «звук → цвет» (видит только он). Ухо называет звук, Глаза — цвет,
 * Руки жмут. Ошибка — током всех, «последовательность заново», минус жизнь (3 на всю игру; на последней — сердце).
 * Время вышло или жизни кончились — смерть (чернота), снова кнопка. 3 раунда: 1:30 / 1:00 / 0:50. Победа — газ,
 * все падают и просыпаются в заглушке комнаты 3. Вдвоём: Ухо + (Глаза и Руки у одного). Один (тест) — всё у одного.
 */
public final class Room2 {
	public static final String TAG = "vadim_r2";
	public static final String SECRET = "vadim_r2_secret";
	public static final int P_NONE = 0, P_IN = 1, P_RULES = 2, P_READY = 3, P_SEAT = 4, P_TITLE = 5, P_ROUND = 6, P_BREAK = 7,
			P_LOST = 8, P_WON = 9, P_DONE = 10;
	public static final int LOST_LEN = 170;
	private static final int RULES_LEN = 935, TITLE_LEN = 60, WIN_GAS = 120, WIN_MOVE = WIN_GAS + 260;
	private static final int[] ROUND_TIME = {90 * 20, 60 * 20, 50 * 20};
	public static final int ROLE_EAR = 1, ROLE_HANDS = 2, ROLE_EYES = 4;
	public static final int H_RESTART = 40;

	/** Звуки (порядок = индексы в SEQS). */
	static final String[] SOUND_NAMES = {"ПЕРДЁЖ", "ТРАНСФОРМЕР", "МАЛЕНЬКИЕ ДЕВОЧКИ", "СМЕХ", "АНАЛИЗ", "ДОЛБАЁБЫ", "КОНЧЕННЫЕ УРОДЫ", "МЯУ"};
	/** 13 записей (файлы room2_rec_01..13), по 8 звуков. */
	static final int[][] SEQS = {
			{1, 2, 5, 5, 2, 4, 7, 6}, {1, 2, 6, 4, 7, 3, 5, 0}, {4, 0, 6, 5, 2, 3, 7, 7}, {1, 7, 3, 2, 3, 1, 7, 5},
			{3, 1, 2, 0, 0, 2, 4, 7}, {6, 7, 6, 0, 3, 1, 1, 0}, {5, 2, 4, 2, 4, 3, 5, 0}, {0, 4, 6, 6, 1, 7, 1, 2},
			{7, 4, 2, 3, 5, 4, 0, 5}, {5, 0, 6, 7, 5, 0, 1, 2}, {4, 5, 0, 6, 7, 3, 1, 1}, {3, 6, 7, 2, 1, 0, 6, 1},
			{5, 4, 5, 7, 3, 4, 1, 0}};
	/** Длина записей в тиках (+ хвост). */
	static final int[] REC_TICKS = {356, 319, 279, 327, 320, 325, 312, 369, 284, 313, 330, 354, 295};
	/** 8 цветов клавиш (макет «Руки»): красный, оранжевый, жёлтый, зелёный, голубой, синий, фиолетовый, белый. */
	static final int[] COLORS = {0xD7263D, 0xF46F1F, 0xF2C230, 0x3FAE49, 0x3CC6E0, 0x2F5BD3, 0x8E44C9, 0xECE6D8};
	static final String[] COLOR_NAMES = {"красный", "оранжевый", "жёлтый", "зелёный", "голубой", "синий", "фиолетовый", "белый"};
	private static final Block[] KEY_BLOCKS = {Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE,
			Blocks.LIGHT_BLUE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.WHITE_CONCRETE};
	private static final float[] KEY_PITCH = {0.6f, 0.67f, 0.75f, 0.8f, 0.9f, 1.0f, 1.12f, 1.2f};

	// ---- геометрия: студия x −13..7, z −30..−13, пол y=63 (стоят на 64), потолок 71
	public static final int X0 = -13, X1 = 7, Z0 = -30, Z1 = -13;
	/** Кабинки: внутри x bx..bx+2, z −30..−28; передняя стеклянная стенка z=−27, дверь — по центру. */
	public static final int[] BX = {-10, -4, 2};
	public static final int BOOTH_Z0 = -30, BOOTH_FRONT = -27;
	/** Плита в коридоре (блок z=−11, x −4..−2): наверху y 67, внизу 64. */
	static final int PLATE_Z = -11;
	static final double PLATE_UP = 67.0, PLATE_DOWN = 64.0;
	public static final Vec3 STUDIO_SPAWN = new Vec3(-2.5, 64, -14.5);
	public static final Vec3 ROOM3_SPAWN = new Vec3(44.5, 64, 0.5);
	static final Vec3 BOARD = new Vec3(-2.5, 68.6, -21.0);
	static final Vec3 BUTTON = new Vec3(-2.5, 64, -18.5);
	static final BlockPos JUKEBOX = new BlockPos(5, 64, -16);
	static final Vec3 RECORD_AT = new Vec3(-9.5, 64.03, -16.0);

	// ---- состояние
	private static int phase = P_NONE, phaseTick = 0;
	private static int round = 1, lives = 3, pos = 0, timeLeft = ROUND_TIME[0], shocks = 0, rec = -1;
	private static int[] seq = SEQS[0];
	/** colorOf[звук] = цвет; keyColor[клавиша] = цвет. */
	private static final int[] colorOf = new int[8], keyColor = new int[8];
	private static final int[] boothRole = new int[3];
	private static final UUID[] boothPlayer = new UUID[3];
	private static final Set<Integer> usedRec = new HashSet<>();
	private static int stable = 0, playUntil = -1, restartHint = 0, plateT = -1, boardBlink = 0;
	private static boolean someoneLeft = false;
	private static final Map<UUID, Long> lastPress = new HashMap<>();

	// ---- сущности
	private static final List<Entity> live = new ArrayList<>();
	private static final Map<Entity, UUID> secretViewer = new HashMap<>();
	private static final Display.ItemDisplay[] chairs = new Display.ItemDisplay[3];
	private static final Interaction[] chairHit = new Interaction[3];
	private static final List<Entity> fixtures = new ArrayList<>();
	private static final Display.BlockDisplay[] keyDisp = new Display.BlockDisplay[8];
	private static final Interaction[] keyHit = new Interaction[8];
	private static final int[] keyAnim = new int[8];
	private static Display.BlockDisplay leverDisp, lampDisp, plate, buttonCap;
	private static Interaction leverHit, buttonHit, jukeboxHit, recordHit;
	private static Display.ItemDisplay recordShow, recordOnPlayer;
	private static final Display.ItemDisplay[] digits = new Display.ItemDisplay[4];
	private static Display.TextDisplay roundText, message;
	private static final Display.BlockDisplay[] lifeLamp = new Display.BlockDisplay[3];

	private Room2() {}

	// ======================================================================= регистрация

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Room2::tick);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.dimension() != Places.ROOMS || !entity.getTags().contains(TAG)) return InteractionResult.PASS;
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
			use(sp, entity);
			return InteractionResult.SUCCESS;
		});
	}

	public static boolean canSee(Entity e, ServerPlayer p) {
		UUID v = secretViewer.get(e);
		return v != null && v.equals(p.getUUID());
	}

	public static boolean isStale(Entity e) {
		return e.getTags().contains(TAG) && !live.contains(e);
	}

	public static boolean inStudio(double x, double z) {
		return x > X0 - 0.2 && x < X1 + 1.2 && z > Z0 - 1.0 && z < Z1 + 0.8;
	}

	// ======================================================================= постройка (из Rooms.ensure)

	private static BlockState b(Block x) { return x.defaultBlockState(); }

	private static void set(ServerLevel l, int x, int y, int z, BlockState st) {
		l.setBlock(new BlockPos(x, y, z), st, Block.UPDATE_CLIENTS);
	}

	/** Студия: коридор от двери комнаты 1, щель для плиты, зал, три кабинки, проигрыватель, декор; заглушка комнаты 3. */
	static void build(ServerLevel l) {
		Random r = new Random(2025);
		// оболочка
		for (int x = X0 - 2; x <= X1 + 2; x++) for (int z = Z0 - 2; z <= -6; z++) for (int y = 62; y <= 72; y++) {
			if (z >= -6 && x >= -7) continue;   // стена комнаты 1 — не трогаем
			set(l, x, y, z, b(Blocks.GRAY_CONCRETE));
		}
		// коридор x −4..−2, z −11..−7, высота 3, щель над z=−11 для плиты
		for (int z = -11; z <= -7; z++) for (int x = -4; x <= -2; x++) {
			set(l, x, 63, z, b(Blocks.SMOOTH_STONE));
			for (int y = 64; y <= 66; y++) set(l, x, y, z, b(Blocks.AIR));
			set(l, x, 67, z, b(Blocks.SMOOTH_STONE));
		}
		for (int x = -4; x <= -2; x++) for (int y = 67; y <= 70; y++) set(l, x, y, PLATE_Z, b(Blocks.AIR));
		for (int x = -5; x <= -1; x += 4) for (int y = 64; y <= 70; y++) set(l, x, y, PLATE_Z, b(Blocks.IRON_BLOCK));
		set(l, -3, 66, -9, b(Blocks.LIGHT).setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 6));
		// зал
		for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
			set(l, x, 63, z, b(Blocks.DARK_OAK_PLANKS));
			for (int y = 64; y <= 70; y++) set(l, x, y, z, b(Blocks.AIR));
			set(l, x, 71, z, b(Blocks.BLACK_WOOL));
			int k = r.nextInt(100);
			set(l, x, 64, z, b(k < 70 ? Blocks.RED_CARPET : k < 85 ? Blocks.BROWN_CARPET : Blocks.AIR));
		}
		// стены: орех понизу, «поролон» (серая шерсть) выше
		for (int y = 64; y <= 70; y++) {
			for (int x = X0 - 1; x <= X1 + 1; x++) for (int z : new int[]{Z0 - 1, Z1 + 1}) set(l, x, y, z, studioWall(r, y));
			for (int z = Z0 - 1; z <= Z1 + 1; z++) for (int x : new int[]{X0 - 1, X1 + 1}) set(l, x, y, z, studioWall(r, y));
		}
		// вход из коридора
		for (int x = -4; x <= -2; x++) for (int y = 64; y <= 66; y++) set(l, x, y, Z1 + 1, b(Blocks.AIR));
		// кабинки
		for (int i = 0; i < 3; i++) booth(l, BX[i]);
		// проигрыватель на тумбе у восточной стены
		set(l, JUKEBOX.getX(), 63, JUKEBOX.getZ(), b(Blocks.DARK_OAK_PLANKS));
		set(l, JUKEBOX.getX(), 64, JUKEBOX.getZ(), b(Blocks.JUKEBOX));
		// декор: колонки-комбики, стойки микрофонов, пульт, лампы
		for (int[] a : new int[][]{{6, -29}, {6, -24}, {-12, -24}, {-12, -20}}) {
			set(l, a[0], 64, a[1], b(Blocks.NOTE_BLOCK));
			set(l, a[0], 65, a[1], b(Blocks.NOTE_BLOCK));
		}
		for (int[] a : new int[][]{{-8, -22}, {3, -22}, {-11, -15}}) {
			set(l, a[0], 64, a[1], b(Blocks.IRON_BARS));
			set(l, a[0], 65, a[1], b(Blocks.LIGHTNING_ROD));
		}
		for (int x = 1; x <= 4; x++) set(l, x, 64, -13, b(Blocks.SMOOTH_STONE_SLAB));
		for (int[] a : new int[][]{{-8, -17}, {3, -17}, {-8, -25}, {3, -25}, {-2, -14}}) {
			set(l, a[0], 70, a[1], b(Blocks.IRON_CHAIN));
			set(l, a[0], 69, a[1], b(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		}
		set(l, -12, 64, -29, b(Blocks.COBWEB));
		set(l, 7, 70, -13, b(Blocks.COBWEB));
		// заглушка комнаты 3 (x 40..48, z −4..4)
		for (int x = 38; x <= 50; x++) for (int z = -6; z <= 6; z++) for (int y = 62; y <= 70; y++)
			set(l, x, y, z, (x == 38 || x == 50 || z == -6 || z == 6 || y == 62 || y == 70) ? b(Blocks.GRAY_CONCRETE) : b(Blocks.AIR));
		for (int x = 39; x <= 49; x++) for (int z = -5; z <= 5; z++) {
			set(l, x, 63, z, b(Blocks.SMOOTH_STONE));
			if (x == 39 || x == 49 || z == -5 || z == 5) for (int y = 64; y <= 69; y++) set(l, x, y, z, b(Blocks.GRAY_CONCRETE));
		}
		set(l, 44, 68, 0, b(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		set(l, 44, 69, 0, b(Blocks.IRON_CHAIN));
	}

	private static BlockState studioWall(Random r, int y) {
		if (y <= 66) return r.nextInt(12) == 0 ? b(Blocks.STRIPPED_DARK_OAK_LOG) : b(Blocks.DARK_OAK_PLANKS);
		int k = r.nextInt(10);
		return k < 6 ? b(Blocks.GRAY_WOOL) : k < 9 ? b(Blocks.LIGHT_GRAY_WOOL) : b(Blocks.BLACK_WOOL);
	}

	/** Кабинка: стекло спереди и по бокам, рама из тёмного дерева, крыша, табличка-лампа «ИДЁТ ЗАПИСЬ». */
	private static void booth(ServerLevel l, int bx) {
		int x0 = bx - 1, x1 = bx + 3;
		for (int x = x0; x <= x1; x++) for (int z = BOOTH_Z0; z <= BOOTH_FRONT; z++) {
			boolean edgeX = x == x0 || x == x1, front = z == BOOTH_FRONT;
			for (int y = 64; y <= 66; y++) {
				if (edgeX && front) set(l, x, y, z, b(Blocks.STRIPPED_DARK_OAK_LOG));
				else if (edgeX || front) set(l, x, y, z, b(Blocks.GLASS_PANE));
				else set(l, x, y, z, b(Blocks.AIR));
			}
			set(l, x, 67, z, b(Blocks.DARK_OAK_PLANKS));
		}
		for (int x = bx; x <= bx + 2; x++) for (int z = BOOTH_Z0; z < BOOTH_FRONT; z++) set(l, x, 64, z, b(Blocks.GRAY_CARPET));
		set(l, bx + 1, 67, BOOTH_FRONT, b(Blocks.REDSTONE_LAMP));
		set(l, bx + 1, 66, BOOTH_Z0 + 1, b(Blocks.LIGHT).setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 9));
		// стёкла — соединить
		for (int x = x0; x <= x1; x++) for (int z = BOOTH_Z0; z <= BOOTH_FRONT; z++) for (int y = 64; y <= 66; y++) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState s = l.getBlockState(p);
			if (s.is(Blocks.GLASS_PANE)) l.setBlock(p, Block.updateFromNeighbourShapes(s, l, p), Block.UPDATE_CLIENTS);
		}
	}

	// ======================================================================= старт сервера

	public static void setup(ServerLevel l) {
		List<Entity> old = new ArrayList<>();
		for (Entity e : l.getAllEntities()) if (e.getTags().contains(TAG)) old.add(e);
		old.forEach(Entity::discard);
		live.clear();
		secretViewer.clear();
		clearFixtures();
		playUntil = -1;
		restartHint = 0;
		pos = 0;
		State st = state(l.getServer());
		// стулья
		for (int i = 0; i < 3; i++) {
			Vec3 c = chairPos(i);
			Display.ItemDisplay ch = item(l, "room1_chair", c, new Quaternionf(), 1f);
			ch.setYRot(0f);
			ch.setViewRange(2f);
			chairs[i] = ch;
			chairHit[i] = hitbox(l, new Vec3(c.x, 64.0, c.z - 0.05), 0.75f, 0.8f);   // низкий — сидящему не мешает жать клавиши
		}
		// табло
		Display.BlockDisplay back = block(l, Blocks.BLACK_CONCRETE.defaultBlockState(), new Vec3(BOARD.x - 3.8, BOARD.y - 1.0, BOARD.z + 0.05), new Vector3f(7.6f, 2.1f, 0.1f));
		back.setViewRange(3f);
		Display.BlockDisplay rim = block(l, Blocks.DARK_OAK_PLANKS.defaultBlockState(), new Vec3(BOARD.x - 3.9, BOARD.y - 1.1, BOARD.z + 0.12), new Vector3f(7.8f, 2.3f, 0.1f));
		rim.setViewRange(3f);
		for (int x : new int[]{-5, 0}) {   // цепи до потолка
			Display.BlockDisplay c = block(l, Blocks.IRON_CHAIN.defaultBlockState(), new Vec3(x + 0.0, BOARD.y + 1.05, BOARD.z - 0.4), new Vector3f(1f, 1.4f, 1f));
			c.setViewRange(3f);
		}
		for (int k = 0; k < 4; k++) {
			Display.ItemDisplay d = item(l, "room1_seg_0", new Vec3(BOARD.x - 1.1 + k * 0.62 - (k >= 2 ? 0.05 : 0), BOARD.y + 0.05, BOARD.z - 0.02), new Quaternionf(), 0.8f);
			d.setBrightnessOverride(new Brightness(15, 15));
			d.setViewRange(3f);
			digits[k] = d;
		}
		roundText = text(l, new Vec3(BOARD.x - 2.85, BOARD.y - 0.45, BOARD.z - 0.02), 0.7f);
		message = text(l, new Vec3(BOARD.x, BOARD.y - 0.35, BOARD.z - 0.03), 1.6f);
		for (int k = 0; k < 3; k++) {
			Display.BlockDisplay d = block(l, Blocks.OCHRE_FROGLIGHT.defaultBlockState(), new Vec3(BOARD.x + 2.2 + k * 0.45, BOARD.y - 0.15, BOARD.z - 0.1), new Vector3f(0.32f));
			d.setBrightnessOverride(new Brightness(15, 15));
			d.setViewRange(3f);
			lifeLamp[k] = d;
		}
		Display.TextDisplay livesLabel = text(l, new Vec3(BOARD.x + 2.65, BOARD.y + 0.3, BOARD.z - 0.02), 0.4f);
		livesLabel.setText(Component.literal("ЖИЗНИ").withColor(0x7A2A24));
		Display.TextDisplay timeLabel = text(l, new Vec3(BOARD.x - 0.1, BOARD.y + 0.55, BOARD.z - 0.02), 0.4f);
		timeLabel.setText(Component.literal("ВРЕМЯ").withColor(0x7A2A24));
		// кнопка «НАЧАТЬ ИГРУ»
		block(l, Blocks.GRAY_CONCRETE.defaultBlockState(), new Vec3(BUTTON.x - 0.3, 64.0, BUTTON.z - 0.3), new Vector3f(0.6f, 0.9f, 0.6f));
		block(l, Blocks.YELLOW_CONCRETE.defaultBlockState(), new Vec3(BUTTON.x - 0.36, 64.9, BUTTON.z - 0.36), new Vector3f(0.72f, 0.08f, 0.72f));
		buttonCap = block(l, Blocks.RED_CONCRETE.defaultBlockState(), new Vec3(BUTTON.x - 0.2, 64.98, BUTTON.z - 0.2), new Vector3f(0.4f, 0.14f, 0.4f));
		for (int s = 0; s < 2; s++) {
			Display.TextDisplay t = text(l, new Vec3(BUTTON.x, 64.35, BUTTON.z + (s == 0 ? -0.31 : 0.31)), 0.3f);
			t.setText(Component.literal("НАЧАТЬ\nИГРУ").withColor(0x2A1D14));
			t.setBackgroundColor(0xFFE8DCC0);
			t.setBrightnessOverride(new Brightness(10, 10));
			if (s == 1) t.setTransformation(new Transformation(null, null, new Vector3f(0.3f), null));
		}
		buttonHit = hitbox(l, BUTTON, 0.8f, 1.2f);
		jukeboxHit = hitbox(l, new Vec3(JUKEBOX.getX() + 0.5, JUKEBOX.getY() - 0.01, JUKEBOX.getZ() + 0.5), 1.06f, 1.08f);
		// плита в коридоре
		plate = block(l, Blocks.IRON_BLOCK.defaultBlockState(), new Vec3(-4.0, st.entered ? PLATE_DOWN : PLATE_UP, PLATE_Z + 0.3), new Vector3f(3.0f, 3.0f, 0.4f));
		plate.setViewRange(2f);
		doorway(l, st.entered && !st.won ? true : st.entered);
		plateT = -1;
		// состояние
		for (int i = 0; i < 3; i++) { boothPlayer[i] = null; boothRole[i] = 0; setDoor(l, i, false); setLamp(l, i, false); }
		lives = 3;
		round = 1;
		usedRec.clear();
		if (st.won) setPhase(P_DONE);
		else if (st.entered) setPhase(P_IN);
		else setPhase(P_NONE);
		if (!st.won) putRecord(l);
		showBoard();
		VadimNaTrone.LOG.info("[room2] готова (вошли: {}, пройдена: {})", st.entered, st.won);
	}

	static Vec3 chairPos(int i) { return new Vec3(BX[i] + 1.5, 64.5, BOOTH_Z0 + 0.5); }

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
		if (d == null) return;
		if (model == null) { d.setItemStack(ItemStack.EMPTY); return; }
		ItemStack st = new ItemStack(Items.PAPER);
		st.set(DataComponents.ITEM_MODEL, VadimNaTrone.id(model));
		d.setItemStack(st);
	}

	/** Блок-дисплей: corner — угол куба, size — размер. */
	private static Display.BlockDisplay block(ServerLevel l, BlockState s, Vec3 corner, Vector3f size) {
		Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		d.setBlockState(s);
		d.setPos(corner.x, corner.y, corner.z);
		d.setTransformation(new Transformation(null, null, size, null));
		d.setViewRange(1f);
		add(l, d);
		return d;
	}

	/** Текст, смотрящий на −Z (к кабинкам). */
	private static Display.TextDisplay text(ServerLevel l, Vec3 at, float scale) {
		Display.TextDisplay t = EntityType.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		t.setPos(at.x, at.y, at.z);
		t.setTransformation(new Transformation(null, new Quaternionf().rotateY((float) Math.PI), new Vector3f(scale), null));
		t.setBackgroundColor(0);
		t.setBrightnessOverride(Brightness.FULL_BRIGHT);
		t.setText(Component.empty());
		t.setViewRange(3f);
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

	private static void remove(Entity e) {
		if (e == null) return;
		e.discard();
		live.remove(e);
		secretViewer.remove(e);
	}

	// ======================================================================= пластинка, плита, двери

	private static void putRecord(ServerLevel l) {
		remove(recordShow);
		remove(recordHit);
		remove(recordOnPlayer);
		recordOnPlayer = null;
		Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		d.setItemStack(new ItemStack(ModItems.RECORD));
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setPos(RECORD_AT.x, RECORD_AT.y, RECORD_AT.z);
		d.setTransformation(new Transformation(null, new Quaternionf().rotateY(0.7f).rotateX((float) (Math.PI / 2)), new Vector3f(0.7f), null));
		add(l, d);
		recordShow = d;
		recordHit = hitbox(l, RECORD_AT.add(0, -0.03, 0), 0.7f, 0.25f);
	}

	private static void doorway(ServerLevel l, boolean closed) {
		for (int x = -4; x <= -2; x++) for (int y = 64; y <= 66; y++)
			l.setBlock(new BlockPos(x, y, PLATE_Z), closed ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static void setDoor(ServerLevel l, int i, boolean open) {
		int x = BX[i] + 1;
		for (int y = 64; y <= 65; y++) {
			BlockPos p = new BlockPos(x, y, BOOTH_FRONT);
			BlockState s = open ? Blocks.AIR.defaultBlockState() : Block.updateFromNeighbourShapes(Blocks.GLASS_PANE.defaultBlockState(), l, p);
			l.setBlock(p, s, Block.UPDATE_ALL);
		}
	}

	private static void doors(ServerLevel l, boolean open) {
		for (int i = 0; i < 3; i++) setDoor(l, i, open);
		Vec3 c = new Vec3(-2.5, 65, BOOTH_FRONT);
		l.playSound(null, c.x, c.y, c.z, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.6f);
		for (int i = 0; i < 3; i++)
			l.playSound(null, BX[i] + 1.5, 65, BOOTH_FRONT + 0.5, open ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f, 0.7f);
	}

	private static void setLamp(ServerLevel l, int i, boolean on) {
		BlockPos p = new BlockPos(BX[i] + 1, 67, BOOTH_FRONT);
		BlockState s = l.getBlockState(p);
		if (s.hasProperty(RedstoneLampBlock.LIT)) l.setBlock(p, s.setValue(RedstoneLampBlock.LIT, on), Block.UPDATE_CLIENTS);
	}

	// ======================================================================= игроки

	private static List<ServerPlayer> here(MinecraftServer s) {
		List<ServerPlayer> out = new ArrayList<>();
		ServerLevel l = Places.rooms(s);
		if (l == null) return out;
		for (ServerPlayer p : l.players()) if (!p.isSpectator() && inStudio(p.getX(), p.getZ())) out.add(p);
		return out;
	}

	private static int boothOf(UUID id) {
		for (int i = 0; i < 3; i++) if (id.equals(boothPlayer[i])) return i;
		return -1;
	}

	private static int seatedCount() {
		int n = 0;
		for (UUID u : boothPlayer) if (u != null) n++;
		return n;
	}

	private static void sit(ServerPlayer p, int i) {
		ServerLevel l = Places.rooms(p.level().getServer());
		if (l == null) return;
		Vec3 c = chairPos(i);
		boothPlayer[i] = p.getUUID();
		if (p.isPassenger() && p.getVehicle() != chairs[i]) p.stopRiding();
		if (p.getVehicle() == chairs[i]) return;
		p.teleportTo(l, c.x, c.y - 0.6, c.z, Set.of(), 0f, 15f, true);
		p.startRiding(chairs[i], true, true);
	}

	private static boolean mustSit() {
		return phase == P_TITLE || phase == P_ROUND || phase == P_WON || (phase == P_LOST && phaseTick < LOST_LEN - 10);
	}

	/** Все встали и вышли из кабинок (после проигрыша). */
	private static void everyoneOut(ServerLevel l) {
		for (int i = 0; i < 3; i++) {
			UUID u = boothPlayer[i];
			boothPlayer[i] = null;
			if (u == null) continue;
			ServerPlayer p = l.getServer().getPlayerList().getPlayer(u);
			if (p == null) continue;
			if (p.isPassenger()) p.stopRiding();
			p.teleportTo(l, BX[i] + 1.5, 64, BOOTH_FRONT + 2.5, Set.of(), 0f, 0f, true);
		}
	}

	// ======================================================================= такт

	private static void tick(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null || live.isEmpty()) return;
		List<ServerPlayer> ps = here(server);
		if (phase == P_NONE) { plateTick(l); return; }
		if (ps.isEmpty() && phase != P_WON) return;
		phaseTick++;

		// сидящие: выбило со стула во время раунда — обратно; встал в перерыве — место свободно
		for (int i = 0; i < 3; i++) {
			UUID u = boothPlayer[i];
			if (u == null) continue;
			ServerPlayer p = server.getPlayerList().getPlayer(u);
			if (p == null || p.level() != l) { boothPlayer[i] = null; continue; }
			if (p.getVehicle() != chairs[i]) {
				if (mustSit()) sit(p, i);
				else { boothPlayer[i] = null; someoneLeft = true; }
			}
		}
		for (int k = 0; k < 8; k++) if (keyAnim[k] > 0 && --keyAnim[k] == 0) keyUp(k);
		if (playUntil >= 0 && --playUntil <= 0) { playUntil = -1; leverState(false); }
		if (restartHint > 0) restartHint--;

		switch (phase) {
			case P_IN -> {}
			case P_RULES -> { if (phaseTick >= RULES_LEN) { setPhase(P_READY); showBoard(); } }
			case P_READY -> {}
			case P_SEAT, P_BREAK -> {
				int need = Math.min(ps.size(), 3);
				if (need > 0 && seatedCount() >= need) stable++;
				else stable = 0;
				int wait = phase == P_SEAT || someoneLeft ? 20 : 100;
				if (stable >= wait) startTitle(l);
			}
			case P_TITLE -> {
				boardBlink++;
				message.setText(boardBlink / 8 % 2 == 0 ? Component.literal("РАУНД " + round).withColor(0xFF3B2F) : Component.empty());
				if (phaseTick >= TITLE_LEN) startRound(l);
			}
			case P_ROUND -> roundTick(l);
			case P_LOST -> {
				if (phaseTick == 40) say(l, ModSounds.ROOM2_LOSE);
				if (phaseTick >= LOST_LEN) resetToReady(l);
			}
			case P_WON -> wonTick(l, server);
			default -> {}
		}

		long now = l.getGameTime();
		for (ServerPlayer p : ps) sendState(p, now % 20 == 0);
		hints(ps, now);
	}

	/** Все из комнат зашли в студию → плита падает. */
	private static void plateTick(ServerLevel l) {
		MinecraftServer server = l.getServer();
		if (plateT < 0) {
			if (!Room1.won(server)) return;
			List<ServerPlayer> all = new ArrayList<>();
			for (ServerPlayer p : l.players()) if (!p.isSpectator()) all.add(p);
			if (all.isEmpty()) return;
			for (ServerPlayer p : all) if (!inStudio(p.getX(), p.getZ()) || p.getZ() > Z1 - 0.5) return;
			plateT = 0;
			l.playSound(null, -2.5, 67.5, PLATE_Z + 0.5, ModSounds.ZAVOD_HATCH_MOVE, SoundSource.BLOCKS, 1f, 1f);
			VadimNaTrone.LOG.info("[room2] все в студии — плита падает ({} игр.)", all.size());
			return;
		}
		plateT++;
		if (plateT == 4 && plate != null) {
			plate.setPosRotInterpolationDuration(16);
			plate.teleportTo(-4.0, PLATE_DOWN, PLATE_Z + 0.3);
		}
		if (plateT == 20) {
			l.playSound(null, -2.5, 65.5, PLATE_Z + 0.5, ModSounds.ZAVOD_HATCH_SLAM, SoundSource.BLOCKS, 1f, 1f);
			for (ServerPlayer p : here(server)) {
				p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.ZAVOD_HATCH_SLAM_BED),
						SoundSource.MASTER, p.getX(), p.getEyeY(), p.getZ(), 0.8f, 1f, l.getRandom().nextLong()));
				if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodFinal.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodFinal(1, 0));
			}
			l.sendParticles(ParticleTypes.POOF, -2.5, 64.2, PLATE_Z + 0.5, 20, 1.0, 0.05, 0.3, 0.03);
			doorway(l, true);
			State st = state(server);
			st.entered = true;
			st.setDirty();
			plateT = -1;
			setPhase(P_IN);
		}
	}

	private static void setPhase(int ph) {
		phase = ph;
		phaseTick = 0;
	}

	// ======================================================================= действия

	private static void use(ServerPlayer p, Entity e) {
		ServerLevel l = (ServerLevel) p.level();
		if (e == recordHit) { takeRecord(p); return; }
		if (e == jukeboxHit) { playRecord(l, p); return; }
		if (e == buttonHit) { pressButton(l, p); return; }
		for (int i = 0; i < 3; i++) if (e == chairHit[i]) { tryToSit(p, i); return; }
		if (e == leverHit) { pullLever(l, p); return; }
		for (int k = 0; k < 8; k++) if (e == keyHit[k]) { pressKey(l, p, k); return; }
	}

	private static void takeRecord(ServerPlayer p) {
		if (phase != P_IN || recordShow == null) return;
		ItemStack s = new ItemStack(ModItems.RECORD);
		if (p.getMainHandItem().isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, s);
		else p.getInventory().add(s);
		remove(recordShow);
		remove(recordHit);
		recordShow = null;
		recordHit = null;
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.8f);
	}

	private static void playRecord(ServerLevel l, ServerPlayer p) {
		if (phase != P_IN || !p.getMainHandItem().is(ModItems.RECORD)) return;
		p.getMainHandItem().shrink(1);
		recordOnPlayer = item0(l, new ItemStack(ModItems.RECORD), new Vec3(JUKEBOX.getX() + 0.5, JUKEBOX.getY() + 1.02, JUKEBOX.getZ() + 0.5),
				new Quaternionf().rotateX((float) (Math.PI / 2)), 0.8f);
		l.playSound(null, JUKEBOX, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8f, 0.6f);
		l.playSound(null, JUKEBOX.getX() + 0.5, JUKEBOX.getY() + 1, JUKEBOX.getZ() + 0.5, ModSounds.ROOM2_RULES, SoundSource.RECORDS, 1f, 1f);
		setPhase(P_RULES);
		VadimNaTrone.LOG.info("[room2] пластинка в проигрывателе ({})", p.getName().getString());
	}

	private static Display.ItemDisplay item0(ServerLevel l, ItemStack st, Vec3 at, Quaternionf rot, float scale) {
		Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		d.setItemStack(st);
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setPos(at.x, at.y, at.z);
		d.setTransformation(new Transformation(null, rot, new Vector3f(scale), null));
		add(l, d);
		return d;
	}

	private static void pressButton(ServerLevel l, ServerPlayer p) {
		if (buttonCap != null) {
			buttonCap.setTransformationInterpolationDelay(0);
			buttonCap.setTransformationInterpolationDuration(2);
			buttonCap.setTransformation(new Transformation(new Vector3f(0, -0.07f, 0), null, new Vector3f(0.4f, 0.14f, 0.4f), null));
		}
		l.playSound(null, BUTTON.x, 65, BUTTON.z, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1f, 0.6f);
		capUp = 6;
		if (phase != P_READY) return;
		startSeat(l);
		VadimNaTrone.LOG.info("[room2] кнопка: кабинки открыты ({})", p.getName().getString());
	}

	private static int capUp = 0;

	private static void startSeat(ServerLevel l) {
		for (int i = 0; i < 3; i++) { boothPlayer[i] = null; boothRole[i] = 0; }
		doors(l, true);
		stable = 0;
		someoneLeft = false;
		setPhase(P_SEAT);
		showBoard();
	}

	private static void tryToSit(ServerPlayer p, int i) {
		if (phase != P_SEAT && phase != P_BREAK) return;
		if (boothPlayer[i] != null && !boothPlayer[i].equals(p.getUUID())) return;
		int mine = boothOf(p.getUUID());
		if (mine >= 0 && mine != i) return;
		// сесть можно только изнутри кабинки (через открытую дверь)
		if (p.getZ() > BOOTH_FRONT + 0.2) return;
		sit(p, i);
		stable = 0;
	}

	private static void startTitle(ServerLevel l) {
		doors(l, false);
		for (int i = 0; i < 3; i++) setLamp(l, i, true);
		boardBlink = 0;
		setPhase(P_TITLE);
		showBoard();
		l.playSound(null, BOARD.x, BOARD.y, BOARD.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.2f, 0.5f);
	}

	/** Новый раунд: роли по кабинкам, запись, цвета, интерфейсы. */
	private static void startRound(ServerLevel l) {
		Random r = new Random();
		List<Integer> seated = new ArrayList<>();
		for (int i = 0; i < 3; i++) if (boothPlayer[i] != null) seated.add(i);
		List<Integer> masks = switch (seated.size()) {
			case 3 -> new ArrayList<>(List.of(ROLE_EAR, ROLE_HANDS, ROLE_EYES));
			case 2 -> new ArrayList<>(List.of(ROLE_EAR, ROLE_HANDS | ROLE_EYES));
			default -> new ArrayList<>(List.of(ROLE_EAR | ROLE_HANDS | ROLE_EYES));
		};
		Collections.shuffle(masks, r);
		for (int i = 0; i < 3; i++) boothRole[i] = 0;
		for (int k = 0; k < seated.size() && k < masks.size(); k++) boothRole[seated.get(k)] = masks.get(k);
		List<Integer> free = new ArrayList<>();
		for (int i = 0; i < SEQS.length; i++) if (!usedRec.contains(i)) free.add(i);
		if (free.isEmpty()) { usedRec.clear(); for (int i = 0; i < SEQS.length; i++) free.add(i); }
		rec = free.get(r.nextInt(free.size()));
		usedRec.add(rec);
		seq = SEQS[rec];
		perm(colorOf, r);
		perm(keyColor, r);
		pos = 0;
		playUntil = -1;
		clearFixtures();
		for (int i = 0; i < 3; i++) {
			int m = boothRole[i];
			if (m == 0) continue;
			boolean combo = Integer.bitCount(m) > 1;
			if ((m & ROLE_HANDS) != 0) spawnKeys(l, i);
			if ((m & ROLE_EYES) != 0) spawnPoster(l, i, combo);
			if ((m & ROLE_EAR) != 0) spawnTape(l, i, combo);
		}
		timeLeft = ROUND_TIME[Math.min(2, round - 1)];
		setPhase(P_ROUND);
		message.setText(Component.empty());
		showBoard();
		StringBuilder sb = new StringBuilder();
		for (int s : seq) sb.append(SOUND_NAMES[s]).append('=').append(COLOR_NAMES[colorOf[s]]).append(' ');
		VadimNaTrone.LOG.info("[room2] раунд {}: запись {}, роли {}/{}/{}, {}", round, rec + 1, boothRole[0], boothRole[1], boothRole[2], sb);
	}

	private static void perm(int[] a, Random r) {
		List<Integer> l = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7));
		Collections.shuffle(l, r);
		for (int i = 0; i < 8; i++) a[i] = l.get(i);
	}

	private static void clearFixtures() {
		for (Entity e : fixtures) remove(e);
		fixtures.clear();
		for (int k = 0; k < 8; k++) { keyDisp[k] = null; keyHit[k] = null; keyAnim[k] = 0; }
		leverDisp = lampDisp = null;
		leverHit = null;
	}

	private static <T extends Entity> T fx(T e) {
		fixtures.add(e);
		return e;
	}

	/** Пульт: стол перед стулом, 8 клавиш 4×2. */
	private static void spawnKeys(ServerLevel l, int i) {
		double cx = BX[i] + 1.5;
		fx(block(l, Blocks.DARK_OAK_PLANKS.defaultBlockState(), new Vec3(cx - 0.8, 64.0, -28.75), new Vector3f(1.6f, 0.72f, 0.6f)));
		fx(block(l, Blocks.BLACK_CONCRETE.defaultBlockState(), new Vec3(cx - 0.74, 64.72, -28.7), new Vector3f(1.48f, 0.02f, 0.5f)));
		for (int k = 0; k < 8; k++) {
			double x = cx - 0.54 + (k % 4) * 0.36, z = -28.6 + (k / 4) * 0.3;
			Display.BlockDisplay d = fx(block(l, KEY_BLOCKS[keyColor[k]].defaultBlockState(), new Vec3(x - 0.15, 64.74, z - 0.12), new Vector3f(0.3f, 0.07f, 0.24f)));
			d.setBrightnessOverride(new Brightness(11, 11));
			keyDisp[k] = d;
			keyHit[k] = fx(hitbox(l, new Vec3(x, 64.72, z), 0.29f, 0.12f));
		}
	}

	/** Плакат «звук → цвет» — текст видит только тот, у кого «Глаза». В кабинке с клавишами — на боковой стенке. */
	private static void spawnPoster(ServerLevel l, int i, boolean side) {
		double cx = BX[i] + 1.5;
		UUID viewer = boothPlayer[i];
		Vec3 at;
		Quaternionf rot;
		if (side) {
			double wx = BX[i] + 2.9;
			fx(block(l, Blocks.SPRUCE_PLANKS.defaultBlockState(), new Vec3(wx + 0.02, 64.45, -29.45), new Vector3f(0.05f, 1.5f, 1.3f)));
			at = new Vec3(wx, 64.6, -28.8);
			rot = new Quaternionf().rotateY((float) (-Math.PI / 2));
		} else {
			fx(block(l, Blocks.SPRUCE_PLANKS.defaultBlockState(), new Vec3(cx - 0.7, 64.45, -28.15), new Vector3f(1.4f, 1.5f, 0.05f)));
			fx(block(l, Blocks.DARK_OAK_FENCE.defaultBlockState(), new Vec3(cx - 0.1, 64.0, -28.12), new Vector3f(0.2f, 0.5f, 0.2f)));
			at = new Vec3(cx, 64.6, -28.18);
			rot = new Quaternionf().rotateY((float) Math.PI);
		}
		MutableComponent t = Component.literal("АЗБУКА ВАДИМА\n").withColor(0xE0505A);
		for (int s = 0; s < 8; s++) {
			t.append(Component.literal("■ ").withColor(COLORS[colorOf[s]]));
			t.append(Component.literal(SOUND_NAMES[s] + (s < 7 ? "\n" : "")).withColor(0xEFE6D6));
		}
		Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		d.setPos(at.x, at.y, at.z);
		d.setTransformation(new Transformation(null, rot, new Vector3f(0.42f), null));
		d.setBackgroundColor(0xF0201814);
		d.setBrightnessOverride(new Brightness(13, 13));
		d.setText(t);
		d.setLineWidth(240);
		d.setViewRange(1f);
		d.addTag(SECRET);
		if (viewer != null) secretViewer.put(d, viewer);
		add(l, d);
		fx(d);
	}

	/** Магнитофон с рычагом «прослушать запись». Один в кабинке — по центру; вместе с клавишами (тест одному) — сбоку справа. */
	private static void spawnTape(ServerLevel l, int i, boolean side) {
		double cx = side ? BX[i] + 0.35 : BX[i] + 1.5;
		double z = side ? -28.6 : -28.45;
		double w = side ? 0.6 : 1.4;
		fx(block(l, Blocks.DARK_OAK_PLANKS.defaultBlockState(), new Vec3(cx - w / 2, 64.0, z - 0.3), new Vector3f((float) w, 0.72f, 0.6f)));
		// корпус магнитофона, окошко с катушками, лампа «ВОСПР.»
		double rx = side ? cx - 0.25 : cx - 0.55, rw = side ? 0.5 : 0.8, rz = side ? z - 0.28 : z - 0.22;
		fx(block(l, Blocks.SMOOTH_SANDSTONE.defaultBlockState(), new Vec3(rx, 64.72, rz), new Vector3f((float) rw, 0.34f, side ? 0.3f : 0.42f)));
		fx(block(l, Blocks.BLACK_CONCRETE.defaultBlockState(), new Vec3(rx + 0.05, 64.86, rz - 0.01), new Vector3f((float) rw - 0.1f, 0.16f, 0.02f)));
		lampDisp = fx(block(l, Blocks.REDSTONE_LAMP.defaultBlockState(), new Vec3(rx + 0.04, 65.06, rz + 0.04), new Vector3f(0.1f)));
		// рычаг: модель рычага по центру своего блока
		double lx = side ? cx : cx + 0.45, lz = side ? z + 0.15 : z;
		leverDisp = fx(block(l, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR), new Vec3(lx - 0.5, 64.72, lz - 0.5), new Vector3f(1f)));
		leverHit = fx(hitbox(l, new Vec3(side ? cx : cx + 0.1, 64.7, z), side ? 0.6f : 1.15f, 0.6f));
	}

	private static void leverState(boolean on) {
		if (leverDisp != null) leverDisp.setBlockState(Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.POWERED, on));
		if (lampDisp != null) {
			lampDisp.setBlockState(Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, on));
			lampDisp.setBrightnessOverride(on ? new Brightness(15, 15) : null);
		}
	}

	private static void pullLever(ServerLevel l, ServerPlayer p) {
		if (phase != P_ROUND) return;
		int b = boothOf(p.getUUID());
		if (b < 0 || (boothRole[b] & ROLE_EAR) == 0) return;
		if (playUntil > 0) return;
		playUntil = REC_TICKS[rec];
		leverState(true);
		l.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.6f);
		// запись слышит только тот, кто в этой кабинке
		p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.ROOM2_REC[rec]),
				SoundSource.MASTER, p.getX(), p.getEyeY(), p.getZ(), 1f, 1f, l.getRandom().nextLong()));
	}

	private static void pressKey(ServerLevel l, ServerPlayer p, int k) {
		if (phase != P_ROUND) return;
		int b = boothOf(p.getUUID());
		if (b < 0 || (boothRole[b] & ROLE_HANDS) == 0) return;
		long now = l.getGameTime();
		Long last = lastPress.get(p.getUUID());
		if (last != null && now - last < 6) return;
		lastPress.put(p.getUUID(), now);
		int color = keyColor[k];
		keyDown(k);
		l.playSound(null, keyDisp[k].getX(), 65, keyDisp[k].getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.7f, KEY_PITCH[color]);
		if (color == colorOf[seq[pos]]) {
			pos++;
			if (pos >= 8) roundDone(l);
		} else shock(l, false);
	}

	private static void keyDown(int k) {
		Display.BlockDisplay d = keyDisp[k];
		if (d == null) return;
		d.setTransformationInterpolationDelay(0);
		d.setTransformationInterpolationDuration(1);
		d.setTransformation(new Transformation(new Vector3f(0, -0.04f, 0), null, new Vector3f(0.3f, 0.07f, 0.24f), null));
		d.setBrightnessOverride(new Brightness(15, 15));
		keyAnim[k] = 5;
	}

	private static void keyUp(int k) {
		Display.BlockDisplay d = keyDisp[k];
		if (d == null) return;
		d.setTransformationInterpolationDelay(0);
		d.setTransformationInterpolationDuration(2);
		d.setTransformation(new Transformation(null, null, new Vector3f(0.3f, 0.07f, 0.24f), null));
		d.setBrightnessOverride(new Brightness(11, 11));
	}

	/** Удар током всех сидящих. timeout — время вышло (сразу смерть). */
	private static void shock(ServerLevel l, boolean timeout) {
		shocks++;
		lives = timeout ? 0 : Math.max(0, lives - 1);
		for (int i = 0; i < 3; i++) {
			UUID u = boothPlayer[i];
			if (u == null) continue;
			Vec3 c = chairPos(i);
			l.playSound(null, c.x, 65.5, c.z, ModSounds.ZAVOD_SPARK, SoundSource.BLOCKS, 1f, 0.8f);
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, 65.2, c.z, 30, 0.35, 0.5, 0.35, 0.15);
		}
		l.playSound(null, BOARD.x, 66, -28.5, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.7f, 1.4f);
		for (ServerPlayer p : here(l.getServer()))
			if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodFinal.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodFinal(1, 0));
		// клавиши вспыхивают
		for (int k = 0; k < 8; k++) if (keyDisp[k] != null) { keyDown(k); keyAnim[k] = 8; }
		VadimNaTrone.LOG.info("[room2] удар током ({}), жизней {}", timeout ? "время вышло" : "ошибка на звуке " + (pos + 1), lives);
		showBoard();
		if (lives <= 0) { lose(l); return; }
		pos = 0;
		restartHint = 60;
	}

	private static void roundDone(ServerLevel l) {
		l.playSound(null, BOARD.x, BOARD.y, BOARD.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.2f, 1.0f);
		l.playSound(null, BOARD.x, BOARD.y, BOARD.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.2f, 1.26f);
		l.playSound(null, BOARD.x, BOARD.y, BOARD.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.2f, 1.5f);
		VadimNaTrone.LOG.info("[room2] раунд {} пройден, осталось {} с", round, timeLeft / 20);
		clearFixtures();
		playUntil = -1;
		if (round >= 3) { win(l); return; }
		round++;
		for (int i = 0; i < 3; i++) setLamp(l, i, false);
		doors(l, true);
		stable = 0;
		someoneLeft = false;
		setPhase(P_BREAK);
		showBoard();
		message.setText(Component.literal("ГОТОВО").withColor(0xFF3B2F));
	}

	private static void roundTick(ServerLevel l) {
		timeLeft--;
		int sec = (timeLeft + 19) / 20;
		if (timeLeft % 20 == 0) {
			showBoard();
			if (sec <= 10 && sec > 0) l.playSound(null, BOARD.x, BOARD.y, BOARD.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 1.4f, 1.2f);
		}
		if (sec <= 10 && timeLeft % 20 == 10) for (Display.ItemDisplay d : digits) setModel(d, null);
		if (timeLeft <= 0) shock(l, true);
	}

	private static void lose(ServerLevel l) {
		clearFixtures();
		playUntil = -1;
		setPhase(P_LOST);
		showBoard();
		message.setText(Component.literal("ВЫ ТРУПЫ").withColor(0xFF3B2F));
		VadimNaTrone.LOG.info("[room2] проигрыш (раунд {})", round);
	}

	/** После смерти: все встали у кабинок, двери закрыты, жизни 3, раунд 1 — снова кнопка. */
	private static void resetToReady(ServerLevel l) {
		everyoneOut(l);
		for (int i = 0; i < 3; i++) { setDoor(l, i, false); setLamp(l, i, false); boothRole[i] = 0; }
		lives = 3;
		round = 1;
		usedRec.clear();
		message.setText(Component.empty());
		setPhase(P_READY);
		showBoard();
	}

	private static void win(ServerLevel l) {
		setPhase(P_WON);
		message.setText(Component.literal("ХМ.").withColor(0xFF3B2F));
		VadimNaTrone.LOG.info("[room2] победа — газ");
	}

	private static void wonTick(ServerLevel l, MinecraftServer server) {
		if (phaseTick == 10) say(l, ModSounds.ROOM2_WIN);
		List<ServerPlayer> all = new ArrayList<>();
		for (ServerPlayer p : l.players()) if (!p.isSpectator() && inStudio(p.getX(), p.getZ())) all.add(p);
		int g = phaseTick - WIN_GAS;
		if (g == 0) for (ServerPlayer p : all) p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 320, 0, false, false));
		if (g >= 0 && g % 100 == 0) for (ServerPlayer p : all)
			if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodFinal.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodFinal(2, g));
		if (g == 60 || g == 110 || g == 150) for (ServerPlayer p : all)
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, g == 60 ? 1 : g == 110 ? 3 : 6, false, false));
		if (g >= 0 && g < 230 && g % 2 == 0) {
			for (int i = 0; i < 3; i++) {
				double x = BX[i] + 1.5, z = -29;
				l.sendParticles(new DustParticleOptions(0x7FCB3B, 2.2f), x, 66.6, z, 5, 0.6, 0.1, 0.6, 0.0);
				if (g > 40) l.sendParticles(new DustParticleOptions(0x6AA834, 2.6f), x, 64.6 + Math.min(1.8, g / 60.0), z, 4, 0.8, 0.4, 0.8, 0.0);
			}
			for (double[] v : new double[][]{{-8, -17}, {3, -17}, {-2.5, -24}}) {
				l.sendParticles(new DustParticleOptions(0x7FCB3B, 2.4f), v[0], 70.3, v[1], 6, 0.4, 0.1, 0.4, 0.0);
				l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, v[0], 70.3, v[1], 1, 0.2, 0.05, 0.2, 0.0);
			}
		}
		if (phaseTick >= WIN_MOVE) {
			State st = state(server);
			st.won = true;
			st.setDirty();
			for (ServerPlayer p : all) {
				p.removeEffect(MobEffects.NAUSEA);
				p.removeEffect(MobEffects.SLOWNESS);
				toRoom3(p);
			}
			for (int i = 0; i < 3; i++) boothPlayer[i] = null;
			setPhase(P_DONE);
			VadimNaTrone.LOG.info("[room2] пройдена, {} игр. в комнате 3", all.size());
		}
	}

	static void toRoom3(ServerPlayer p) {
		ServerLevel l = Places.rooms(p.level().getServer());
		if (l == null) return;
		if (p.isPassenger()) p.stopRiding();
		p.teleportTo(l, ROOM3_SPAWN.x, ROOM3_SPAWN.y, ROOM3_SPAWN.z, Set.of(), 180f, 0f, true);
		p.fallDistance = 0;
		if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodFinal.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodFinal(3, 0));
	}

	private static void say(ServerLevel l, SoundEvent snd) {
		l.playSound(null, BOARD.x, BOARD.y, BOARD.z, snd, SoundSource.RECORDS, 1f, 1f);
	}

	// ======================================================================= табло

	private static void showBoard() {
		if (digits[0] == null) return;
		int t = phase == P_ROUND ? timeLeft : ROUND_TIME[Math.min(2, round - 1)];
		boolean showTime = phase == P_ROUND || phase == P_READY || phase == P_SEAT || phase == P_BREAK;
		if (phase == P_BREAK) t = ROUND_TIME[Math.min(2, round - 1)];
		int sec = Math.max(0, (t + 19) / 20);
		String[] g = {"seg_" + (sec / 60 % 10), "seg_colon", "seg_" + (sec % 60 / 10), "seg_" + (sec % 10)};
		for (int k = 0; k < 4; k++) setModel(digits[k], showTime && phase != P_BREAK ? "room1_" + g[k] : null);
		if (roundText != null) roundText.setText(phase >= P_READY && phase <= P_LOST
				? Component.literal("РАУНД\n" + Math.min(round, 3) + "/3").withColor(0xFF3B2F) : Component.empty());
		for (int k = 0; k < 3; k++) if (lifeLamp[k] != null) {
			boolean on = k < lives && phase >= P_READY && phase <= P_LOST;
			lifeLamp[k].setBlockState(on ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
			lifeLamp[k].setBrightnessOverride(on ? new Brightness(15, 15) : new Brightness(3, 3));
		}
		if (phase != P_TITLE && phase != P_BREAK && phase != P_LOST && phase != P_WON && message != null) message.setText(Component.empty());
	}

	// ======================================================================= клиентам

	private static final Map<UUID, Long> sentKey = new HashMap<>();
	private static final Map<UUID, Long> lastHint = new HashMap<>();

	private static void sendState(ServerPlayer p, boolean force) {
		int b = boothOf(p.getUUID());
		boolean locked = b >= 0 && mustSit();
		long key = phase * 100000L + lives * 10000L + shocks * 10L + (locked ? 1 : 0);
		Long last = sentKey.get(p.getUUID());
		if (!force && last != null && last == key) return;
		sentKey.put(p.getUUID(), key);
		if (ServerPlayNetworking.canSend(p, HubPayloads.Room2State.TYPE))
			ServerPlayNetworking.send(p, new HubPayloads.Room2State(phase, phaseTick, lives, locked, shocks));
	}

	private static void hints(List<ServerPlayer> ps, long now) {
		if (capUp > 0 && --capUp == 0 && buttonCap != null) {
			buttonCap.setTransformationInterpolationDelay(0);
			buttonCap.setTransformationInterpolationDuration(3);
			buttonCap.setTransformation(new Transformation(null, null, new Vector3f(0.4f, 0.14f, 0.4f), null));
		}
		for (ServerPlayer p : ps) {
			int b = boothOf(p.getUUID());
			int hint = restartHint > 0 && b >= 0 && (boothRole[b] & ROLE_HANDS) != 0 ? H_RESTART : 0;
			Long last = lastHint.get(p.getUUID());
			if (last != null && last == hint && now % 40 != 0) continue;
			lastHint.put(p.getUUID(), (long) hint);
			if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodHint.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodHint(hint, 0f));
		}
	}

	// ======================================================================= выброшенная пластинка

	public static boolean onItemLoad(Entity e) {
		if (!(e instanceof ItemEntity it) || !it.getItem().is(ModItems.RECORD)) return false;
		ServerLevel l = (ServerLevel) e.level();
		it.discard();
		l.getServer().execute(() -> {
			for (ServerPlayer p : l.getServer().getPlayerList().getPlayers())
				if (p.getInventory().contains(st -> st.is(ModItems.RECORD))) return;
			if (phase == P_IN && recordShow == null) putRecord(l);
		});
		return true;
	}

	// ======================================================================= сохранение и отладка

	public static State state(MinecraftServer s) {
		ServerLevel l = Places.rooms(s);
		return l == null ? new State() : l.getDataStorage().computeIfAbsent(State.TYPE);
	}

	public static boolean won(MinecraftServer s) { return state(s).won; }

	public static boolean entered(MinecraftServer s) { return state(s).entered; }

	/** /vadim room2 [skip] — всех в студию, плита внизу, всё заново (skip — без пластинки, сразу кнопка). */
	public static void restart(MinecraftServer server, boolean skip) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		Room1.markWon(server);
		State st = state(server);
		st.entered = true;
		st.won = false;
		st.setDirty();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) continue;
			if (p.isPassenger()) p.stopRiding();
			if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
			if (!inStudio(p.getX(), p.getZ()) || p.level() != l)
				p.teleportTo(l, STUDIO_SPAWN.x, STUDIO_SPAWN.y, STUDIO_SPAWN.z, Set.of(), 180f, 0f, true);
		}
		setup(l);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) clearRecord(p);
		if (skip) {
			remove(recordShow);
			remove(recordHit);
			recordShow = null;
			recordHit = null;
			setPhase(P_READY);
			showBoard();
		}
	}

	/** /vadim room2 reset — плита наверх, комната 2 не начата, все — к выходу из комнаты 1. */
	public static void reset(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		State st = state(server);
		st.entered = false;
		st.won = false;
		st.setDirty();
		setup(l);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || p.level() != l) continue;
			if (p.isPassenger()) p.stopRiding();
			clearRecord(p);
			p.teleportTo(l, Room1.AFTER_WIN_SPAWN.x, Room1.AFTER_WIN_SPAWN.y, Room1.AFTER_WIN_SPAWN.z, Set.of(), 180f, 0f, true);
		}
	}

	private static void clearRecord(ServerPlayer p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ModItems.RECORD)) inv.setItem(i, ItemStack.EMPTY);
	}

	public static void forceWin(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null || phase == P_DONE) return;
		clearFixtures();
		win(l);
	}

	public static void forceStart(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		if (phase == P_IN || phase == P_RULES) { remove(recordShow); remove(recordHit); recordShow = null; recordHit = null; }
		startSeat(l);
	}

	public static String seqString() {
		if (phase != P_ROUND) return "раунд не идёт";
		StringBuilder sb = new StringBuilder("запись " + (rec + 1) + ", нажато " + pos + "/8: ");
		for (int s : seq) sb.append(SOUND_NAMES[s]).append(" (").append(COLOR_NAMES[colorOf[s]]).append(") ");
		return sb.toString();
	}

	public static String debug() {
		return "фаза " + phase + ", тик " + phaseTick + ", раунд " + round + ", жизни " + lives + ", осталось " + timeLeft / 20
				+ " с, роли " + boothRole[0] + "/" + boothRole[1] + "/" + boothRole[2] + ", сидят " + seatedCount();
	}

	public static final class State extends SavedData {
		static final Codec<State> CODEC = Codec.INT.xmap(v -> {
			State s = new State();
			s.entered = (v & 1) != 0;
			s.won = (v & 2) != 0;
			return s;
		}, s -> (s.entered ? 1 : 0) | (s.won ? 2 : 0));
		static final SavedDataType<State> TYPE = new SavedDataType<>("vadim_room2", State::new, CODEC, null);
		boolean entered, won;
	}
}
