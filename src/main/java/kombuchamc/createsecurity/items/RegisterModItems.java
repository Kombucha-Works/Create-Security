package kombuchamc.createsecurity.items;

import kombuchamc.createsecurity.CreateSecurity;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import static kombuchamc.createsecurity.block.RegisterModBlocks.CAMERA_DISPLAY_BLOCK;

public class RegisterModItems {

    public static final Item PORTABLE_CAMERA_DISPLAY = registerItem("portable_camera_display", new PortableCameraDisplayItem(new FabricItemSettings().maxCount(1)));
    public static final Item PLAYER_LIST = registerItem("player_list", new PlayerListItem(new FabricItemSettings().maxCount(1)));

    public static final Item INCOMPLETE_PORTABLE_CAMERA_DISPLAY = registerItem("incomplete_portable_camera_display",
            new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new FabricItemSettings()));
    public static final Item INCOMPLETE_CAMERA_DISPLAY = registerItem("incomplete_camera_display",
            new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new FabricItemSettings()));
    public static final Item INCOMPLETE_CAMERA_LINK = registerItem("incomplete_camera_link",
            new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new FabricItemSettings()));
    public static final Item FISHEYE_LENS = registerItem("fisheye_lens", new FisheyeLensItem(new FabricItemSettings()));
    public static final Item KEYCARD = registerItem("keycard", new KeycardItem(new FabricItemSettings().maxCount(1)));
    public static final Item KEYCARD_READER = registerItem("keycard_reader",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.keycard_reader"));

    private static void addItemsToIngredientTabItemGroup(FabricItemGroupEntries entries) {

        entries.add(PORTABLE_CAMERA_DISPLAY);
        entries.add(CAMERA_DISPLAY_BLOCK);
        entries.add(kombuchamc.createsecurity.block.RegisterModBlocks.MONITOR);
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(CreateSecurity.MOD_ID, name), item);
    }

    public static void registerModItems() {
        CreateSecurity.LOGGER.info("Registering Mod Items for" + CreateSecurity.MOD_ID);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(RegisterModItems::addItemsToIngredientTabItemGroup);

    }
}

