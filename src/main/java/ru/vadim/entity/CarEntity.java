package ru.vadim.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Машина за окном бара: едет по прямой с постоянной скоростью и исчезает в конце улицы.
 * Двигает её сервер (Traffic), клиент только рисует модель и свет фар (CarLights).
 */
public class CarEntity extends Entity implements software.bernie.geckolib.animatable.GeoEntity {
	public enum Kind { SEDAN, TAXI, TRUCK, VAN, OPEL }

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> PAINT = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
	/** Откуда и когда поехала: позицию и сервер, и клиент считают сами по времени мира — без рывков в сетевой игре. */
	private static final EntityDataAccessor<Float> START_X = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Long> START_T = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.LONG);
	private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache =
			software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
	private static final software.bernie.geckolib.animation.RawAnimation SPIN =
			software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("spin");

	@Override
	public void registerControllers(software.bernie.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar c) {
		c.add(new software.bernie.geckolib.animation.AnimationController<>("main", 0,
				st -> kind() == Kind.OPEL ? st.setAndContinue(SPIN) : software.bernie.geckolib.animation.object.PlayState.STOP));
	}

	@Override public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

	/** Докуда ехать по X (потом исчезнуть). */
	public double endX;

	public CarEntity(EntityType<? extends CarEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		b.define(KIND, 0);
		b.define(SPEED, 0.5f);
		b.define(PAINT, 0);
		b.define(START_X, 0f);
		b.define(START_T, 0L);
	}

	public Kind kind() { return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)]; }
	public void setKind(Kind k) { entityData.set(KIND, k.ordinal()); }
	public int paint() { return entityData.get(PAINT); }
	public void setPaint(int p) { entityData.set(PAINT, p); }
	/** Скорость по X, блоков за тик (знак = направление). */
	public float speed() { return entityData.get(SPEED); }
	public void setSpeed(float s) {
		entityData.set(SPEED, s);
		setYRot(s >= 0 ? -90f : 90f);
		yRotO = getYRot();
	}

	/** Запомнить старт (вызывать на сервере после setPos). */
	public void markStart() {
		entityData.set(START_X, (float) getX());
		entityData.set(START_T, level().getGameTime());
	}

	@Override
	public void tick() {
		super.tick();
		float s = speed();
		setYRot(s >= 0 ? -90f : 90f);
		Vec3 p = position();
		long t0 = entityData.get(START_T);
		double x = t0 > 0 ? entityData.get(START_X) + s * (level().getGameTime() - t0) : p.x + s;
		setPos(x, p.y, p.z);
		setDeltaMovement(s, 0, 0);
		if (!level().isClientSide() && ((s > 0 && getX() > endX) || (s < 0 && getX() < endX) || tickCount > 20 * 90)) discard();
	}

	@Override public boolean hurtServer(ServerLevel l, DamageSource src, float a) { return false; }
	@Override public boolean isPickable() { return false; }
	@Override public boolean isPushable() { return false; }
	@Override public boolean shouldBeSaved() { return false; }
	@Override protected void readAdditionalSaveData(ValueInput in) {}
	@Override protected void addAdditionalSaveData(ValueOutput out) {}
	@Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160 * 160; }
}
