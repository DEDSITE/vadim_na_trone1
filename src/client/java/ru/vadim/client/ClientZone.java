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

	/** Студия (комната 2): в измерении комнат и внутри зала студии. */
	public static boolean studio() {
		var mc = net.minecraft.client.Minecraft.getInstance();
		return rooms() && mc.player != null && ru.vadim.world.Room2.inStudio(mc.player.getX(), mc.player.getZ());
	}
}
