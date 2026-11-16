package ru.vadim.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera Overhaul держит одно общее состояние наклона (прошлый поворот камеры) и пересчитывает его при setup
 * ЛЮБОЙ камеры. Камеры наблюдения мода Camera Monitoring вызывают setup для своих Camera каждый кадр —
 * — у Camera Overhaul «прошлый поворот» скачет и экран игрока сильно качает (только на заводе, у мониторов).
 * Пока идёт setup не главной камеры (CctvBridge.otherCamera), Camera Overhaul ничего не считает.
 */
@Pseudo
@Mixin(targets = "mirsario.cameraoverhaul.CameraSystem", remap = false)
public abstract class CameraOverhaulGuardMixin {
	@Inject(method = "onCameraUpdate", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void vadim$skipUpdate(CallbackInfo ci) {
		if (ru.vadim.client.zavod.CctvBridge.otherCamera) ci.cancel();
	}

	@Inject(method = "modifyCameraTransform", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void vadim$skipTransform(CallbackInfo ci) {
		if (ru.vadim.client.zavod.CctvBridge.otherCamera) ci.cancel();
	}
}
