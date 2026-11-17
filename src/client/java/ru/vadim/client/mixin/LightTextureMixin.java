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
		if (ClientZone.zavod()) return 0.35f;
		if (ClientZone.studio()) return 0.45f;   // студия: тёплый свет абажуров
		if (ClientZone.room3()) return original;   // мини-мир: обычная яркость
		if (ClientZone.rooms()) return 0.2f;   // на заводе чуть светлее: ночь, но дорогу видно
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
		if (ClientZone.hub() || ClientZone.studio()) return -1f - brightness;
		return brightness;
	}

	/** Комната 3: светло везде без источников света — как «ночное зрение» без эффекта. */
	@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "updateLightTexture",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
	private boolean vadim$room3Bright(net.minecraft.client.player.LocalPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> eff,
			com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> op) {
		if (ClientZone.room3() && eff.equals(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) return true;
		return op.call(p, eff);
	}

	@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "updateLightTexture",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
	private float vadim$room3Scale(net.minecraft.world.entity.LivingEntity e, float pt, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Float> op) {
		if (ClientZone.room3() && !e.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) return 1f;
		return op.call(e, pt);
	}

	/** Катсцена с новостями: свет в баре приглушается (blockFactor = flicker + 1.5 умножаем на коэффициент). */
	@ModifyExpressionValue(method = "updateLightTexture",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LightTexture;blockLightRedFlicker:F"))
	private float vadim$newsDim(float flicker) {
		float k = ru.vadim.client.news.ClientNews.blockLightFactor();
		return k >= 1f ? flicker : (flicker + 1.5f) * k - 1.5f;
	}
}
