package kombuchamc.createsecurity.block;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import kombuchamc.createsecurity.CreateSecurity;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TurretArmInteraction {

    public static void register() {
        ArmInteractionPointType.register(new TurretPointType(new Identifier(CreateSecurity.MOD_ID, "turret")));
    }

    public static class TurretPointType extends ArmInteractionPointType {

        public TurretPointType(Identifier id) {
            super(id);
        }

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
