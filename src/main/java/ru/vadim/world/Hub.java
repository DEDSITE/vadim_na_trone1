package ru.vadim.world;

import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.ModBlocks;
import ru.vadim.net.HubPayloads;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Хаб-бар: все появляются тут при заходе, режим приключения, без урона и голода, выйти нельзя.
 * Старт: каждый жмёт кнопку «Готов» у двери; когда готовы все — отсчёт 3-2-1.
 * После отсчёта — катсцена с новостями по телеку (News).
 */
public final class Hub {
	private static final Set<UUID> ready = new HashSet<>();
	private static final java.util.Map<UUID, Integer> joining = new java.util.HashMap<>();
	private static int countdown = -1;
	private static Display.TextDisplay readyLabel;
	private static String lastLabel = "";
	private static final java.util.Map<UUID, Long> lastPress = new java.util.HashMap<>();

	private Hub() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Hub::onStarted);
		// перенос в бар — через несколько тиков после входа: межмировой телепорт прямо в момент JOIN
		// ломает учёт чанков игрока, и ванилла падает с NPE в DistanceManager при выходе
		ServerPlayConnectionEvents.JOIN.register((h, s, server) -> joining.put(h.getPlayer().getUUID(), 5));
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
			ready.remove(h.getPlayer().getUUID());
			joining.remove(h.getPlayer().getUUID());
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
			route(newP);
		});
		// в баре никого не бьют и никто не умирает
		// (заброшка пока заглушка — там тоже без урона)
		// в комнате 3 «Мини-мир» — урон только во время игры (Говнозавры, скелет, игроки)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amt) -> (e.level().dimension() != Places.HUB && e.level().dimension() != Places.ZAVOD && e.level().dimension() != Places.ROOMS)
				|| src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| (e.level().dimension() == Places.ROOMS && Room3.damageAllowed(e, src))
				|| Room4Power.in(e));   // бэкрумс: улыбаки и разлучник бьют
		// старые таблички из сохранения подгружаются уже после старта сервера — убираем дубли при загрузке
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (level.dimension() == Places.ZAVOD) {
				if (ZavodDoor.onItemLoad(e)) return;   // выброшенный лом — обратно на стол
				if (Zavod.isStaleCamera(e) || Zavod.isStale(e) || ZavodDoor.isStale(e)) { e.discard(); return; }
				ZavodDoor.onChainLoad(level, e);
				if (e.isRemoved()) return;
				else Zavod.track(e);   // буквы вывески из сохранения — для подсветки
				return;
			}
			if (level.dimension() == Places.ROOMS) {
				if (Room1.onItemLoad(e)) return;   // выброшенные ножницы/болторез
				if (Room2.onItemLoad(e)) return;   // выброшенная пластинка
				if (Room1.isStale(e) || Room2.isStale(e) || Room3.isStale(e)) e.discard();
				return;
			}
			if (level.dimension() != Places.HUB) return;
			if (e.getTags().contains("vadim_ready") && e != readyLabel) e.discard();
			else if (e.getTags().contains(Bar.TAG) && !e.getTags().contains(Bar.versionTag())) e.discard();
			else if (e instanceof net.minecraft.world.entity.decoration.painting.Painting) e.setInvulnerable(true);   // Тимофея не сломать
		});
		// картину в баре нельзя сбить даже в креативе
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, e, hit) ->
				level.dimension() == Places.HUB && e instanceof net.minecraft.world.entity.decoration.painting.Painting
						? InteractionResult.FAIL : InteractionResult.PASS);
		// двери, люки, кнопки и прочее в баре трогать нельзя — только нашу кнопку «Готов»
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			// во дворе завода трогать можно только дверь КПП (взлом двери цеха — этап 3б)
			if (level.dimension() == Places.ZAVOD && !player.isCreative())
				return Zavod.usable(hit.getBlockPos()) ? InteractionResult.PASS : InteractionResult.FAIL;
			// в комнатах ничего не трогаем руками (двери открывает игра, кровать — операционный стол)
			// (в комнате 3 можно сажать саженцы и растить их костной мукой — Room3.useBlock)
			if (level.dimension() == Places.ROOMS && !player.isCreative()) return Room3.useBlock(player, level, hand, hit);
			if (level.dimension() != Places.HUB || player.isCreative()) return InteractionResult.PASS;
			BlockState s = level.getBlockState(hit.getBlockPos());
			if (s.is(ModBlocks.READY)) return InteractionResult.PASS;
			String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
			if (id.contains("door")) {
				if (!level.isClientSide()) player.displayClientMessage(Component.literal("Закрыто. Бармен не выпустит, пока не сыграете.")
						.withStyle(ChatFormatting.GRAY), true);
				return InteractionResult.FAIL;
			}
			if (id.contains("trapdoor") || id.contains("gate") || id.contains("button") || id.contains("lever")
					|| id.contains("blinds") || id.contains("window") || id.contains("curtain") || id.contains("drawer")
					|| id.contains("cabinet") || id.contains("wardrobe")) return InteractionResult.FAIL;
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Hub::tick);
	}

	private static void onStarted(MinecraftServer server) {
		// мир, открытый по сети (LAN / Radmin): пускаем друзей с пиратских лаунчеров (Legacy Launcher и т.п.) без проверки лицензии
		if (!server.isDedicatedServer()) server.setUsesAuthentication(false);
		ready.clear();
		countdown = -1;
		ServerLevel hub = Places.hub(server);
		if (hub == null) {
			VadimNaTrone.LOG.error("Нет измерения хаба — датапак мода не загрузился");
			return;
		}
		// чанки бара и улицы перед ним всегда загружены — машины ездят, надписи живут
		for (int cx = -6; cx <= 5; cx++) for (int cz = -1; cz <= 1; cz++) hub.setChunkForced(cx, cz, true);
		Bar.ensure(hub, false);
		spawnReadyLabel(hub);
		ServerLevel zavod = Places.zavod(server);
		if (zavod != null) Zavod.ensure(zavod, false);
		ServerLevel rooms = Places.rooms(server);
		if (rooms != null) { Rooms.ensure(rooms); Room1.setup(rooms); Room2.setup(rooms); Room3.setup(rooms); }
	}

	public static void rebuildZavod(MinecraftServer server) {
		ServerLevel zavod = Places.zavod(server);
		if (zavod != null) Zavod.ensure(zavod, true);
	}

	public static void rebuild(MinecraftServer server) {
		ServerLevel hub = Places.hub(server);
		Bar.ensure(hub, true);
		spawnReadyLabel(hub);
	}

	private static void spawnReadyLabel(ServerLevel hub) {
		List<Entity> old = new ArrayList<>();
		for (Entity e : hub.getAllEntities()) if (e.getTags().contains("vadim_ready")) old.add(e);
		old.forEach(Entity::discard);
		Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(hub, EntitySpawnReason.TRIGGERED);
		if (d == null) return;
		BlockPos b = Bar.READY_BUTTON;
		d.setPos(b.getX() + 0.5, b.getY() + 0.85, b.getZ() + 0.75);
		d.setYRot(180f);
		d.addTag("vadim_ready");
		d.setTransformation(new Transformation(null, null, new Vector3f(0.45f, 0.45f, 0.45f), null));
		d.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		d.setBackgroundColor(0x66000000);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.setViewRange(0.5f);
		readyLabel = d;   // до добавления: ENTITY_LOAD удаляет всех «vadim_ready», кроме текущей
		hub.addFreshEntity(d);
		lastLabel = "";
	}

	/** В бар: без вещей, без урона, режим приключения (креатив не трогаем). */
	public static void sendToHub(ServerPlayer p) {
		ServerLevel hub = Places.hub(p.level().getServer());
		if (hub == null) return;
		if (!p.isCreative()) {
			p.getInventory().clearContent();
			p.setGameMode(GameType.ADVENTURE);
		}
		Vec3 s = Places.HUB_SPAWN;
		double ox = (p.getRandom().nextDouble() - 0.5) * 4, oz = (p.getRandom().nextDouble() - 0.5) * 1.5;
		if (p.isPassenger()) p.stopRiding();
		p.teleportTo(hub, s.x + ox, s.y, s.z + oz, Set.of(), Places.HUB_YAW, 0f, true);
		p.fallDistance = 0;
		p.setHealth(p.getMaxHealth());
	}

	/** Куда отправить игрока при входе/возрождении: бар, завод или (после газа) комнаты. */
	static void route(ServerPlayer p) {
		if (ZavodFinal.done(p.level().getServer())) Rooms.send(p);
		else if (News.goesToZavod(p)) News.sendToZavod(p);
		else sendToHub(p);
	}

	public static void pressReady(ServerPlayer p, BlockPos pos) {
		if (!Places.inHub(p) || countdown >= 0 || News.active()) return;
		long now = p.level().getGameTime();
		Long last = lastPress.put(p.getUUID(), now);
		if (last != null && now - last < 10) return;   // зажатая ПКМ не дёргает «готов/не готов»
		ServerLevel l = (ServerLevel) p.level();
		if (ready.add(p.getUUID())) {
			p.displayClientMessage(Component.literal("Ты готов. Ждём остальных…").withStyle(ChatFormatting.GREEN), true);
			l.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.8f, 0.7f);
		} else {
			ready.remove(p.getUUID());
			p.displayClientMessage(Component.literal("Передумал. Не готов.").withStyle(ChatFormatting.GRAY), true);
			l.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.8f, 0.6f);
		}
		lastLabel = "";
	}

	private static List<ServerPlayer> everyone(MinecraftServer server) {
		return server.getPlayerList().getPlayers().stream().filter(pl -> !pl.isSpectator()).toList();
	}

	private static void tick(MinecraftServer server) {
		if (!joining.isEmpty()) {
			var it = joining.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				if (e.getValue() > 1) { e.setValue(e.getValue() - 1); continue; }
				it.remove();
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p == null || p.hasDisconnected()) continue;
				route(p);   // катсцена уже прошла / шла без него — к заводу, после газа — в комнаты
			}
		}
		List<ServerPlayer> all = everyone(server);
		// сытость и здоровье в баре
		if (server.getTickCount() % 20 == 0) for (ServerPlayer p : all) if (p.level().dimension() == Places.ZAVOD || (p.level().dimension() == Places.ROOMS && !Room3.managesFood(p))) {
			p.getFoodData().setFoodLevel(20);
			p.getFoodData().setSaturation(5f);
		}
		if (server.getTickCount() % 20 == 0) for (ServerPlayer p : all) if (Places.inHub(p)) {
			p.getFoodData().setFoodLevel(20);
			p.getFoodData().setSaturation(5f);
			if (p.getY() < 50 && !p.isCreative()) sendToHub(p);   // вывалился из карты — обратно в бар
		}
		if (server.getTickCount() % 20 == 0) for (ServerPlayer p : all)
			if (p.level().dimension() == Places.ZAVOD && p.getY() < 40 && !p.isCreative()) News.sendToZavod(p);   // упал с площадки
		ready.removeIf(id -> all.stream().noneMatch(pl -> pl.getUUID().equals(id) && Places.inHub(pl)));
		int total = (int) all.stream().filter(Places::inHub).count();
		boolean allReady = total > 0 && ready.size() == total && !News.active();
		if (allReady && countdown < 0) countdown = 60;
		if (!allReady && countdown > 0) countdown = -1;
		if (countdown >= 0) {
			if (countdown % 20 == 0) {
				int n = countdown / 20;
				for (ServerPlayer p : all)
					if (Places.inHub(p) && ServerPlayNetworking.canSend(p, HubPayloads.Countdown.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.Countdown(n));
				ServerLevel hub = Places.hub(server);
				if (hub != null) hub.playSound(null, Bar.READY_BUTTON, n > 0 ? SoundEvents.NOTE_BLOCK_HAT.value() : SoundEvents.BELL_BLOCK,
						SoundSource.BLOCKS, 1f, n > 0 ? 0.6f : 0.8f);
			}
			if (countdown-- == 0) {
				ready.clear();
				countdown = -1;
				News.start(server);
			}
		}
		News.tick(server);
		Zavod.tick(server);
		ZavodDoor.tick(server);
		if (readyLabel == null || server.getTickCount() % 5 != 0) return;
		String s = countdown >= 0 ? "c" + (countdown / 20 + 1) : ready.size() + "/" + total;
		if (s.equals(lastLabel)) return;
		lastLabel = s;
		readyLabel.setText(countdown >= 0
				? Component.literal("НАЧИНАЕМ… " + (countdown / 20 + 1)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
				: Component.literal("ГОТОВЫ?\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal(ready.size() + " / " + total).withStyle(ready.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN)));
	}
}
