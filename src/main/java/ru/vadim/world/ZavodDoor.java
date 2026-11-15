package ru.vadim.world;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.ZavodBlocks;
import ru.vadim.item.ModItems;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Этап 3б — взлом боковой двери цеха (кооп-обучалка).
 *  1) на столе в КПП лежит лом (светится контуром) и журнал охранника (ПКМ — почитать);
 *  2) кто-то берёт лом (ПКМ) — теперь светится цепь на двери;
 *  3) с ломом в руке зажимаешь ПКМ по цепи, второй игрок одновременно тоже зажимает ПКМ по цепи — плёнка заполняется
 *     (~7 с, отпустили — откатывается). Одному не сломать; если на заводе вообще один игрок — можно одному (для теста);
 *  4) цепь лопается и падает, дверь открывается, лом пропадает. Сохраняется в мире (/vadim zavod reset — вернуть).
 *
 * Подсказки внизу экрана шлём каждому игроку пакетом ZavodHint (клиент рисует их в стиле «плёнка»).
 */
public final class ZavodDoor {
	/** Коды подсказок (совпадают с client/zavod/ZavodHud). */
	public static final int H_NONE = 0, H_FIND = 1, H_TAKE = 2, H_GO = 3, H_HELP = 4, H_NEED = 5, H_PRY = 6, H_DONE = 7;
	public static final String TAG = "vadim_zavod_3b";
	/** Сколько тиков вдвоём держать ПКМ, чтобы сбить цепь. */
	private static final int PRY_TICKS = 140;

	private static final Vec3 CROWBAR_AT = new Vec3(9.45, 64.97, -5.45);
	/** Журнал валяется на полу будки (пол — верх y=63, т.е. y 64.0). */
	private static final Vec3 JOURNAL_AT = new Vec3(8.5, 64.08, -6.6);   // на коврике
	private static final Vec3 CHAIN_AT = new Vec3(Zavod.SIDE_DOOR.getX() + 0.5, 64.15, Zavod.SHOP_Z1 + 1.15);

	private static final List<Entity> live = new ArrayList<>();
	private static Display.ItemDisplay crowbarShow;
	private static Interaction crowbarHit, journalHit, chainHit;
	private static boolean crowbarTaken = false;
	private static float progress = 0f;
	private static int doneShow = 0;
	/** Кто последний раз жал ПКМ по цепи (тик сервера) и был ли у него лом в руке. */
	private static final Map<UUID, Long> pressed = new HashMap<>();
	private static final Map<UUID, Boolean> withBar = new HashMap<>();
	private static final Map<UUID, Integer> inZavodTicks = new HashMap<>();
	private static final Map<UUID, Long> lastHint = new HashMap<>();

	private ZavodDoor() {}

