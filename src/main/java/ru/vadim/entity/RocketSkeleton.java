package ru.vadim.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import ru.vadim.VadimNaTrone;

/**
 * Скелет комнаты 3: стоит в углу, никого не трогает, в руке вместо лука — ракета Вадима из комнаты 1.
 * С каждого — ровно одна кость. Новый появляется сразу (Room3).
 */
public class RocketSkeleton extends Skeleton {
	public RocketSkeleton(EntityType<? extends Skeleton> type, Level level) {
		super(type, level);
		setNoAi(true);
		setPersistenceRequired();
		xpReward = 0;
		ItemStack rocket = new ItemStack(Items.PAPER);
		rocket.set(DataComponents.ITEM_MODEL, VadimNaTrone.id("room3_rocket_held"));
		setItemSlot(EquipmentSlot.MAINHAND, rocket);
		setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	@Override protected void registerGoals() {}
	@Override public void reassessWeaponGoal() {}
	@Override public boolean removeWhenFarAway(double d) { return false; }

	@Override
	protected void dropAllDeathLoot(ServerLevel level, DamageSource src) {
		spawnAtLocation(level, new ItemStack(Items.BONE));
	}
}
