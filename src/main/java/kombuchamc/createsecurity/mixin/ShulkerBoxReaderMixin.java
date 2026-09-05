package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.KeycardReaderAttachment;
import kombuchamc.createsecurity.block.KeycardReaderStore;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ShulkerBoxBlock.class)
public class ShulkerBoxReaderMixin {

    @Inject(method = "onBreak", at = @At("HEAD"))
    private void createsecurity$detachReader(World world, BlockPos pos, BlockState state,
                                             PlayerEntity player, CallbackInfo ci) {
        if (world instanceof ServerWorld serverWorld) {
            KeycardReaderAttachment.detach(serverWorld, pos);
        }
    }

    @Redirect(method = "onBreak", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private boolean createsecurity$tagCreativeDrop(World spawnWorld, Entity entity,
                                                   World world, BlockPos pos, BlockState state,
                                                   PlayerEntity player) {
        if (entity instanceof ItemEntity item) {
            KeycardReaderStore.Entry entry = KeycardReaderAttachment.claim(pos);
            if (entry != null) KeycardReaderAttachment.write(item.getStack(), entry);
        }
        return spawnWorld.spawnEntity(entity);
    }

    @Inject(method = "getDroppedStacks", at = @At("RETURN"))
    private void createsecurity$tagDroppedBox(BlockState state, LootContextParameterSet.Builder builder,
                                              CallbackInfoReturnable<List<ItemStack>> cir) {
        BlockEntity blockEntity = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
        if (blockEntity == null) return;
        KeycardReaderStore.Entry entry = KeycardReaderAttachment.claim(blockEntity.getPos());
        if (entry == null) return;

        for (ItemStack stack : cir.getReturnValue()) {
            if (stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                KeycardReaderAttachment.write(stack, entry);
            }
        }
    }

    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void createsecurity$restoreReader(World world, BlockPos pos, BlockState state,
                                              LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (world instanceof ServerWorld serverWorld) {
            KeycardReaderAttachment.restore(serverWorld, pos, stack);
        }
    }
}