	private static List<Entity> all(ServerLevel z) {
		List<Entity> out = new ArrayList<>();
		for (Entity e : z.getAllEntities()) out.add(e);
		return out;
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return entity == crowbarHit || entity == journalHit || entity == chainHit ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			if (entity == crowbarHit) { takeCrowbar(sp); return InteractionResult.SUCCESS; }
			if (entity == journalHit) {
				if (ServerPlayNetworking.canSend(sp, HubPayloads.ZavodJournal.TYPE)) ServerPlayNetworking.send(sp, new HubPayloads.ZavodJournal());
				level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1f);
				return InteractionResult.SUCCESS;
			}
			if (entity == chainHit) {
				long now = level.getGameTime();
				pressed.put(sp.getUUID(), now);
				withBar.put(sp.getUUID(), sp.getMainHandItem().is(ModItems.CROWBAR));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	// ======================================================================= состояние в мире

	public static boolean broken(ServerLevel z) { return z.getDataStorage().computeIfAbsent(State.TYPE).broken; }

	private static void setBroken(ServerLevel z, boolean b) {
		State st = z.getDataStorage().computeIfAbsent(State.TYPE);
		st.broken = b;
		st.setDirty();
	}

	/** После постройки/старта: поставить лом и журнал, а если дверь уже взломана — открыть её и убрать цепь. */
	static void setup(ServerLevel z) {
		for (Entity e : all(z)) if (e.getTags().contains(TAG)) e.discard();
		live.clear();
		crowbarShow = null;
		crowbarHit = journalHit = chainHit = null;
		crowbarTaken = false;
		progress = 0f;
		pressed.clear();
		// журнал на столе: можно читать всегда
		Display.ItemDisplay j = itemShow(z, new ItemStack(Items.WRITABLE_BOOK), JOURNAL_AT,
				new Quaternionf().rotateY(-0.25f).rotateX((float) Math.toRadians(90)), 0.45f);
		journalHit = hitbox(z, JOURNAL_AT.add(0, -0.03, 0), 0.6f, 0.2f);
		if (j != null) j.setBrightnessOverride(new net.minecraft.util.Brightness(9, 0));
		if (broken(z)) {
			openDoor(z, false);
			for (Entity e : all(z))
				if (e.getTags().contains("vadim_zavod_chain")) e.discard();
			cutChainOnGround(z);
			return;
		}
		// лом на столе, светится
		crowbarShow = itemShow(z, new ItemStack(ModItems.CROWBAR), CROWBAR_AT,
				new Quaternionf().rotateY(0.5f).rotateX((float) Math.toRadians(90)), 0.7f);
		if (crowbarShow != null) {
			crowbarShow.setGlowingTag(true);
			crowbarShow.setGlowColorOverride(0xE8C77A);
			crowbarShow.setBrightnessOverride(new net.minecraft.util.Brightness(12, 0));
		}
		crowbarHit = hitbox(z, CROWBAR_AT.add(0, -0.12, 0), 0.7f, 0.3f);
		chainHit = hitbox(z, CHAIN_AT, 1.4f, 1.5f);
		glowChain(z, false);
	}

	private static Display.ItemDisplay itemShow(ServerLevel z, ItemStack st, Vec3 at, Quaternionf rot, float scale) {
		Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(z, EntitySpawnReason.TRIGGERED);
		if (d == null) return null;
		d.setItemStack(st);
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setPos(at.x, at.y, at.z);
		d.setTransformation(new Transformation(null, rot, new Vector3f(scale, scale, scale), null));
		d.setViewRange(1f);
		d.addTag(TAG);
		live.add(d);
		z.addFreshEntity(d);
		return d;
	}

	private static Interaction hitbox(ServerLevel z, Vec3 at, float w, float h) {
		Interaction i = EntityType.INTERACTION.create(z, EntitySpawnReason.TRIGGERED);
		if (i == null) return null;
		i.setPos(at.x, at.y, at.z);
		i.setWidth(w);
		i.setHeight(h);
		i.setResponse(true);
		i.addTag(TAG);
		live.add(i);
		z.addFreshEntity(i);
		return i;
	}

	/** Цепь догрузилась с чанком: если дверь уже взломана — убрать, если лом уже взят — подсветить. */
	public static void onChainLoad(ServerLevel z, Entity e) {
		if (!e.getTags().contains("vadim_zavod_chain")) return;
		if (broken(z)) { e.discard(); return; }
		if (crowbarTaken && e instanceof Display d) { d.setGlowingTag(true); d.setGlowColorOverride(0xE8C77A); }
	}

	/** Старые сущности 3б из сохранения — убрать (живые пересоздаются при старте). */
	public static boolean isStale(Entity e) {
		return e.getTags().contains(TAG) && !live.contains(e);
	}

	private static void glowChain(ServerLevel z, boolean on) {
		for (Entity e : z.getAllEntities())
			if (e.getTags().contains("vadim_zavod_chain") && e instanceof Display d) {
				d.setGlowingTag(on);
				d.setGlowColorOverride(0xE8C77A);
			}
	}

	// ======================================================================= лом

	private static void takeCrowbar(ServerPlayer p) {
		ServerLevel z = Places.zavod(p.level().getServer());
		if (z == null || crowbarTaken || broken(z)) return;
		if (p.getInventory().contains(st -> st.is(ModItems.CROWBAR))) return;
		crowbarTaken = true;
		ItemStack bar = new ItemStack(ModItems.CROWBAR);
		if (p.getMainHandItem().isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, bar);
		else p.getInventory().add(bar);
		if (crowbarShow != null) { crowbarShow.discard(); live.remove(crowbarShow); crowbarShow = null; }
		if (crowbarHit != null) { crowbarHit.discard(); live.remove(crowbarHit); crowbarHit = null; }
		z.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1f, 0.8f);
		glowChain(z, true);
	}

	/** Лом выбросили (Q) или он выпал — не даём потерять: возвращаем на стол. */
	public static boolean onItemLoad(Entity e) {
		if (!(e instanceof ItemEntity it) || !it.getItem().is(ModItems.CROWBAR)) return false;
		ServerLevel z = (ServerLevel) e.level();
		it.discard();
		if (broken(z)) return true;
		z.getServer().execute(() -> returnCrowbar(z));
		return true;
	}

