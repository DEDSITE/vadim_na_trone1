package ru.vadim.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import ru.vadim.VadimNaTrone;
import ru.vadim.util.Ids;

/** Комната 2 «Студия» (этап 5б): стены студии по макету — орех понизу, латунная полоса, поролон ромбами выше. */
public final class StudioBlocks {
	public static final Block FOAM = reg("studio_foam", MapColor.COLOR_GRAY, SoundType.WOOL);
	public static final Block WALNUT = reg("studio_walnut", MapColor.COLOR_BROWN, SoundType.WOOD);
	public static final Block BRASS = reg("studio_brass", MapColor.COLOR_BROWN, SoundType.WOOD);

	private StudioBlocks() {}

	private static Block reg(String name, MapColor c, SoundType s) {
		return Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id(name),
				new Block(Ids.block(name).mapColor(c).strength(-1f, 3600000f).sound(s)));
	}

	public static void init() {}
}
