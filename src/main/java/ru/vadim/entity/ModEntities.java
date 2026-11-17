package ru.vadim.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import ru.vadim.VadimNaTrone;

public final class ModEntities {
	public static final EntityType<BarmanEntity> BARMAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, VadimNaTrone.id("barman"),
			EntityType.Builder.<BarmanEntity>of(BarmanEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f).clientTrackingRange(16).noSummon().fireImmune()
					.build(ResourceKey.create(Registries.ENTITY_TYPE, VadimNaTrone.id("barman"))));

	public static final EntityType<CarEntity> CAR = Registry.register(BuiltInRegistries.ENTITY_TYPE, VadimNaTrone.id("car"),
			EntityType.Builder.<CarEntity>of(CarEntity::new, MobCategory.MISC)
					.sized(2.0f, 1.6f).clientTrackingRange(12).updateInterval(2).noSummon().fireImmune()
					.build(ResourceKey.create(Registries.ENTITY_TYPE, VadimNaTrone.id("car"))));

	/** Буханка — та же машина, но рисуется из блочных моделей Стрипухоеда (VanCarRenderer). */
	public static final EntityType<CarEntity> VAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, VadimNaTrone.id("van"),
			EntityType.Builder.<CarEntity>of(CarEntity::new, MobCategory.MISC)
					.sized(6f, 4f).clientTrackingRange(12).updateInterval(2).noSummon().fireImmune()
					.build(ResourceKey.create(Registries.ENTITY_TYPE, VadimNaTrone.id("van"))));

	/** Комната 3: мини-Говнозавр (как зомби) и скелет с ракетой (безобидный, одна кость). */
	public static final EntityType<GovnozavrEntity> GOVNOZAVR = Registry.register(BuiltInRegistries.ENTITY_TYPE, VadimNaTrone.id("govnozavr"),
			EntityType.Builder.<GovnozavrEntity>of(GovnozavrEntity::new, MobCategory.MONSTER)
					.sized(0.75f, 2.0f).clientTrackingRange(10).noSummon()
					.build(ResourceKey.create(Registries.ENTITY_TYPE, VadimNaTrone.id("govnozavr"))));
	public static final EntityType<RocketSkeleton> ROCKET_SKELETON = Registry.register(BuiltInRegistries.ENTITY_TYPE, VadimNaTrone.id("rocket_skeleton"),
			EntityType.Builder.<RocketSkeleton>of(RocketSkeleton::new, MobCategory.MISC)
					.sized(0.6f, 1.99f).clientTrackingRange(10).noSummon()
					.build(ResourceKey.create(Registries.ENTITY_TYPE, VadimNaTrone.id("rocket_skeleton"))));

	private ModEntities() {}

	public static void init() {
		FabricDefaultAttributeRegistry.register(BARMAN, BarmanEntity.attributes());
		FabricDefaultAttributeRegistry.register(GOVNOZAVR, GovnozavrEntity.attributes());
		FabricDefaultAttributeRegistry.register(ROCKET_SKELETON, net.minecraft.world.entity.monster.skeleton.AbstractSkeleton.createAttributes());
	}
}
