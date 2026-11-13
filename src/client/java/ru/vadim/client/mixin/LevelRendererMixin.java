package ru.vadim.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vadim.client.render.FlashlightLights;

/** Матрицы вида и проекции кадра — для лучей фонарей в тумане (пост-эффект). */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void vadim$matrices(GraphicsResourceAllocator alloc, DeltaTracker delta, boolean outline, Camera camera,
	                                 Matrix4f view, Matrix4f proj, Matrix4f cullProj, GpuBufferSlice fog, Vector4f fogColor,
	                                 boolean sky, CallbackInfo ci) {
		FlashlightLights.matrices(view, proj);
	}
}
