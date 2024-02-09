package com.starfish_studios.hamsters.block;

import com.starfish_studios.hamsters.entity.Hamster;
import com.starfish_studios.hamsters.entity.SeatEntity;
import com.starfish_studios.hamsters.registry.HamstersBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class HamsterWheelBlock extends BaseEntityBlock implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public HamsterWheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    protected static final VoxelShape NORTH = Block.box(1, 0, 3, 15, 16, 16);
    protected static final VoxelShape SOUTH = Block.box(1, 0, 0, 15, 16, 13);
    protected static final VoxelShape EAST = Block.box(0, 0, 1, 13, 16, 15);
    protected static final VoxelShape WEST = Block.box(3, 0, 1, 16, 16, 15);


    public boolean isMountable(BlockState state) {
        return true;
    }


    public BlockPos primaryDismountLocation(Level level, BlockState state, BlockPos pos) {
        return pos;
    }

    public float setRiderRotation(BlockState state, Entity entity) {
        return entity.getYRot();
    }

    public static boolean isOccupied(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // level.setBlock(pos, state.setValue(POWERED, true), 3);
        return !level.getEntitiesOfClass(SeatEntity.class, new AABB(pos)).isEmpty();
    }

    public float seatHeight(BlockState state) {
        return 0F;
    }

    public static Optional<Entity> getLeashed(Player player) {
        List<Entity> entities = player.level().getEntities((Entity) null, player.getBoundingBox().inflate(10), e -> true);
        for (Entity e : entities)
            if (e instanceof Mob mob && mob.getLeashHolder() == player && canBePickedUp(e)) return Optional.of(mob);
        return Optional.empty();
    }

    public static boolean ejectSeatedExceptPlayer(Level level, SeatEntity seatEntity) {
        List<Entity> passengers = seatEntity.getPassengers();
        if (passengers.isEmpty()) return false;
        if (!level.isClientSide) seatEntity.ejectPassengers();
        return true;
    }

    public static boolean canBePickedUp(Entity passenger) {
        if (passenger instanceof Player) return false;
        return passenger instanceof LivingEntity;
    }

    public static void sitDown(Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;
        if (entity == null) return;

        SeatEntity seat = new SeatEntity(level, pos);
        level.addFreshEntity(seat);
        entity.startRiding(seat);

        level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return isOccupied(level, pos) ? 15 : 0;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return HamstersBlockEntities.HAMSTER_WHEEL.get().create(blockPos, blockState);
    }


    @Override
    public InteractionResult use(BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (player.getItemInHand(interactionHand).isEmpty() || (!player.getItemInHand(interactionHand).isEmpty() && !player.isShiftKeyDown())) {
            if (!level.mayInteract(player, blockPos)) return InteractionResult.PASS;

            if (!isMountable(blockState) || player.isPassenger() || player.isCrouching()) return InteractionResult.PASS;

            if (isOccupied(level, blockPos)) {


                List<SeatEntity> seats = level.getEntitiesOfClass(SeatEntity.class, new AABB(blockPos));

                if (seats.get(0).getFirstPassenger() instanceof Hamster hamster) {
                    hamster.setWaitTimeWhenRunningTicks(0);
                    hamster.setWaitTimeBeforeRunTicks(hamster.getRandom().nextInt(200) + 600);
                }

                if (ejectSeatedExceptPlayer(level, seats.get(0))) return InteractionResult.SUCCESS;
                return InteractionResult.PASS;
            }
            if (getLeashed(player).isPresent() && getLeashed(player).get() instanceof Hamster hamster) {
                hamster.setWaitTimeBeforeRunTicks(0);
                sitDown(level, blockPos, hamster);
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(blockState, level, blockPos, player, interactionHand, blockHitResult);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), net.minecraft.core.Direction.UP);
    }
}
