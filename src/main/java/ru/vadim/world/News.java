package ru.vadim.world;

import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ru.vadim.entity.BarmanEntity;
import ru.vadim.entity.ModEntities;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Этап 2 — катсцена «Новости по телеку» (сервер). Сервер только ведёт часы и делает то, что видно всем:
 * Тимофей поворачивается к телеку и щёлкает пультом, по окончании все переносятся ко входу в заброшку.
 * Камеру, видео на экране, звук, затемнение и приглушение света клиент считает сам по тику старта (ClientNews).
 *
 * Время (секунды от старта, совпадает с ClientNews):
 *  0.8 — Тимофей поворачивается к ТВ; 2.2 — взмах рукой; 2.5 — щелчок пульта; 2.6 — пошёл ролик (37 с);
 *  37.6 — в ролике помехи (сюда прыгаем при пропуске); 40.0 — перенос ко входу в заброшку.
 */
public final class News {
	public static final int TURN = 16, SWING = 44, CLICK = 50, STATIC_AT = 752, END = 800;

	private static boolean active = false;
	private static boolean done = false;   // катсцена уже была на этом сервере — опоздавшие сразу к заброшке
	private static long start = 0;
	private static final Set<UUID> participants = new HashSet<>();
	private static final Set<UUID> votes = new HashSet<>();
	private static boolean skipped = false;

	private News() {}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> votes.remove(h.getPlayer().getUUID()));
		ServerPlayNetworking.registerGlobalReceiver(HubPayloads.NewsSkip.TYPE, (p, ctx) -> vote(ctx.player()));
	}

	public static boolean active() { return active; }

	/** Куда отправлять игрока при заходе: после (или во время) катсцены участник идёт сразу к заброшке. */
	public static boolean goesToZavod(ServerPlayer p) {
		return done || (active && participants.contains(p.getUUID()));
	}

	private static List<ServerPlayer> online(MinecraftServer server) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : participants) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && !p.hasDisconnected()) out.add(p);
		}
		return out;
	}

	private static BarmanEntity barman(ServerLevel hub) {
		for (Entity e : hub.getEntities(ModEntities.BARMAN, e -> true)) if (e instanceof BarmanEntity b) return b;
		return null;
	}

	/** Все нажали «Готов» (или /vadim news): запускаем. */
	public static void start(MinecraftServer server) {
		ServerLevel hub = Places.hub(server);
		if (hub == null || active) return;
		participants.clear();
		votes.clear();
		skipped = false;
		for (ServerPlayer p : server.getPlayerList().getPlayers())
			if (Places.inHub(p) && !p.isSpectator()) participants.add(p.getUUID());
		if (participants.isEmpty()) return;
		active = true;
		start = hub.getGameTime() + 2;   // пара тиков запаса, чтобы пакет успел дойти
		for (ServerPlayer p : online(server)) send(p, new HubPayloads.NewsStart(hub.getGameTime() - start, false));
	}

	/** /vadim news stop — прервать и оставить всех в баре. */
	public static void stop(MinecraftServer server) {
		if (!active) return;
		for (ServerPlayer p : online(server)) send(p, new HubPayloads.NewsEnd(false));
		finish(server);
	}

	private static void finish(MinecraftServer server) {
		active = false;
		votes.clear();
		ServerLevel hub = Places.hub(server);
		BarmanEntity b = hub == null ? null : barman(hub);
		if (b != null) b.setWatchTv(false);
	}

	public static void tick(MinecraftServer server) {
		if (!active) return;
		ServerLevel hub = Places.hub(server);
		if (hub == null) { active = false; return; }
		List<ServerPlayer> on = online(server);
		if (on.isEmpty()) { finish(server); return; }   // все вышли — катсцену сбрасываем
		long t = hub.getGameTime() - start;
		// раз в 5 с пересинхронизируем клиентов (по сети время может уползти)
		if (t > 0 && t % 100 == 0) for (ServerPlayer p : on) send(p, new HubPayloads.NewsStart(t, skipped));
		BarmanEntity b = barman(hub);
		if (b != null) {
			if (t == TURN) b.setWatchTv(true);
			if (t == SWING && !skipped) b.swing(InteractionHand.MAIN_HAND, true);
			if (t == CLICK && !skipped) hub.playSound(null, b.getX(), b.getEyeY(), b.getZ(), ModSounds.TV_CLICK, SoundSource.NEUTRAL, 0.9f, 1f);
		}
		if (t >= END) {
			for (ServerPlayer p : on) {
				sendToZavod(p);
				send(p, new HubPayloads.NewsEnd(true));
			}
			done = true;
			finish(server);
		}
	}

	private static void vote(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		ServerLevel hub = Places.hub(server);
		if (!active || skipped || hub == null || !participants.contains(p.getUUID())) return;
		long t = hub.getGameTime() - start;
		if (t >= STATIC_AT) return;
		votes.add(p.getUUID());
		List<ServerPlayer> on = online(server);
		int total = on.size();
		int n = (int) on.stream().filter(pl -> votes.contains(pl.getUUID())).count();
		if (n >= total) {
			skipped = true;
			start = hub.getGameTime() - STATIC_AT;   // прыгаем сразу в помехи в конце ролика
			BarmanEntity b = barman(hub);
			if (b != null) b.setWatchTv(true);
			for (ServerPlayer pl : on) send(pl, new HubPayloads.NewsStart(hub.getGameTime() - start, true));
		} else {
			for (ServerPlayer pl : on) send(pl, new HubPayloads.NewsVotes(n, total));
		}
	}

	private static void send(ServerPlayer p, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(p, payload.type())) ServerPlayNetworking.send(p, payload);
	}

	// ---------- заброшка ----------

	/** Ко входу в заброшку: снаружи у ворот завода (двор строит Zavod при старте сервера). */
	public static void sendToZavod(ServerPlayer p) {
		ServerLevel z = Places.zavod(p.level().getServer());
		if (z == null) {
			ru.vadim.VadimNaTrone.LOG.error("Нет измерения заброшки — датапак мода не загрузился");
			return;
		}
		if (!p.isCreative()) p.setGameMode(GameType.ADVENTURE);
		if (p.isPassenger()) p.stopRiding();
		Vec3 s = Places.ZAVOD_SPAWN;
		double ox = (p.getRandom().nextDouble() - 0.5) * 3;
		p.teleportTo(z, s.x + ox, s.y, s.z, Set.of(), Places.ZAVOD_YAW, 0f, true);
		p.fallDistance = 0;
	}
}
