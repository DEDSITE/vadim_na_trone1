package ru.vadim.world;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.Room4Blocks;
import ru.vadim.item.ModItems;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Комната 4 «Бэкрумс», этап 7б: роли, электрик (щитки, предохранители, рубильники), питание секторов, темнота и улыбаки.
 * Роли: 0 — электрик, 1 — связист, 2 — оператор; случайно при входе (при смерти всех — заново, этап 7д).
 * Сектор без тока: лампы моргают, иногда весь сектор гаснет на несколько секунд. Замыкание — свет сектора гаснет на 15 с.
 * Улыбака (вариант A): в темноте зоны с улыбаками или погасшего сектора появляется, подлетает, бьёт на 2 сердца; на свету рассыпается.
 */
public final class Room4Power {
	public static final int ELECTRIC = 0, SIGNAL = 1, OPERATOR = 2;
	public static final int H_ROLE_E = 70, H_ROLE_S = 71, H_ROLE_O = 72, H_NOT_YOURS = 73, H_FUSE = 74, H_BOX = 75, H_BOX_NEED = 76,
			H_POWERED = 77, H_SHORT = 78, H_FUSE_GOT = 79;
	static final String TAG = "vadim_r4", FUSE_TAG = "vadim_r4_fuse", SMILER_TAG = "vadim_r4_smiler";
	/** Порог перегрузки (стрелка в красном). */
	public static final int OVERLOAD = 90;

	static BlockPos[] boxes;
	static Direction[] boxFacing;
	static Vec3[] fuses;
	static int[][] values;
	static int[] zone;
	static long seedFor = Long.MIN_VALUE;
	static final int[] mask = new int[3];
	static final long[] blackoutUntil = new long[3], nextBlackout = new long[3];
	static final Map<Integer, Entity[]> fuseEnt = new HashMap<>();
	static final Map<UUID, Smiler> smilers = new HashMap<>();
	static final Map<UUID, int[]> timedHint = new HashMap<>();   // {код, до какого тика}
	static final Map<UUID, Integer> lastHint = new HashMap<>();
	static final List<Entity> live = new ArrayList<>();

	private Room4Power() {}

	static final class Smiler {
		Display.ItemDisplay d;
		int darkTicks, cd, age;
		Vec3 pos;
	}

	// ======================================================================= планировка щитков, предохранителей, рубильников

	static void compute(ServerLevel l) {
		Room4.Layout lay = Room4.layout(l);
		if (seedFor == lay.seed && boxes != null) return;
		seedFor = lay.seed;
		Random r = new Random(lay.seed * 13 + 5);
		boxes = new BlockPos[3];
		boxFacing = new Direction[3];
		fuses = new Vec3[9];
		values = new int[3][5];
		zone = new int[6];
		for (int s = 0; s < 3; s++) {
			int[] sp = Room4.SPAWN_CELLS[s];
			List<int[]> cells = new ArrayList<>(), darkCells = new ArrayList<>(), anyCells = new ArrayList<>();
			for (int i = 0; i < Room4.N; i++) for (int j = 0; j < Room4.N; j++) {
				if (Room4.sector(i, j) != s || (i == sp[0] && j == sp[1]) || Room4.inside(i, j, Room4.HALL_I0, Room4.HALL_I1, Room4.HALL_J0, Room4.HALL_J1)) continue;
				int d = Math.max(Math.abs(i - sp[0]), Math.abs(j - sp[1]));
				if (lay.dark[i][j] == 0 && d >= 2 && d <= 5) cells.add(new int[]{i, j});
				if (lay.dark[i][j] == 1 || lay.dark[i][j] == 3) darkCells.add(new int[]{i, j});
				else if (lay.dark[i][j] == 0) anyCells.add(new int[]{i, j});
			}
			java.util.Collections.shuffle(cells, r);
			for (int[] c : cells) if (findWall(lay, c[0], c[1], s)) break;
			if (boxes[s] == null) VadimNaTrone.LOG.error("[room4] не нашлось места под щиток сектора {}", s + 1);
			java.util.Collections.shuffle(darkCells, r);
			java.util.Collections.shuffle(anyCells, r);
			for (int k = 0; k < 3; k++) {
				List<int[]> from = (k < 2 && darkCells.size() > k) ? darkCells : anyCells;
				int[] c = from.get(k < 2 ? k : 0);
				int ox, oz;
				do { ox = 2 + r.nextInt(7); oz = 2 + r.nextInt(7); } while (ox == 5 || oz == 5);
				fuses[s * 3 + k] = new Vec3(Room4.X0 + c[0] * Room4.CELL + ox + 0.5, Room4.FLOOR + 1, Room4.Z0 + c[1] * Room4.CELL + oz + 0.5);
			}
			// рубильники: 5 токов, одна комбинация (2–3 рубильника) попадает в зелёную зону 35..75, все вместе — перегрузка
			while (true) {
				int total = 0;
				for (int k = 0; k < 5; k++) { values[s][k] = 6 + r.nextInt(29); total += values[s][k]; }
				int m = 0;
				while (Integer.bitCount(m) < 2 || Integer.bitCount(m) > 3) m = r.nextInt(32);
				int sum = 0;
				for (int k = 0; k < 5; k++) if ((m >> k & 1) != 0) sum += values[s][k];
				if (sum >= 35 && sum <= 75 && total >= OVERLOAD + 5) { zone[s * 2] = sum - 3; zone[s * 2 + 1] = sum + 3; break; }
			}
		}
	}

