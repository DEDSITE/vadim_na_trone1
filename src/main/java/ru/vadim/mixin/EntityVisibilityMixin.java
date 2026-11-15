package ru.vadim.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.vadim.world.Room1;

/** Комната 1: цифры кода кровью отправляются только тому, кто в цепях и должен их видеть. */
@Mixin(Entity.class)
public abstract class EntityVisibilityMixin {
	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
	private void vadim$secret(ServerPlayer p, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self.getTags().contains(Room1.SECRET)) cir.setReturnValue(Room1.canSee(self, p));
	}
}
