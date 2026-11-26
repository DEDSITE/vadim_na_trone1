package ru.vadim.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import ru.vadim.VadimNaTrone;
import ru.vadim.util.Ids;

/**
 * Комната 4 «Бэкрумс» (этап 7): блоки Level 0. Текстуры 16×16 — из аддона Liminal Craft (карта Димы «Lost Inside The Backrooms»):
 * обои (точки / полоски, с плинтусом, с пятнами), мокрый и грязный ковёр, потолочная плитка, вентиляция, лампа (LIT — горит).
 * Всё неломаемое.
 */
public final class Room4Blocks {
	public static final Block WALL = deco("room4_wall", MapColor.SAND);
	public static final Block WALL_BASE = deco("room4_wall_base", MapColor.SAND);
	public static final Block WALL_S = deco("room4_wall_s", MapColor.SAND);
	public static final Block WALL_S_BASE = deco("room4_wall_s_base", MapColor.SAND);
	public static final Block[] WALL_STAIN = {deco("room4_wall_stain1", MapColor.SAND), deco("room4_wall_stain2", MapColor.SAND), deco("room4_wall_stain3", MapColor.SAND)};
	public static final Block CARPET = deco("room4_carpet", MapColor.TERRACOTTA_YELLOW, SoundType.WOOL);
	public static final Block[] CARPET_DIRTY = {deco("room4_carpet2", MapColor.TERRACOTTA_YELLOW, SoundType.WOOL), deco("room4_carpet3", MapColor.TERRACOTTA_YELLOW, SoundType.WOOL),
			deco("room4_carpet4", MapColor.TERRACOTTA_YELLOW, SoundType.WOOL), deco("room4_carpet5", MapColor.TERRACOTTA_YELLOW, SoundType.WOOL)};
	public static final Block CEILING = deco("room4_ceiling", MapColor.SAND);
	public static final Block CEILING2 = deco("room4_ceiling2", MapColor.SAND);
	public static final Block VENT = deco("room4_vent", MapColor.METAL);
	/** Лампа в потолке: LIT — горит (свет 15), иначе «мёртвая» (сломанная текстура). Мигание и питание секторов — Room4. */
	public static final Block LAMP = Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id("room4_lamp"),
			new Lamp(Ids.block("room4_lamp").mapColor(MapColor.SNOW).strength(-1f, 3600000f).sound(SoundType.GLASS)
					.lightLevel(s -> s.getValue(Lamp.LIT) ? 15 : 0)));

	private Room4Blocks() {}

	public static final class Lamp extends Block {
		public static final BooleanProperty LIT = BlockStateProperties.LIT;

		Lamp(Properties p) {
			super(p);
			registerDefaultState(defaultBlockState().setValue(LIT, true));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LIT); }
	}

	private static Block deco(String name, MapColor c) { return deco(name, c, SoundType.STONE); }

	private static Block deco(String name, MapColor c, SoundType s) {
		return Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id(name), new Block(Ids.block(name).mapColor(c).strength(-1f, 3600000f).sound(s)));
	}

	public static void init() {}
}
