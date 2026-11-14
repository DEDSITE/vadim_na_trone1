package ru.vadim.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vadim.client.news.ClientNews;

/** Катсцена с новостями: камера летит к телеку, а не сидит в голове игрока. */
@Mixin(Camera.class)
public abstract class NewsCameraMixin {
	@Shadow protected abstract void setRotation(float yRot, float xRot);
	@Shadow protected abstract void setPosition(Vec3 pos);

	@Inject(method = "setup", at = @At("TAIL"))
	private void vadim$news(Level level, Entity entity, boolean detached, boolean mirror, float partial, CallbackInfo ci) {
		if (!ClientNews.active()) return;
		ClientNews.Cam c = ClientNews.camera(partial);
		setRotation(c.yaw(), c.pitch());
		setPosition(c.pos());
	}
}
