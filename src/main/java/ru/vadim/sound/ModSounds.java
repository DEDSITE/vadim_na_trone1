package ru.vadim.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import ru.vadim.VadimNaTrone;

public final class ModSounds {
	/** Джаз в баре: играет из колонок по углам (позиционно) и в главном меню. */
	public static final SoundEvent BAR_JAZZ = register("bar_jazz");
	public static final SoundEvent MENU_JAZZ = register("menu_jazz");

	private ModSounds() {}

	private static SoundEvent register(String name) {
		return Registry.register(BuiltInRegistries.SOUND_EVENT, VadimNaTrone.id(name), SoundEvent.createVariableRangeEvent(VadimNaTrone.id(name)));
	}

	public static void init() {}
}
