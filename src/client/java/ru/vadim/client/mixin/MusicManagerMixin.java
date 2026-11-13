package ru.vadim.client.mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ванильной музыки нет нигде (меню, мир, креатив) — у мода своя атмосфера. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Shadow public abstract void stopPlaying();

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void vadim$noMusic(CallbackInfo ci) {
		stopPlaying();
		ci.cancel();
	}

	@Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
	private void vadim$noStart(net.minecraft.sounds.Music music, CallbackInfo ci) {
		ci.cancel();
	}
}
