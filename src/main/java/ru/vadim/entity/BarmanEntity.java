package ru.vadim.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Бармен Тимофей: стоит за стойкой, следит головой за ближайшим гостем. Неуязвим, не толкается.
 * Диалог открывается на клиенте по ПКМ (BarmanScreen).
 */
public class BarmanEntity extends Mob {
	public BarmanEntity(EntityType<? extends Mob> type, Level level) {
		super(type, level);
		setNoAi(true);
		setPersistenceRequired();
		setSilent(true);
	}

	public static AttributeSupplier.Builder attributes() { return Mob.createMobAttributes(); }

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) return;
		Player p = level().getNearestPlayer(this, 7);
		float baseYaw = getYRot();
		if (p != null) {
			double dx = p.getX() - getX(), dz = p.getZ() - getZ(), dy = p.getEyeY() - getEyeY();
			float want = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
			float rel = net.minecraft.util.Mth.wrapDegrees(want - baseYaw);
			rel = net.minecraft.util.Mth.clamp(rel, -70, 70);
			setYHeadRot(baseYaw + rel);
			setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
		} else {
			setYHeadRot(baseYaw);
			setXRot(0);
		}
	}

	@Override public boolean hurtServer(ServerLevel l, DamageSource s, float a) { return false; }
	@Override public boolean isPushable() { return false; }
	@Override protected void doPush(net.minecraft.world.entity.Entity e) {}
	@Override public boolean shouldBeSaved() { return false; }
	@Override public boolean removeWhenFarAway(double d) { return false; }
}
