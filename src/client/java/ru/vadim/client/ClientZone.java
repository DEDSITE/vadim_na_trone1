package ru.vadim.client;

import net.minecraft.client.Minecraft;
import ru.vadim.world.Places;

/** Где сейчас игрок (по измерению). */
public final class ClientZone {
	private ClientZone() {}

	public static boolean hub() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.dimension() == Places.HUB;
	}

	public static boolean rooms() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.dimension() == Places.ROOMS;
	}

	public static boolean zavod() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.dimension() == Places.ZAVOD;
	}
}
