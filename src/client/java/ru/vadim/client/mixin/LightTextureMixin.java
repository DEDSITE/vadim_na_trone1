package ru.vadim.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.vadim.client.ClientAtmo;
import ru.vadim.client.ClientZone;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	/** Во время игры игнорируем настройку «Яркость» — ночь должна быть тёмной у всех. */
	@ModifyExpressionValue(method = "updateLightTexture",
			at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
	private float vadim$gamma(float original) {
		return ClientAtmo.active() ? 0.0f : original;
	}

	/**
	 * Цвет света от ламп (блочный свет). В хабе — тёплый, как лампа накаливания ~3000 K.
	 * В 1.21.11 карта освещения считается шейдером (assets/minecraft/shaders/core/lightmap.fsh):
	 * отрицательный BrightnessFactor = «тёплый режим», яркость = -x - 1.
	 */
	@ModifyExpressionValue(method = "updateLightTexture",
			at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
	private float vadim$warmFlag(float brightness) {
		if (ClientZone.hub()) return -1f - brightness;
		return brightness;
	}
}
