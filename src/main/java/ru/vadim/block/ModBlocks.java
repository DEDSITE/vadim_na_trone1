package ru.vadim.block;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.vadim.VadimNaTrone;
import ru.vadim.util.Ids;
import ru.vadim.world.Hub;

/**
 * Свои блоки хаба: гирлянда (из Стрипухоеда), стенд записок, кнопка «Готов», колонка,
 * телевизор и витрина с экспонатом. Ломать нельзя никому (сила -1), это декор карты.
 */
public final class ModBlocks {
	private static BlockBehaviour.Properties props(String name, MapColor c, SoundType s) {
		return Ids.block(name).mapColor(c).strength(-1f, 3600000f).sound(s).noOcclusion().pushReaction(PushReaction.BLOCK);
	}

	/** Объявлено до блоков: конструктор NotesBoard уже читает это свойство. */
	public static final IntegerProperty NOTES_PART = IntegerProperty.create("part", 0, 11);

	public static final Block GARLAND = reg("garland", new Garland(props("garland", MapColor.METAL, SoundType.LANTERN).noCollision()
			.lightLevel(s -> s.getValue(Garland.LIT) ? 14 : 0)));
	public static final Block NOTES = reg("notes_board", new NotesBoard(props("notes_board", MapColor.WOOD, SoundType.WOOD)));
	public static final Block READY = reg("ready_button", new ReadyButton(props("ready_button", MapColor.COLOR_RED, SoundType.METAL)
			.lightLevel(s -> 6)));
	public static final Block SPEAKER = reg("speaker", new Facing(props("speaker", MapColor.COLOR_BLACK, SoundType.WOOD),
			Block.box(3, 1, 0, 13, 15, 10)));
	public static final Block TV = reg("tv", new Tv(props("tv", MapColor.COLOR_BLACK, SoundType.METAL)));
	public static final Block SHOWCASE = reg("showcase", new Showcase(props("showcase", MapColor.WOOD, SoundType.GLASS)
			.lightLevel(s -> 9)));

