package kombuchamc.createsecurity.block.entity;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static final BlockEntityType<CameraBlockEntity> CAMERA_BLOCK_ENTITY =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(CreateSecurity.MOD_ID, "camera_be"),
                    FabricBlockEntityTypeBuilder.create(CameraBlockEntity::new,
                            RegisterModBlocks.CAMERA_BLOCK).build());

    public static final BlockEntityType<CameraLinkBlockEntity> CAMERA_LINK_BLOCK_ENTITY =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(CreateSecurity.MOD_ID, "camera_link_be"),
                    FabricBlockEntityTypeBuilder.create(CameraLinkBlockEntity::new,
                            RegisterModBlocks.CAMERA_LINK_BLOCK).build());

    public static final BlockEntityType<CameraDisplayBlockEntity> CAMERA_DISPLAY_BLOCK_ENTITY =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(CreateSecurity.MOD_ID, "camera_display_be"),
                    FabricBlockEntityTypeBuilder.create(CameraDisplayBlockEntity::new,
                            RegisterModBlocks.CAMERA_DISPLAY_BLOCK).build());

    public static final BlockEntityType<MonitorBlockEntity> MONITOR_BLOCK_ENTITY =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(CreateSecurity.MOD_ID, "monitor_be"),
                    FabricBlockEntityTypeBuilder.create(MonitorBlockEntity::new,
                            RegisterModBlocks.MONITOR).build());

    public static void registerBlockEntities() {
        CreateSecurity.LOGGER.info("Registering BlockEntities for" + CreateSecurity.MOD_ID);
    }
}

