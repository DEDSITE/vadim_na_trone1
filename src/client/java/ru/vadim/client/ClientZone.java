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

	/** Комната 3 «Мини-мир» (светлый мир 40×40, x 41..80). */
	public static boolean room3() {
		var mc = net.minecraft.client.Minecraft.getInstance();
		return rooms() && mc.player != null && ru.vadim.world.Room3.inRoom3(mc.player.getX(), mc.player.getZ());
	}

	/** Комната 4 «Бэкрумс» (лабиринт Level 0, x 200..350). */
	public static boolean room4() {
		var mc = net.minecraft.client.Minecraft.getInstance();
		return rooms() && mc.player != null && ru.vadim.world.Room4.inRoom4(mc.player.getX(), mc.player.getZ());
	}

	/** Студия (комната 2): в измерении комнат и внутри зала студии. */
	public static boolean studio() {
		var mc = net.minecraft.client.Minecraft.getInstance();
		return rooms() && mc.player != null && ru.vadim.world.Room2.inStudio(mc.player.getX(), mc.player.getZ());
	}
}
