package kombuchamc.createsecurity.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import kombuchamc.createsecurity.block.entity.ModBlockEntities;
import kombuchamc.createsecurity.block.entity.TurretBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class TurretBlock extends KineticBlock implements IBE<TurretBlockEntity> {

    public static final BooleanProperty DEPLOYED = BooleanProperty.of("deployed");
    public static final IntProperty LIGHT = IntProperty.of("light", 0, 15);

    public TurretBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(DEPLOYED, false).with(LIGHT, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(DEPLOYED, LIGHT);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(WorldView world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        TurretBlockEntity turret = world.getBlockEntity(pos) instanceof TurretBlockEntity be ? be : null;
        if (turret == null) return ActionResult.PASS;

        if (held.isEmpty()) {
            if (!world.isClient && BlockOwnership.checkAndWarn(turret, player)) {
                player.openHandledScreen(turret);
            }
            return ActionResult.SUCCESS;
        }

        if (!(held.getItem() instanceof BlockItem blockItem)) return ActionResult.PASS;
        BlockState applied = blockItem.getBlock().getDefaultState();
        if (!applied.isOpaqueFullCube(world, pos)) return ActionResult.PASS;

        if (turret.getMaterial() != null) return ActionResult.PASS;

        if (!world.isClient) {
            turret.setMaterial(applied);
            if (!player.getAbilities().creativeMode) held.decrement(1);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @org.jetbrains.annotations.Nullable net.minecraft.entity.LivingEntity placer,
                         ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && placer instanceof PlayerEntity player
                && world.getBlockEntity(pos) instanceof TurretBlockEntity turret) {
            turret.setOwner(player.getUuid());
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())
                && world.getBlockEntity(pos) instanceof TurretBlockEntity turret) {
            if (turret.getMaterial() != null) dropMaterial(world, pos, turret.getMaterial());
            ItemScatterer.spawn(world, pos, turret);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    private static void dropMaterial(World world, BlockPos pos, BlockState material) {
        ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(material.getBlock()));
    }

    @Override
    public Class<TurretBlockEntity> getBlockEntityClass() {
        return TurretBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TurretBlockEntity> getBlockEntityType() {
        return ModBlockEntities.TURRET_BLOCK_ENTITY;
    }
}
