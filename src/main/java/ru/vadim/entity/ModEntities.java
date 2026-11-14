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

	private ModEntities() {}

	public static void init() {
		FabricDefaultAttributeRegistry.register(BARMAN, BarmanEntity.attributes());
	}
}
