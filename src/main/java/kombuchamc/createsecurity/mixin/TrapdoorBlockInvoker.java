package kombuchamc.createsecurity.mixin;

import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TrapdoorBlock.class)
public interface TrapdoorBlockInvoker {

    @Invoker("playToggleSound")
    void invokePlayToggleSound(PlayerEntity player, World world, BlockPos pos, boolean open);
}
