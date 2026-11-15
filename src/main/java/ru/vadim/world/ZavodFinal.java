package ru.vadim.world;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ru.vadim.VadimNaTrone;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Этап 3г — финал завода. За стеной мониторов проём x=−18, z −55..−54, y 64..66, над ним в стене спрятана
 * стальная плита-гильотина. Когда ВСЕ игроки на заводе зашли в комнату за проёмом (x −24..−19, z −58..−51):
 *  0      лязг (zavod_hatch_move), плита падает за 1 с;
 *  20     удар + сопровождение, проём закрыт барьерами, клиентам — «тряхнуло» (ZavodFinal phase 1);
 *  220    (10 с тишины) — газ: шипение и зелёные частицы из двух решёток, клиентам phase 2;
 *         клиент: туман, виньетка, качание → на 8 с падение, сердцебиение, к 11.5 с чернота;
 *  220+260 всех переносит в измерение комнат (Rooms), флаг финала сохраняется, клиентам phase 3 — «очнулись».
 * Перезаход после финала — сразу в комнаты. /vadim zavod gas — запустить сразу, /vadim zavod reset — вернуть плиту.
 */
public final class ZavodFinal {
	public static final String TAG = "vadim_zavod_final";
	/** Решётки вентиляции под потолком комнаты. */
	public static final BlockPos[] VENTS = {new BlockPos(-21, 68, -57), new BlockPos(-21, 68, -52)};
	private static final double PLATE_X = -17.75, PLATE_Z = -55.05, PLATE_UP = 67.0, PLATE_DOWN = 64.0;
	private static final int FALL = 20, GAS = FALL + 200, MOVE = GAS + 260;

	private static Display.BlockDisplay plate;
	private static int t = -1;
	private static final List<ServerPlayer> inside = new ArrayList<>();

	private ZavodFinal() {}

	/** Игрок в комнате за проёмом. */
	public static boolean inRoom(double x, double y, double z) {
		return x > -24.0 && x < -18.35 && z > -58.0 && z < -50.0 && y < 69;
	}

	/** Старт сервера / пересборка завода: плита наверху (или внизу, если финал уже был). */
	static void setup(ServerLevel z) {
		t = -1;
		inside.clear();
		boolean done = done(z.getServer());
		spawnPlate(z, done ? PLATE_DOWN : PLATE_UP);
		doorway(z, done);
	}

	private static void spawnPlate(ServerLevel z, double y) {
		if (plate != null && !plate.isRemoved()) plate.discard();
		Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(z, EntitySpawnReason.TRIGGERED);
		if (d == null) return;
		d.setBlockState(Blocks.IRON_BLOCK.defaultBlockState());
		d.setPos(PLATE_X, y, PLATE_Z);
		// толщина 0.5, высота 3, ширина 2.1 (заходит в раму)
		d.setTransformation(new Transformation(new Matrix4f().scale(0.5f, 3.0f, 2.1f)));
		d.setViewRange(1f);
		d.addTag(ZavodDoor.TAG);
		d.addTag(TAG);
		ZavodDoor.keep(d);
		z.addFreshEntity(d);
		plate = d;
	}

