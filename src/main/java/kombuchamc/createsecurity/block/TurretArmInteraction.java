package kombuchamc.createsecurity.block;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import kombuchamc.createsecurity.CreateSecurity;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TurretArmInteraction {

    public static void register() {
        Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE,
                new Identifier(CreateSecurity.MOD_ID, "turret"), new TurretPointType());
    }

    public static class TurretPointType extends ArmInteractionPointType {

        @Override
        public boolean canCreatePoint(World world, BlockPos pos, BlockState state) {
            return state.isOf(RegisterModBlocks.TURRET);
        }

        @Override
        public ArmInteractionPoint createPoint(World world, BlockPos pos, BlockState state) {
            return new TurretPoint(this, world, pos, state);
        }
    }

    public static class TurretPoint extends ArmInteractionPoint {

        public TurretPoint(ArmInteractionPointType type, World world, BlockPos pos, BlockState state) {
            super(type, world, pos, state);
        }

        @Override
        public void cycleMode() {
        }

        @Override
        public ItemStack extract(int amount, TransactionContext context) {
            return ItemStack.EMPTY;
        }
    }
}