	/** Стена на краю клетки, перед которой пусто, с соседями-стенами по бокам: туда щиток (смотрит в клетку). */
	private static boolean findWall(Room4.Layout lay, int i, int j, int s) {
		int x0 = Room4.X0 + i * Room4.CELL, z0 = Room4.Z0 + j * Room4.CELL;
		for (int k = 3; k <= 7; k++) {
			int[][] tries = {{x0 + k, z0, 0, 1}, {x0 + k, z0 + Room4.CELL, 0, -1}, {x0, z0 + k, 1, 0}, {x0 + Room4.CELL, z0 + k, -1, 0}};
			for (int[] t : tries) {
				int x = t[0], z = t[1], dx = t[2], dz = t[3];
				if (!lay.wallAt(x, z) || lay.wallAt(x + dx, z + dz)) continue;
				if (!lay.wallAt(x + dz, z + dx) || !lay.wallAt(x - dz, z - dx)) continue;
				boxes[s] = new BlockPos(x, Room4.FLOOR + 2, z);
				boxFacing[s] = dx == 1 ? Direction.EAST : dx == -1 ? Direction.WEST : dz == 1 ? Direction.SOUTH : Direction.NORTH;
				return true;
			}
		}
		return false;
	}

	/** Из Room4.build: щитки в стены. */
	public static void placeBoxes(ServerLevel l) {
		compute(l);
		PState st = state(l.getServer());
		for (int s = 0; s < 3; s++) if (boxes[s] != null)
			l.setBlock(boxes[s], Room4Blocks.FUSEBOX.defaultBlockState().setValue(Room4Blocks.FuseBox.FACING, boxFacing[s])
					.setValue(Room4Blocks.FuseBox.POWERED, st.powered.get(s) != 0), Block.UPDATE_CLIENTS);
	}

