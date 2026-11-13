package ru.vadim.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.vadim.VadimNaTrone;

/**
 * Где что стоит. Хаб — отдельное пустое измерение (вечная ночь), в нём кодом строится
 * квартал города, а в одном из домов — бар (см. Bar).
 */
public final class Places {
	public static final ResourceKey<Level> HUB = ResourceKey.create(Registries.DIMENSION, VadimNaTrone.id("hub"));

	/** Точка появления в баре: посередине зала, лицом к стойке (на север). */
	public static final Vec3 HUB_SPAWN = new Vec3(0.5, 64, -1.5);
	public static final float HUB_YAW = 180f;

	private Places() {}

	public static ServerLevel hub(MinecraftServer s) { return s.getLevel(HUB); }
	public static boolean inHub(Player p) { return p.level().dimension() == HUB; }
}
