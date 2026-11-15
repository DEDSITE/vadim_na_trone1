package ru.vadim.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Комната 1: привязанный к стулу не выворачивает голову назад (ограничение после поворота мышью). */
@Mixin(Entity.class)
public abstract class Room1TurnMixin {
	@Inject(method = "turn", at = @At("TAIL"))
	private void vadim$clamp(double dx, double dy, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer p) ru.vadim.client.room.Room1Client.clamp(p);
	}
}
