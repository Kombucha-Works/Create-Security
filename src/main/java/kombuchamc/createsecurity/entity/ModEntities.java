package kombuchamc.createsecurity.entity;

import kombuchamc.createsecurity.CreateSecurity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    public static final EntityType<BulletEntity> BULLET = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(CreateSecurity.MOD_ID, "bullet"),
            FabricEntityTypeBuilder.<BulletEntity>create(SpawnGroup.MISC, BulletEntity::new)
                    .dimensions(EntityDimensions.fixed(0.2f, 0.2f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .build());

    public static void registerEntities() {
        CreateSecurity.LOGGER.info("Registering Mod Entities for " + CreateSecurity.MOD_ID);
    }
}
