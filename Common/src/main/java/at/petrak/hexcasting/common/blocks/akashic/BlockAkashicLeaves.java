package at.petrak.hexcasting.common.blocks.akashic;

import at.petrak.hexcasting.annotations.SoftImplement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BlockAkashicLeaves extends LeavesBlock {
    public BlockAkashicLeaves(Properties props) {
        super(props);
    }

    @SoftImplement("forge")
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @SoftImplement("forge")
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }

    @SoftImplement("forge")
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 30;
    }
}