	// ======================================================================= регистрация

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Room4Power::tick);
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (level.dimension() != Places.ROOMS || live.contains(e)) return;
			if (e.getTags().contains(FUSE_TAG) || e.getTags().contains(SMILER_TAG)) e.discard();
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.dimension() != Places.ROOMS || !entity.getTags().contains(FUSE_TAG)) return InteractionResult.PASS;
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
			takeFuse((ServerLevel) level, sp, entity);
			return InteractionResult.SUCCESS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.dimension() != Places.ROOMS || !level.getBlockState(hit.getBlockPos()).is(Room4Blocks.FUSEBOX)) return InteractionResult.PASS;
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
			useBox((ServerLevel) level, sp, hit.getBlockPos());
			return InteractionResult.SUCCESS;
		});
		ServerPlayNetworking.registerGlobalReceiver(HubPayloads.Room4PanelAct.TYPE, (p, ctx) -> ctx.server().execute(() -> panelAct(ctx.player(), p)));
		// смерть в бэкрумсе — пока (до 7д) просто встаёт на своём спавне
		ServerLivingEntityEvents.ALLOW_DEATH.register((e, src, amt) -> {
			if (!(e instanceof ServerPlayer p) || !in(p)) return true;
			p.setHealth(p.getMaxHealth());
			Room4.place((ServerLevel) p.level(), p);
			return false;
		});
	}

	public static boolean in(Entity e) {
		return e.level().dimension() == Places.ROOMS && Room4.inRoom4(e.getX(), e.getZ());
	}

	// ======================================================================= роли

	public static void assignRole(ServerPlayer p) {
		PState st = state(p.level().getServer());
		String id = p.getUUID().toString();
		Integer role = st.roles.get(id);
		if (role == null) {
			List<Integer> free = new ArrayList<>(List.of(ELECTRIC, SIGNAL, OPERATOR));
			free.removeAll(st.roles.values());
			role = free.isEmpty() ? new Random().nextInt(3) : free.get(new Random().nextInt(free.size()));
			st.roles.put(id, role);
			st.setDirty();
		}
		timed(p, role == ELECTRIC ? H_ROLE_E : role == SIGNAL ? H_ROLE_S : H_ROLE_O, 200);
	}

	public static int role(ServerPlayer p) {
		Integer r = state(p.level().getServer()).roles.get(p.getUUID().toString());
		return r == null ? -1 : r;
	}

	public static void setRole(ServerPlayer p, int role) {
		PState st = state(p.level().getServer());
		st.roles.put(p.getUUID().toString(), role);
		st.setDirty();
		timed(p, role == ELECTRIC ? H_ROLE_E : role == SIGNAL ? H_ROLE_S : H_ROLE_O, 200);
	}

	/** Все умерли (7д): роли заново. */
	public static void shuffleRoles(MinecraftServer server) {
		PState st = state(server);
		st.roles.clear();
		st.setDirty();
	}

	// ======================================================================= предохранители и щитки

	private static void takeFuse(ServerLevel l, ServerPlayer p, Entity hit) {
		int idx = -1;
		for (var e : fuseEnt.entrySet()) if (e.getValue()[1] == hit) idx = e.getKey();
		if (idx < 0) return;
		if (role(p) != ELECTRIC && !p.isCreative()) { timed(p, H_NOT_YOURS, 60); return; }
		PState st = state(l.getServer());
		if (st.picked.contains(idx)) return;
		st.picked.add(idx);
		st.setDirty();
		for (Entity e : fuseEnt.remove(idx)) { if (e != null) { live.remove(e); e.discard(); } }
		if (!p.getInventory().add(new ItemStack(ModItems.FUSE))) p.drop(new ItemStack(ModItems.FUSE), false);
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
		timed(p, H_FUSE_GOT, 50);
	}

	private static int sectorOfBox(BlockPos pos) {
		for (int s = 0; s < 3; s++) if (pos.equals(boxes[s])) return s;
		return -1;
	}

	private static void useBox(ServerLevel l, ServerPlayer p, BlockPos pos) {
		compute(l);
		int s = sectorOfBox(pos);
		if (s < 0) return;
		if (role(p) != ELECTRIC && !p.isCreative()) { timed(p, H_NOT_YOURS, 60); return; }
		PState st = state(l.getServer());
		if (st.powered.get(s) != 0) { timed(p, H_POWERED, 60); return; }
		int in = st.fuses.get(s);
		while (in < 3) {
			int slot = p.getInventory().findSlotMatchingItem(new ItemStack(ModItems.FUSE));
			if (slot < 0) break;
			p.getInventory().removeItem(slot, 1);
			in++;
			l.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 1.6f);
		}
		if (in != st.fuses.get(s)) { st.fuses.set(s, in); st.setDirty(); }
		if (in < 3) { timed(p, H_BOX_NEED, 80); return; }
		send(p, s, 0);
	}

	private static void send(ServerPlayer p, int s, int result) {
		List<Integer> v = new ArrayList<>();
		for (int k : values[s]) v.add(k);
		if (ServerPlayNetworking.canSend(p, HubPayloads.Room4Panel.TYPE))
			ServerPlayNetworking.send(p, new HubPayloads.Room4Panel(s, v, mask[s], zone[s * 2], zone[s * 2 + 1], result));
	}

	private static void panelAct(ServerPlayer p, HubPayloads.Room4PanelAct a) {
		ServerLevel l = (ServerLevel) p.level();
		int s = a.sector();
		if (s < 0 || s > 2 || boxes == null || boxes[s] == null || !in(p)) return;
		if (role(p) != ELECTRIC && !p.isCreative()) return;
		if (p.distanceToSqr(Vec3.atCenterOf(boxes[s])) > 7 * 7) return;
		PState st = state(l.getServer());
		if (st.powered.get(s) != 0 || st.fuses.get(s) < 3) return;
		int m = a.mask() & 31;
		if (m != mask[s]) l.playSound(null, boxes[s], SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 0.7f);
		mask[s] = m;
		int sum = 0;
		for (int k = 0; k < 5; k++) if ((m >> k & 1) != 0) sum += values[s][k];
		if (sum >= OVERLOAD || a.supply() && (sum < zone[s * 2] || sum > zone[s * 2 + 1])) {
			shortCircuit(l, s);
			send(p, s, 1);
			timed(p, H_SHORT, 80);
			return;
		}
		if (a.supply()) {
			power(l, s, true);
			send(p, s, 2);
			for (ServerPlayer o : l.players()) if (in(o)) timed(o, H_POWERED, 80);
			return;
		}
		send(p, s, 0);
	}

	public static void power(ServerLevel l, int s, boolean on) {
		compute(l);
		PState st = state(l.getServer());
		st.powered.set(s, on ? 1 : 0);
		if (on) st.fuses.set(s, 3);
		st.setDirty();
		blackoutUntil[s] = 0;
		if (boxes[s] != null) {
			BlockState b = l.getBlockState(boxes[s]);
			if (b.is(Room4Blocks.FUSEBOX)) l.setBlock(boxes[s], b.setValue(Room4Blocks.FuseBox.POWERED, on), Block.UPDATE_CLIENTS);
			if (on) {
				l.playSound(null, boxes[s], SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 0.8f);
				l.playSound(null, boxes[s], ModSounds.ZAVOD_LAMP_CYCLE, SoundSource.BLOCKS, 0.8f, 1f);
			}
		}
		setSectorLamps(l, s, true);
	}

	private static void shortCircuit(ServerLevel l, int s) {
		mask[s] = 0;
		BlockPos b = boxes[s];
		Vec3 c = Vec3.atCenterOf(b).add(Vec3.atLowerCornerOf(boxFacing[s].getUnitVec3i()).scale(0.6));
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.3, 0.3, 0.3, 0.2);
		l.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.0);
		l.playSound(null, b, ModSounds.ZAVOD_SPARK, SoundSource.BLOCKS, 1.5f, 0.9f);
		l.playSound(null, b, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.4f, 1.8f);
		blackout(l, s, 300);
	}

	/** Сектор гаснет на ticks тиков (все живые лампы). */
	public static void blackout(ServerLevel l, int s, int ticks) {
		blackoutUntil[s] = l.getServer().getTickCount() + ticks;
		setSectorLamps(l, s, false);
		for (ServerPlayer p : l.players()) if (in(p) && sectorOf(p) == s)
			l.playSound(null, p.getX(), p.getY() + 3, p.getZ(), ModSounds.ZAVOD_LAMP_FLICKER3, SoundSource.BLOCKS, 0.6f, 0.7f);
	}

	private static void setSectorLamps(ServerLevel l, int s, boolean lit) {
		Room4.Layout lay = Room4.layout(l);
		for (int k = 0; k < lay.lamps.size(); k++) {
			if (lay.lampSector.get(k) != s) continue;
			BlockPos p = lay.lamps.get(k);
			if (lay.broken.contains(p)) continue;
			BlockState b = l.getBlockState(p);
			if (b.is(Room4Blocks.LAMP) && b.getValue(Room4Blocks.Lamp.LIT) != lit) l.setBlock(p, b.setValue(Room4Blocks.Lamp.LIT, lit), Block.UPDATE_CLIENTS);
		}
	}

	public static boolean powered(MinecraftServer server, int s) { return s >= 0 && state(server).powered.get(s) != 0; }

	public static boolean blackedOut(MinecraftServer server, int s) { return s >= 0 && blackoutUntil[s] > server.getTickCount(); }

	public static int sectorOf(Entity e) {
		int i = (int) Math.floor((e.getX() - Room4.X0) / Room4.CELL), j = (int) Math.floor((e.getZ() - Room4.Z0) / Room4.CELL);
		if (i < 0 || j < 0 || i >= Room4.N || j >= Room4.N) return -1;
		return Room4.sector(i, j);
	}

	// ======================================================================= тик

	private static void tick(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return;
		List<ServerPlayer> here = new ArrayList<>();
		for (ServerPlayer p : l.players()) if (in(p)) here.add(p);
		long now = server.getTickCount();
		if (here.isEmpty()) {
			for (UUID id : new ArrayList<>(smilers.keySet())) dissolve(l, id, null);
			return;
		}
		compute(l);
		PState st = state(server);
		// сытость без регена
		if (now % 20 == 0) for (ServerPlayer p : here) if (!p.isSpectator() && !p.isCreative()) {
			p.getFoodData().setFoodLevel(17);
			p.getFoodData().setSaturation(0f);
		}
		// конец затемнения; случайные затемнения в секторах без тока, где кто-то есть
		for (int s = 0; s < 3; s++) {
			if (blackoutUntil[s] != 0 && blackoutUntil[s] <= now) {
				blackoutUntil[s] = 0;
				setSectorLamps(l, s, true);
			}
			if (st.powered.get(s) != 0) continue;
			boolean someone = false;
			for (ServerPlayer p : here) if (sectorOf(p) == s && !p.isSpectator()) someone = true;
			if (!someone) continue;
			if (nextBlackout[s] == 0) nextBlackout[s] = now + 900 + new Random().nextInt(900);
			if (now >= nextBlackout[s] && blackoutUntil[s] == 0) {
				blackout(l, s, 120 + new Random().nextInt(100));
				nextBlackout[s] = now + 1100 + new Random().nextInt(1100);
			}
		}
		// предохранители: показываем, когда кто-то рядом
		if (now % 20 == 0) for (int k = 0; k < fuses.length; k++) {
			if (st.picked.contains(k)) continue;
			Entity[] e = fuseEnt.get(k);
			if (e != null && !e[0].isRemoved()) continue;
			boolean near = false;
			for (ServerPlayer p : here) if (p.distanceToSqr(fuses[k]) < 40 * 40) near = true;
			if (near) spawnFuse(l, k);
		}
		smilersTick(l, here, now);
		if (now % 10 == 0) hints(l, here, st, now);
	}

	private static void spawnFuse(ServerLevel l, int k) {
		Vec3 at = fuses[k];
		Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		ItemStack st = new ItemStack(Items.PAPER);
		st.set(DataComponents.ITEM_MODEL, VadimNaTrone.id("room4_fuse"));
		d.setItemStack(st);
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setPos(at.x, at.y + 0.03, at.z);
		d.setTransformation(new Transformation(null, new Quaternionf().rotateY(k * 1.3f).rotateX((float) Math.toRadians(90)), new Vector3f(0.55f), null));
		d.addTag(TAG);
		d.addTag(FUSE_TAG);
		Interaction i = EntityType.INTERACTION.create(l, EntitySpawnReason.TRIGGERED);
		i.setPos(at.x, at.y, at.z);
		i.setWidth(0.6f);
		i.setHeight(0.3f);
		i.setResponse(true);
		i.addTag(TAG);
		i.addTag(FUSE_TAG);
		live.add(d);
		live.add(i);
		l.addFreshEntity(d);
		l.addFreshEntity(i);
		fuseEnt.put(k, new Entity[]{d, i});
	}

	// ======================================================================= улыбаки

	private static boolean darkForSmiler(ServerLevel l, ServerPlayer p) {
		BlockPos eye = BlockPos.containing(p.getEyePosition());
		if (l.getBrightness(LightLayer.BLOCK, eye) > 4) return false;
		int s = sectorOf(p);
		return Room4.smilerZone(l, p.getX(), p.getZ()) || blackedOut(l.getServer(), s);
	}

	private static void smilersTick(ServerLevel l, List<ServerPlayer> here, long now) {
		for (UUID id : new ArrayList<>(smilers.keySet())) if (l.getServer().getPlayerList().getPlayer(id) == null || !here.contains(l.getServer().getPlayerList().getPlayer(id)))
			dissolve(l, id, null);
		for (ServerPlayer p : here) {
			if (p.isSpectator() || p.isCreative()) { dissolve(l, p.getUUID(), p); continue; }
			Smiler sm = smilers.computeIfAbsent(p.getUUID(), u -> new Smiler());
			if (!darkForSmiler(l, p)) {
				sm.darkTicks = 0;
				if (sm.d != null) dissolve(l, p.getUUID(), p);
				continue;
			}
			sm.darkTicks++;
			if (sm.d == null) {
				if (sm.darkTicks < 30) continue;
				spawnSmiler(l, p, sm);
				continue;
			}
			sm.age++;
			if (sm.cd > 0) sm.cd--;
			Vec3 eye = p.getEyePosition();
			Vec3 to = eye.subtract(sm.pos);
			double dist = to.length();
			if (dist > 1.2) sm.pos = sm.pos.add(to.normalize().scale(Math.min(0.11, dist - 1.2)));
			if (dist > 24) { dissolve(l, p.getUUID(), p); continue; }
			if (sm.age % 2 == 0) {
				double bob = Math.sin(sm.age * 0.12) * 0.08;
				sm.d.setPosRotInterpolationDuration(2);
				sm.d.teleportTo(sm.pos.x, sm.pos.y - 0.8 + bob, sm.pos.z);
			}
			if (dist < 1.7 && sm.cd == 0) {
				p.hurtServer(l, l.damageSources().magic(), 4f);   // 2 сердца
				sm.cd = 25;
				l.sendParticles(new DustParticleOptions(0xF3EEDC, 1.2f), sm.pos.x, sm.pos.y - 0.2, sm.pos.z, 8, 0.3, 0.3, 0.3, 0.0);
			}
		}
	}

	private static void spawnSmiler(ServerLevel l, ServerPlayer p, Smiler sm) {
		Random r = new Random();
		Vec3 eye = p.getEyePosition();
		Vec3 at = null;
		for (int t = 0; t < 16 && at == null; t++) {
			double a = r.nextDouble() * Math.PI * 2, d = 6 + r.nextDouble() * 3;
			Vec3 c = eye.add(Math.cos(a) * d, 0, Math.sin(a) * d);
			if (Room4.inRoom4(c.x, c.z) && l.getBlockState(BlockPos.containing(c)).isAir()) at = c;
		}
		if (at == null) return;
		sm.pos = at;
		sm.age = 0;
		sm.cd = 20;
		Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(l, EntitySpawnReason.TRIGGERED);
		ItemStack st = new ItemStack(Items.PAPER);
		st.set(DataComponents.ITEM_MODEL, VadimNaTrone.id("room4_smiler"));
		d.setItemStack(st);
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.setBrightnessOverride(new Brightness(15, 15));
		d.setPos(at.x, at.y - 0.8, at.z);
		d.setTransformation(new Transformation(new Vector3f(0, 0.8f, 0), null, new Vector3f(1.6f), null));
		d.setViewRange(1.5f);
		d.addTag(TAG);
		d.addTag(SMILER_TAG);
		live.add(d);
		l.addFreshEntity(d);
		sm.d = d;
		p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.ROOM4_DARK),
				SoundSource.MASTER, p.getX(), p.getEyeY(), p.getZ(), 0.9f, 1f, l.getRandom().nextLong()));
	}

	/** Свет включился (или игрок ушёл): лицо осыпается светлыми «пикселями». */
	private static void dissolve(ServerLevel l, UUID id, ServerPlayer p) {
		Smiler sm = smilers.get(id);
		if (sm == null || sm.d == null) return;
		Vec3 c = sm.pos;
		l.sendParticles(new DustParticleOptions(0xF3EEDC, 1.6f), c.x, c.y, c.z, 30, 0.45, 0.45, 0.45, 0.0);
		l.sendParticles(ParticleTypes.WHITE_ASH, c.x, c.y, c.z, 20, 0.4, 0.4, 0.4, 0.0);
		live.remove(sm.d);
		sm.d.discard();
		sm.d = null;
		if (p != null) p.connection.send(new ClientboundStopSoundPacket(BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.ROOM4_DARK), SoundSource.MASTER));
	}

	// ======================================================================= подсказки

	static void timed(ServerPlayer p, int code, int ticks) {
		timedHint.put(p.getUUID(), new int[]{code, p.level().getServer().getTickCount() + ticks});
		lastHint.remove(p.getUUID());
	}

	private static void hints(ServerLevel l, List<ServerPlayer> here, PState st, long now) {
		for (ServerPlayer p : here) {
			int h = 0;
			int[] t = timedHint.get(p.getUUID());
			if (t != null && t[1] > now) h = t[0];
			else if (role(p) == ELECTRIC) {
				for (int k = 0; k < fuses.length && h == 0; k++) if (!st.picked.contains(k) && p.distanceToSqr(fuses[k]) < 2.5 * 2.5) h = H_FUSE;
				for (int s = 0; s < 3 && h == 0; s++) if (boxes[s] != null && st.powered.get(s) == 0 && p.distanceToSqr(Vec3.atCenterOf(boxes[s])) < 3.2 * 3.2) h = H_BOX;
			}
			Integer last = lastHint.get(p.getUUID());
			if (last != null && last == h && now % 40 != 0) continue;
			lastHint.put(p.getUUID(), h);
			if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodHint.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodHint(h, 0f));
		}
	}

	public static String debug(MinecraftServer server) {
		ServerLevel l = Places.rooms(server);
		if (l == null) return "";
		compute(l);
		PState st = state(server);
		StringBuilder sb = new StringBuilder("роли " + st.roles + ", ток " + st.powered + ", предохранители в щитках " + st.fuses + ", подобрано " + st.picked);
		for (int s = 0; s < 3; s++) sb.append("\nщиток ").append(s + 1).append(": ").append(boxes[s] == null ? "—" : boxes[s].toShortString())
				.append(", токи ").append(java.util.Arrays.toString(values[s])).append(", зона ").append(zone[s * 2]).append("..").append(zone[s * 2 + 1]);
		return sb.toString();
	}

	public static BlockPos box(ServerLevel l, int s) { compute(l); return boxes[s] == null ? null : boxes[s].relative(boxFacing[s], 2); }

	public static void resetAll(ServerLevel l) {
		PState st = state(l.getServer());
		for (int s = 0; s < 3; s++) { st.powered.set(s, 0); st.fuses.set(s, 0); mask[s] = 0; blackoutUntil[s] = 0; nextBlackout[s] = 0; }
		st.picked.clear();
		st.roles.clear();
		st.setDirty();
		for (Entity[] es : fuseEnt.values()) for (Entity e : es) if (e != null) { live.remove(e); e.discard(); }
		fuseEnt.clear();
		placeBoxes(l);
		for (int s = 0; s < 3; s++) setSectorLamps(l, s, true);
	}

	// ======================================================================= сохранение

	static PState state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(PState.TYPE);
	}

	static final class PState extends SavedData {
		static final Codec<PState> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("roles").forGetter(s -> s.roles),
				Codec.INT.listOf().fieldOf("powered").forGetter(s -> s.powered),
				Codec.INT.listOf().fieldOf("fuses").forGetter(s -> s.fuses),
				Codec.INT.listOf().fieldOf("picked").forGetter(s -> s.picked)
		).apply(i, (r, p, f, k) -> {
			PState s = new PState();
			s.roles.putAll(r);
			for (int x = 0; x < 3 && x < p.size(); x++) s.powered.set(x, p.get(x));
			for (int x = 0; x < 3 && x < f.size(); x++) s.fuses.set(x, f.get(x));
			s.picked.addAll(k);
			return s;
		}));
		static final SavedDataType<PState> TYPE = new SavedDataType<>("vadim_room4_power", PState::new, CODEC, null);
		final Map<String, Integer> roles = new HashMap<>();
		final List<Integer> powered = new ArrayList<>(List.of(0, 0, 0)), fuses = new ArrayList<>(List.of(0, 0, 0)), picked = new ArrayList<>();
	}
}
