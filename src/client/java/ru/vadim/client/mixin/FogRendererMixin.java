package ru.vadim.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.vadim.client.ClientAtmo;
import ru.vadim.client.render.FlashlightLights;

import java.nio.ByteBuffer;

/**
 * Туман 1.21.11 (FogRenderer.setupFog пишет UBO «Fog»):
 *  - свой густой туман: цвет и дистанция; небо целиком тонет в тумане;
 *  - буфер тумана удлинён — в хвосте едут фонарики для шейдеров мира (include/fog.glsl).
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	/** Буферы тумана длиннее ванильных: под ними наши фонари. */
	@ModifyExpressionValue(method = {"<init>", "getBuffer"}, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;FOG_UBO_SIZE:I"))
	private int vadim$size(int vanilla) {
		return FlashlightLights.fogSize();
	}

	/** Буфер «без тумана» (интерфейс): фонарей там нет. */
	@WrapOperation(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
	private void vadim$emptyFog(FogRenderer self, ByteBuffer buf, int pos, Vector4f color, float envStart, float envEnd,
	                                 float rdStart, float rdEnd, float skyEnd, float cloudEnd, Operation<Void> original) {
		original.call(self, buf, pos, color, envStart, envEnd, rdStart, rdEnd, skyEnd, cloudEnd);
		FlashlightLights.zero(buf, pos);
	}

	@WrapOperation(method = "setupFog", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
	private void vadim$fog(FogRenderer self, ByteBuffer buf, int pos, Vector4f color, float envStart, float envEnd,
	                            float rdStart, float rdEnd, float skyEnd, float cloudEnd, Operation<Void> original,
	                            @Local(argsOnly = true) Camera camera, @Local(argsOnly = true) DeltaTracker delta) {
		if (ClientAtmo.fogApplies(camera)) {
			float[] c = ClientAtmo.fogColor();
			color.set(c[0], c[1], c[2], color.w());
			envStart = ClientAtmo.fogStart();
			envEnd = Math.min(ClientAtmo.fogEnd(), rdEnd);
			skyEnd = 0f;
			cloudEnd = 0f;
		}
		original.call(self, buf, pos, color, envStart, envEnd, rdStart, rdEnd, skyEnd, cloudEnd);
		FlashlightLights.update(camera, delta.getGameTimeDeltaPartialTick(false));
		FlashlightLights.write(buf, pos, false);
		FlashlightLights.rememberFog(color, envStart, envEnd, rdStart, rdEnd, skyEnd, cloudEnd);
	}

	@ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
	private Vector4f vadim$clearColor(Vector4f color, @Local(argsOnly = true) Camera camera) {
		if (ClientAtmo.fogApplies(camera)) {
			float[] c = ClientAtmo.fogColor();
			color.set(c[0], c[1], c[2], 0f);
		}
		return color;
	}
}
