package kombuchamc.createsecurity.items;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class RegisterModItemGroups {

    public static final ItemGroup DISPLAY_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(CreateSecurity.MOD_ID, "portable_camera_display"),
            FabricItemGroup.builder().displayName(Text.translatable("itemgroup.create-security"))
                    .icon(() -> new ItemStack(RegisterModItems.PORTABLE_CAMERA_DISPLAY)).entries((displayContext, entries) -> {

                        entries.add(RegisterModBlocks.CAMERA_BLOCK);
                        entries.add(RegisterModBlocks.CAMERA_LINK_BLOCK);
                        entries.add(RegisterModBlocks.CAMERA_DISPLAY_BLOCK);
                        entries.add(RegisterModBlocks.MONITOR);
                        entries.add(RegisterModItems.PORTABLE_CAMERA_DISPLAY);
                        entries.add(RegisterModItems.PLAYER_LIST);
                        entries.add(RegisterModBlocks.ALARM);
                        entries.add(RegisterModBlocks.TURRET);
                        entries.add(RegisterModItems.SHOTGUN_BARREL);
                        entries.add(RegisterModItems.MINIGUN_BARREL);
                        entries.add(RegisterModItems.AUTOMATIC_BARREL);
                        entries.add(RegisterModItems.ROCKET_BARREL);
                        entries.add(RegisterModItems.REGULAR_556_AMMO);
                        entries.add(RegisterModItems.INCENDIARY_BUCKSHOT_12G);
                        entries.add(RegisterModItems.HE_ROCKET);
                        entries.add(RegisterModItems.SLUG_12G);
                        entries.add(RegisterModItems.BUCKSHOT_12G);
                        entries.add(RegisterModItems.INCENDIARY_556_AMMO);
                        entries.add(RegisterModItems.REGULAR_762_AMMO);
                        entries.add(RegisterModItems.ROCKET_ROUND);
                        entries.add(RegisterModItems.FISHEYE_LENS);
                        entries.add(RegisterModItems.KEYCARD_READER);
                        entries.add(RegisterModItems.CODE_LOCK);
                        entries.add(RegisterModBlocks.KEYCARD_EDITOR);
                        entries.add(RegisterModItems.KEYCARD);
                        entries.add(RegisterModItems.UNIVERSAL_KEYCARD);

                        for (net.minecraft.block.Block block
                                : kombuchamc.createsecurity.block.obsidian.ObsidianBlocks.ALL) {
                            entries.add(block);
                        }

                    }).build());

    public static void registerItemGropus() {
        CreateSecurity.LOGGER.info("Registering Item  for " + CreateSecurity.MOD_ID);
    }

}
