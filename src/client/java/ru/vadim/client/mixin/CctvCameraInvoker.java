package ru.vadim.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Камера видеонаблюдения: ставим свою камеру в нужную точку и угол. */
@Mixin(Camera.class)
public interface CctvCameraInvoker {
	@Invoker("setPosition") void vadim$setPosition(Vec3 pos);
	@Invoker("setRotation") void vadim$setRotation(float yRot, float xRot);
}
