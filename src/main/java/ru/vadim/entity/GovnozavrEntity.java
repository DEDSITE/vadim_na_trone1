package ru.vadim.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Мини-Говнозавр (комната 3): босс Тимофей из Говномесов, уменьшенный до 2 блоков. Хп, скорость и урон — как у зомби.
 * Охотится только на тех, у кого в инвентаре кирка или топор (меч — не цель). Ничего не роняет.
 */
public class GovnozavrEntity extends PathfinderMob {
	public GovnozavrEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder attributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.23)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.ARMOR, 2.0);
	}

	/** Цель: живой игрок не в креативе/наблюдателе, у которого есть кирка или топор. */
	public static boolean isPrey(LivingEntity e) {
		if (!(e instanceof Player p) || p.isSpectator() || p.isCreative() || !p.isAlive()) return false;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && (s.is(ItemTags.PICKAXES) || s.is(ItemTags.AXES))) return true;
		}
		return false;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 5, false, false, (e, l) -> isPrey(e)));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		// инструмент отдали другому — бросаем цель
		LivingEntity t = getTarget();
		if (t != null && !isPrey(t)) setTarget(null);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// в мирном урон от мобов обнуляется — здесь бьём «как зомби на нормальной» всегда
		if (level.getDifficulty() == Difficulty.PEACEFUL && target instanceof LivingEntity le) {
			swing(InteractionHand.MAIN_HAND);
			return le.hurtServer(level, damageSources().generic(), 3f);
		}
		return super.doHurtTarget(level, target);
	}

	@Override public boolean removeWhenFarAway(double d) { return false; }
	@Override protected SoundEvent getAmbientSound() { return SoundEvents.ZOMBIE_AMBIENT; }
	@Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ZOMBIE_HURT; }
	@Override protected SoundEvent getDeathSound() { return SoundEvents.ZOMBIE_DEATH; }
	@Override public float getVoicePitch() { return 0.6f + random.nextFloat() * 0.15f; }
}
