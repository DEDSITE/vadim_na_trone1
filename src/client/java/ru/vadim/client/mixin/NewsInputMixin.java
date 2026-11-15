package ru.vadim.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vadim.client.news.ClientNews;

/** Катсцена: игрок стоит — WASD, прыжок и шифт не работают. */
@Mixin(KeyboardInput.class)
public abstract class NewsInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void vadim$freeze(CallbackInfo ci) {
		if (!ClientNews.active() && !ru.vadim.client.zavod.ZavodFinalClient.locked() && !ru.vadim.client.room.Room1Client.frozen()) return;
		this.keyPresses = Input.EMPTY;
		this.moveVector = Vec2.ZERO;
	}
}
