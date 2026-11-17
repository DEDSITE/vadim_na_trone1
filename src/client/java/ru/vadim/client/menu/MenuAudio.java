package ru.vadim.client.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import ru.vadim.sound.ModSounds;

/** Джаз в главном меню (тот же трек, что в баре). Играет, пока нет мира. */
public final class MenuAudio {
	private static Track music;

	private MenuAudio() {}

	public static void tick(Minecraft mc) {
		if (mc.level != null || mc.getOverlay() != null) return;
		if (music == null || !mc.getSoundManager().isActive(music)) {
			music = new Track();
			mc.getSoundManager().play(music);
		}
	}

	private static final class Track extends AbstractTickableSoundInstance {
		private float fade = 0f;

		Track() {
			super(ModSounds.MENU_JAZZ, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			volume = 0.001f;
			relative = true;
			attenuation = Attenuation.NONE;
		}

		@Override
		public void tick() {
			boolean on = Minecraft.getInstance().level == null;
			fade = Math.min(1f, fade + (on ? 0.01f : -0.05f));
			if (fade <= 0f && !on) { stop(); return; }
			volume = Math.max(0.001f, 0.8f * fade);   // на 20 % тише оригинала
		}

		@Override public boolean canStartSilent() { return true; }
	}
}