	/** Проём: закрыт невидимой стеной (после удара) или свободен. */
	private static void doorway(ServerLevel z, boolean closed) {
		for (int y = 64; y <= 66; y++)
			for (int zz = -55; zz <= -54; zz++)
				z.setBlock(new BlockPos(-18, y, zz), closed ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	public static void tick(ServerLevel z) {
		MinecraftServer server = z.getServer();
		if (t < 0) {
			if (done(server)) return;
			List<ServerPlayer> ps = players(z);
			if (ps.isEmpty()) return;
			for (ServerPlayer p : ps) if (!inRoom(p.getX(), p.getY(), p.getZ())) return;
			start(z);
			return;
		}
		t++;
		if (t == 4 && plate != null) {
			plate.setPosRotInterpolationDuration(FALL - 4);
			plate.teleportTo(PLATE_X, PLATE_DOWN, PLATE_Z);
		}
		if (t == FALL) {
			Vec3 door = new Vec3(-18, 65.5, -54);
			z.playSound(null, door.x, door.y, door.z, ModSounds.ZAVOD_HATCH_SLAM, SoundSource.BLOCKS, 1f, 1f);
			for (ServerPlayer p : inside)
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
						net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.ZAVOD_HATCH_SLAM_BED),
						SoundSource.MASTER, p.getX(), p.getEyeY(), p.getZ(), 1f, 1f, z.getRandom().nextLong()));
			z.sendParticles(ParticleTypes.POOF, door.x, 64.2, door.z, 20, 0.3, 0.05, 1.0, 0.03);
			doorway(z, true);
			send(1, 0);
		}
		if (t >= GAS && t < MOVE) {
			int g = t - GAS;
			if (g == 0) {
				send(2, 0);
				for (ServerPlayer p : inside) p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 320, 0, false, false));
			}
			if (g % 100 == 50) send(2, g);   // пересинхронизация
			if (g == 60) slow(1);
			if (g == 110) slow(3);
			if (g == 150) slow(6);
			if (g < 230 && g % 2 == 0)
				for (BlockPos v : VENTS) {
					double x = v.getX() + 0.5, y = v.getY() + 0.3, zz = v.getZ() + 0.5;
					z.sendParticles(new DustParticleOptions(0x7FCB3B, 2.2f), x, y, zz, 6, 0.35, 0.1, 0.35, 0.0);
					z.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, zz, 1, 0.2, 0.05, 0.2, 0.0);
					if (g > 40) z.sendParticles(new DustParticleOptions(0x6AA834, 2.6f), -21.5, 64.6 + Math.min(3, g / 60.0), -54.5, 8, 2.4, 0.5, 3.0, 0.0);
				}
		}
		if (t == MOVE) {
			State st = z.getDataStorage().computeIfAbsent(State.TYPE);
			st.done = true;
			st.setDirty();
			for (ServerPlayer p : new ArrayList<>(inside)) {
				if (p.hasDisconnected()) continue;
				p.removeEffect(MobEffects.NAUSEA);
				p.removeEffect(MobEffects.SLOWNESS);
				Rooms.send(p);
			}
			VadimNaTrone.LOG.info("Завод: финал, {} игр. в комнатах", inside.size());
			t = -1;
			inside.clear();
		}
	}

	private static void start(ServerLevel z) {
		t = 0;
		inside.clear();
		inside.addAll(players(z));
		z.playSound(null, -18, 66.5, -54, ModSounds.ZAVOD_HATCH_MOVE, SoundSource.BLOCKS, 1f, 1f);
		VadimNaTrone.LOG.info("Завод: гермодверь закрывается ({} игр.)", inside.size());
	}

	/** /vadim zavod gas — всех на заводе в комнату, дверь сразу падает, газ через 10 с. */
	public static void force(MinecraftServer server) {
		ServerLevel z = Places.zavod(server);
		if (z == null || t >= 0 || players(z).isEmpty()) return;
		for (ServerPlayer p : players(z))
			if (!inRoom(p.getX(), p.getY(), p.getZ())) p.teleportTo(z, -21.5, 64, -54.5, java.util.Set.of(), 90f, 0f, true);
		start(z);
	}

	/** /vadim zavod reset: плита наверх, проём открыт, флаг финала снят. */
	static void reset(ServerLevel z) {
		State st = z.getDataStorage().computeIfAbsent(State.TYPE);
		st.done = false;
		st.setDirty();
	}

	private static void slow(int amp) {
		for (ServerPlayer p : inside) p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, amp, false, false));
	}

	private static void send(int phase, int elapsed) {
		for (ServerPlayer p : inside)
			if (!p.hasDisconnected() && ServerPlayNetworking.canSend(p, HubPayloads.ZavodFinal.TYPE))
				ServerPlayNetworking.send(p, new HubPayloads.ZavodFinal(phase, elapsed));
	}

	private static List<ServerPlayer> players(ServerLevel z) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : z.players()) if (!p.isSpectator()) out.add(p);
		return out;
	}

	public static boolean done(MinecraftServer server) {
		ServerLevel z = Places.zavod(server);
		return z != null && z.getDataStorage().computeIfAbsent(State.TYPE).done;
	}

	public static boolean isPlate(net.minecraft.world.entity.Entity e) { return e == plate; }

	// ======================================================================= сохранение

	static final class State extends SavedData {
		static final Codec<State> CODEC = Codec.BOOL.xmap(b -> {
			State s = new State();
			s.done = b;
			return s;
		}, s -> s.done);
		static final SavedDataType<State> TYPE = new SavedDataType<>("vadim_zavod_final", State::new, CODEC, null);
		boolean done;
	}
}
