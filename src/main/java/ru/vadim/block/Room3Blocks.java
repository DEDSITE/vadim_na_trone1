package ru.vadim.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import ru.vadim.VadimNaTrone;
import ru.vadim.util.Ids;

/**
 * Комната 3 «Мини-мир» (этап 6): говно (блок из Стрипухоеда — кирка ломает его как руду, падает само себя)
 * и «небо» из блоков (стены и потолок комнаты: голубой фон, облака, солнце).
 */
public final class Room3Blocks {
	public static final Block GOVNO = Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id("govno"),
			new Block(Ids.block("govno").mapColor(MapColor.COLOR_BROWN).strength(3f, 3f).requiresCorrectToolForDrops().sound(SoundType.MUD)));
	public static final Item GOVNO_ITEM = Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id("govno"),
			new BlockItem(GOVNO, Ids.blockItem("govno")));
	public static final Block SKY = deco("room3_sky", MapColor.COLOR_LIGHT_BLUE);
	public static final Block CLOUD = deco("room3_cloud", MapColor.SNOW);
	public static final Block SUN = deco("room3_sun", MapColor.COLOR_YELLOW);

	private Room3Blocks() {}

	private static Block deco(String name, MapColor c) {
		return Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id(name),
				new Block(Ids.block(name).mapColor(c).strength(-1f, 3600000f).sound(SoundType.STONE)));
	}

	public static void init() {}
}