	private static void returnCrowbar(ServerLevel z) {
		for (ServerPlayer p : z.getServer().getPlayerList().getPlayers())
			if (p.getInventory().contains(st -> st.is(ModItems.CROWBAR))) return;   // у кого-то ещё есть
		boolean wasTaken = crowbarTaken;
		crowbarTaken = false;
		if (crowbarShow == null) {
			crowbarShow = itemShow(z, new ItemStack(ModItems.CROWBAR), CROWBAR_AT,
					new Quaternionf().rotateY(0.5f).rotateX((float) Math.toRadians(90)), 0.7f);
			if (crowbarShow != null) {
				crowbarShow.setGlowingTag(true);
				crowbarShow.setGlowColorOverride(0xE8C77A);
				crowbarShow.setBrightnessOverride(new net.minecraft.util.Brightness(12, 0));
			}
			crowbarHit = hitbox(z, CROWBAR_AT.add(0, -0.12, 0), 0.7f, 0.3f);
		}
		if (wasTaken) glowChain(z, false);
	}

	// ======================================================================= такт

	public static void tick(MinecraftServer server) {
		ServerLevel z = Places.zavod(server);
		if (z == null) return;
		long now = z.getGameTime();
		List<ServerPlayer> here = new ArrayList<>();
		for (ServerPlayer p : z.players()) if (!p.isSpectator()) here.add(p);
		inZavodTicks.keySet().removeIf(id -> here.stream().noneMatch(p -> p.getUUID().equals(id)));
		for (ServerPlayer p : here) inZavodTicks.merge(p.getUUID(), 1, Integer::sum);
		boolean isBroken = broken(z);

		// кто сейчас держит ПКМ по цепи (клиент повторяет нажатие раз в 4 тика)
		int holding = 0, bars = 0;
		List<ServerPlayer> pryers = new ArrayList<>();
		for (ServerPlayer p : here) {
			Long t = pressed.get(p.getUUID());
			if (t == null || now - t > 6 || p.distanceToSqr(CHAIN_AT) > 16) continue;
			holding++;
			pryers.add(p);
			if (Boolean.TRUE.equals(withBar.get(p.getUUID())) && p.getMainHandItem().is(ModItems.CROWBAR)) bars++;
		}
		int need = Math.min(2, here.size());
		boolean active = !isBroken && bars >= 1 && holding >= need;
		if (!isBroken) {
			if (active) {
				progress = Math.min(1f, progress + 1f / PRY_TICKS);
				if (now % 8 == 0) {
					z.playSound(null, CHAIN_AT.x, CHAIN_AT.y + 0.8, CHAIN_AT.z, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 1f, 0.6f + z.random.nextFloat() * 0.4f);
					for (ServerPlayer p : pryers) p.swing(InteractionHand.MAIN_HAND, true);
				}
				if (now % 4 == 0) z.sendParticles(ParticleTypes.CRIT, CHAIN_AT.x, CHAIN_AT.y + 0.85, CHAIN_AT.z, 3, 0.25, 0.1, 0.1, 0.1);
				if (progress >= 1f) breakChain(z, here);
			} else {
				progress = Math.max(0f, progress - 1f / 60f);
			}
		}
		if (doneShow > 0) doneShow--;

		// подсказки
		for (ServerPlayer p : here) {
			int hint = H_NONE;
			float prog = 0f;
			boolean nearDoor = p.distanceToSqr(CHAIN_AT) < 12 * 12;
			if (isBroken) {
				if (doneShow > 0 && nearDoor) hint = H_DONE;
			} else if (progress > 0f && nearDoor && active) {
				hint = H_PRY;
				prog = progress;
			} else if (!crowbarTaken) {
				if (p.distanceToSqr(CROWBAR_AT) < 3.2 * 3.2) hint = H_TAKE;
				else if (inZavodTicks.getOrDefault(p.getUUID(), 0) > 120) hint = H_FIND;
			} else {
				Long t = pressed.get(p.getUUID());
				boolean me = t != null && now - t <= 6;
				if (me && holding < need) { hint = H_NEED; prog = progress; }
				else if (progress > 0f && nearDoor) { hint = H_PRY; prog = progress; }
				else hint = p.getInventory().contains(st -> st.is(ModItems.CROWBAR)) ? H_GO : H_HELP;
			}
			sendHint(p, hint, prog);
		}
	}

