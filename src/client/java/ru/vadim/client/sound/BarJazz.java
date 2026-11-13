package ru.vadim.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import ru.vadim.block.ModBlocks;
import ru.vadim.client.ClientZone;
import ru.vadim.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Джаз в баре: играет из каждой колонки (блок SPEAKER) — звук стоит на месте, а не ходит за игроком.
 * Все колонки запускаются в один тик, поэтому звучат синхронно. Тихо, фоном.
 */
public final class BarJazz {
	private static final float RANGE = 22f;
	private static final float LOUD = 0.32f;
	private static final List<Track> playing = new ArrayList<>();
	private static Set<BlockPos> current = new HashSet<>();
	private static int scanIn = 0;

	private BarJazz() {}

	public static void tick(Minecraft mc) {
		if (!ClientZone.hub() || mc.player == null || mc.level == null) {
			stopAll();
			return;
		}
		if (--scanIn > 0) return;
		scanIn = 20;
		Set<BlockPos> found = new HashSet<>();
		BlockPos c = mc.player.blockPosition();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = -20; x <= 20; x++)
			for (int y = -6; y <= 8; y++)
				for (int z = -20; z <= 20; z++) {
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (mc.level.getBlockState(m).is(ModBlocks.SPEAKER)) found.add(m.immutable());
				}
		boolean alive = !playing.isEmpty() && playing.stream().allMatch(t -> mc.getSoundManager().isActive(t));
		if (found.equals(current) && (alive || found.isEmpty())) return;
		stopAll();
		current = found;
		for (BlockPos p : found) {
			Track t = new Track(p);
			playing.add(t);
			mc.getSoundManager().play(t);
		}
	}

	public static void stopAll() {
		Minecraft mc = Minecraft.getInstance();
		for (Track t : playing) mc.getSoundManager().stop(t);
		playing.clear();
		current = new HashSet<>();
	}

	private static class Track extends AbstractTickableSoundInstance {
		private int age = 0;

		Track(BlockPos p) {
			super(ModSounds.BAR_JAZZ, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			attenuation = Attenuation.LINEAR;
			volume = RANGE / 16f;   // при старте задаёт слышимость (≈22 блока), дальше громкость убавляем
			x = p.getX() + 0.5;
			y = p.getY() + 0.5;
			z = p.getZ() + 0.5;
		}

		@Override
		public void tick() {
			age++;
			// плавно вступает за пару секунд
			volume = LOUD * Math.min(1f, age / 40f);
		}
	}
}