	/** Буханка «Влог конченных» из Стрипухоеда: части-модели, в мире не ставится — их рисует VanCarRenderer. */
	public static final Block VAN = Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id("van"),
			new VanPart(Ids.block("van").noOcclusion().noCollision().strength(-1f, 3600000f)));

	public static final BlockEntityType<NotesEntity> NOTES_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			VadimNaTrone.id("notes_board"), FabricBlockEntityTypeBuilder.create(NotesEntity::new, NOTES).build());
	public static final BlockEntityType<TvEntity> TV_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			VadimNaTrone.id("tv"), FabricBlockEntityTypeBuilder.create(TvEntity::new, TV).build());
	public static final BlockEntityType<ShowcaseEntity> SHOWCASE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			VadimNaTrone.id("showcase"), FabricBlockEntityTypeBuilder.create(ShowcaseEntity::new, SHOWCASE).build());

	private ModBlocks() {}

	private static Block reg(String name, Block b) {
		Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id(name), b);
		Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id(name), new BlockItem(b, Ids.blockItem(name)));
		return b;
	}

	public static void init() {}

	/** Коробка, повёрнутая из «лицом на юг» в нужную сторону. */
	public static VoxelShape rotated(VoxelShape southBox, Direction to) {
		VoxelShape[] out = {Shapes.empty()};
		southBox.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
			double ax0, az0, ax1, az1;
			switch (to) {
				case NORTH -> { ax0 = 1 - x1; az0 = 1 - z1; ax1 = 1 - x0; az1 = 1 - z0; }
				case EAST -> { ax0 = z0; az0 = 1 - x1; ax1 = z1; az1 = 1 - x0; }
				case WEST -> { ax0 = 1 - z1; az0 = x0; ax1 = 1 - z0; az1 = x1; }
				default -> { ax0 = x0; az0 = z0; ax1 = x1; az1 = z1; }
			}
			out[0] = Shapes.or(out[0], Shapes.box(ax0, y0, az0, ax1, y1, az1));
		});
		return out[0];
	}

	public static class VanPart extends Block {
		public static final IntegerProperty PART = IntegerProperty.create("part", 0, VanData.PARTS - 1);
		VanPart(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(PART, 0)); }
		@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(PART); }
	}

	// ---------------------------------------------------------------- простой декор с поворотом
	public static class Facing extends HorizontalDirectionalBlock {
		private final VoxelShape southShape;
		public Facing(Properties p, VoxelShape southShape) {
			super(p);
			this.southShape = southShape;
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
		}
		@Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { throw new UnsupportedOperationException(); }
		@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
		@Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
			return rotated(southShape, s.getValue(FACING));
		}
	}

	// ---------------------------------------------------------------- гирлянда (висит провисом под блоком, лампочки светят)
	public static class Garland extends Block {
		public static final BooleanProperty LIT = BlockStateProperties.LIT;
		public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
		Garland(Properties p) {
			super(p);
			registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(AXIS, Direction.Axis.X));
		}
		@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LIT, AXIS); }
		@Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Block.box(0, 0, 6, 16, 3, 10); }
	}

	// ---------------------------------------------------------------- кнопка «Готов» на стене бара
	public static class ReadyButton extends Facing {
		ReadyButton(Properties p) { super(p, Block.box(3, 3, 0, 13, 13, 3)); }
		@Override
		protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!level.isClientSide() && player instanceof ServerPlayer sp) Hub.pressReady(sp, pos);
			return InteractionResult.SUCCESS;
		}
	}

	// ---------------------------------------------------------------- телевизор (картинку рисует TvRenderer)
	public static class Tv extends Facing implements EntityBlock {
		Tv(Properties p) { super(p, Block.box(2, 2, 0, 14, 14, 2)); }
		@Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
		@Override public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TvEntity(pos, st); }
	}

	public static class TvEntity extends BlockEntity {
		public TvEntity(BlockPos pos, BlockState st) { super(TV_BE, pos, st); }
	}

	// ---------------------------------------------------------------- витрина с экспонатом (лопаты из Говномесов)
	public enum Exhibit implements StringRepresentable {
		DEZHAVU("dezhavu"), ANIME("anime");
		private final String name;
		Exhibit(String n) { name = n; }
		@Override public String getSerializedName() { return name; }
	}

	public static class Showcase extends Facing implements EntityBlock {
		public static final EnumProperty<Exhibit> EXHIBIT = EnumProperty.create("exhibit", Exhibit.class);
		Showcase(Properties p) {
			super(p, Block.box(1, 0, 0, 15, 16, 6));
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(EXHIBIT, Exhibit.DEZHAVU));
		}
		@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, EXHIBIT); }
		@Override public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new ShowcaseEntity(pos, st); }
	}

	public static class ShowcaseEntity extends BlockEntity {
		public ShowcaseEntity(BlockPos pos, BlockState st) { super(SHOWCASE_BE, pos, st); }
	}

	// ---------------------------------------------------------------- стенд записок Короля 4×3 (бумажки рисует NotesBoardRenderer)
	public static class NotesBoard extends HorizontalDirectionalBlock implements EntityBlock {
		NotesBoard(Properties p) {
			super(p);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(NOTES_PART, 0));
		}
		@Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { throw new UnsupportedOperationException(); }
		@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, NOTES_PART); }
		@Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
			return rotated(Block.box(0, 0, 6.5, 16, 16, 9.5), s.getValue(FACING));
		}
		@Override public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
			return st.getValue(NOTES_PART) == 0 ? new NotesEntity(pos, st) : null;
		}
	}

	/** Поставить стенд: part = row*4 + col; ряды снизу вверх, колонки слева направо, если стоять лицом к стенду. */
	public static void placeNotes(Level l, BlockPos col0row0, Direction facing) {
		Direction colDir = facing.getCounterClockWise();
		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 4; col++) {
				BlockPos p = col0row0.relative(colDir, col).above(row);
				l.setBlock(p, NOTES.defaultBlockState().setValue(NotesBoard.FACING, facing).setValue(NOTES_PART, row * 4 + col),
						Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		if (l.getBlockEntity(col0row0) instanceof NotesEntity be) be.fillAll();
	}

	/** Часть 0 стенда хранит, какие цитаты приколоты. В баре висят все 16 — коллекция собрана. */
	public static class NotesEntity extends BlockEntity {
		public final int[] notes = new int[16];
		public int count = 0;
		public NotesEntity(BlockPos pos, BlockState st) { super(NOTES_BE, pos, st); }

		public void fillAll() {
			count = 16;
			for (int i = 0; i < 16; i++) notes[i] = i + 1;
			setChanged();
			if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}

		@Override
		protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput t) {
			t.putIntArray("notes", java.util.Arrays.copyOf(notes, count));
		}

		@Override
		protected void loadAdditional(net.minecraft.world.level.storage.ValueInput t) {
			int[] a = t.getIntArray("notes").orElse(new int[0]);
			count = Math.min(16, a.length);
			System.arraycopy(a, 0, notes, 0, count);
		}

		@Override public CompoundTag getUpdateTag(HolderLookup.Provider r) { return saveWithoutMetadata(r); }
		@Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
	}
}