	private static void sendHint(ServerPlayer p, int hint, float prog) {
		long key = hint * 1000L + Math.round(prog * 48);
		Long last = lastHint.get(p.getUUID());
		if (last != null && last == key && p.level().getGameTime() % 40 != 0) return;
		lastHint.put(p.getUUID(), key);
		if (ServerPlayNetworking.canSend(p, HubPayloads.ZavodHint.TYPE)) ServerPlayNetworking.send(p, new HubPayloads.ZavodHint(hint, prog));
	}

	// ======================================================================= цепь лопнула

	private static void breakChain(ServerLevel z, List<ServerPlayer> here) {
		setBroken(z, true);
		progress = 0f;
		doneShow = 80;
		z.playSound(null, CHAIN_AT.x, CHAIN_AT.y + 0.85, CHAIN_AT.z, ModSounds.ZAVOD_CHAIN_SNAP, SoundSource.BLOCKS, 1f, 1f);
		z.playSound(null, CHAIN_AT.x, CHAIN_AT.y + 0.85, CHAIN_AT.z, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8f, 0.8f);
		z.sendParticles(ParticleTypes.CRIT, CHAIN_AT.x, CHAIN_AT.y + 0.85, CHAIN_AT.z, 20, 0.4, 0.2, 0.2, 0.3);
		// звенья пропадают, замок с куском цепи падает на землю
		for (Entity e : all(z))
			if (e.getTags().contains("vadim_zavod_chain")) e.discard();
		cutChainOnGround(z);
		if (chainHit != null) { chainHit.discard(); live.remove(chainHit); chainHit = null; }
		// лом своё отработал
		for (ServerPlayer p : z.getServer().getPlayerList().getPlayers()) {
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ModItems.CROWBAR)) inv.setItem(i, ItemStack.EMPTY);
		}
		crowbarTaken = false;
		// дверь со скрипом открывается (чуть позже, когда цепь упала)
		z.getServer().execute(() -> openDoor(z, true));
		VadimNaTrone.LOG.info("Завод: цепь сбита, дверь цеха открыта ({} игр.)", here.size());
	}

	private static void cutChainOnGround(ServerLevel z) {
		for (Entity e : live) if (e.getTags().contains("vadim_zavod_chain_cut") && !e.isRemoved()) return;
		Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(z, EntitySpawnReason.TRIGGERED);
		if (d == null) return;
		d.setBlockState(ZavodBlocks.CHAIN_CUT.defaultBlockState().setValue(ZavodBlocks.Deco.FACING, Direction.SOUTH));
		d.setPos(Zavod.SIDE_DOOR.getX() + 0.1, 64.0, Zavod.SHOP_Z1 + 1.05);
		d.setTransformation(new Transformation(null, new Quaternionf().rotateY(0.35f), null, null));
		d.setViewRange(1f);
		d.addTag(TAG);   // как и лом с журналом: пересоздаётся при каждом старте, старая копия из сохранения удаляется
		d.addTag("vadim_zavod_chain_cut");
		live.add(d);
		z.addFreshEntity(d);
	}

	private static void openDoor(ServerLevel z, boolean sound) {
		BlockPos lo = Zavod.SIDE_DOOR, hi = lo.above();
		BlockState a = z.getBlockState(lo), b = z.getBlockState(hi);
		if (a.hasProperty(DoorBlock.OPEN)) z.setBlock(lo, a.setValue(DoorBlock.OPEN, true), Block.UPDATE_CLIENTS);
		if (b.hasProperty(DoorBlock.OPEN)) z.setBlock(hi, b.setValue(DoorBlock.OPEN, true), Block.UPDATE_CLIENTS);
		if (sound) z.playSound(null, lo, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 0.7f);
	}

	/** /vadim zavod break — сбить цепь сразу (отладка). */
	public static void forceBreak(MinecraftServer server) {
		ServerLevel z = Places.zavod(server);
		if (z == null || broken(z)) return;
		breakChain(z, new ArrayList<>(z.players()));
	}

	/** /vadim zavod reset — цепь обратно, лом на стол, дверь закрыта. */
	public static void reset(MinecraftServer server) {
		ServerLevel z = Places.zavod(server);
		if (z == null) return;
		setBroken(z, false);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ModItems.CROWBAR)) inv.setItem(i, ItemStack.EMPTY);
		}
		Zavod.ensure(z, true);
	}

	// ======================================================================= сохранение

	public static final class State extends SavedData {
		static final Codec<State> CODEC = Codec.BOOL.xmap(State::new, s -> s.broken);
		static final SavedDataType<State> TYPE = new SavedDataType<>("vadim_zavod_door", State::new, CODEC, null);
		boolean broken;
		State() { this(false); }
		State(boolean b) { broken = b; }
	}
}
