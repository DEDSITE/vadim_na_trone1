package ru.vadim.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.vadim.world.Room1;

/** Комната 1: цифры кода кровью — только тому, кто в цепях; комната 2: плакат «звук → цвет» — только «Глазам». */
@Mixin(Entity.class)
public abstract class EntityVisibilityMixin {
	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
	private void vadim$secret(ServerPlayer p, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self.getTags().contains(Room1.SECRET)) cir.setReturnValue(Room1.canSee(self, p));
		else if (self.getTags().contains(ru.vadim.world.Room2.SECRET)) cir.setReturnValue(ru.vadim.world.Room2.canSee(self, p));
	}
}
