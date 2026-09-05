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
    public static final Item INCOMPLETE_TURRET = registerItem("incomplete_turret",
            new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new FabricItemSettings()));
    public static final Item INCOMPLETE_SHOTGUN_BARREL = registerItem("incomplete_shotgun_barrel",
            new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new FabricItemSettings()));
    public static final Item INCOMPLETE_MINIGUN_BARREL = registerItem("incomplete_minigun_barrel",
            new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new FabricItemSettings()));
    public static final Item INCOMPLETE_AUTOMATIC_BARREL = registerItem("incomplete_automatic_barrel",
            new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new FabricItemSettings()));
    public static final Item INCOMPLETE_ROCKET_BARREL = registerItem("incomplete_rocket_barrel",
            new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new FabricItemSettings()));
    public static final Item FISHEYE_LENS = registerItem("fisheye_lens", new FisheyeLensItem(new FabricItemSettings()));
    public static final Item KEYCARD = registerItem("keycard", new KeycardItem(new FabricItemSettings().maxCount(1)));
    public static final Item UNIVERSAL_KEYCARD = registerItem("universal_keycard",
            new UniversalKeycardItem(new FabricItemSettings().maxCount(1)
                    .rarity(net.minecraft.util.Rarity.EPIC)));
    public static final Item KEYCARD_READER = registerItem("keycard_reader",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.keycard_reader"));
    public static final Item CODE_LOCK = registerItem("code_lock",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.code_lock"));

    public static final Item SHOTGUN_BARREL = registerItem("shotgun_barrel",
            new TooltipItem(new FabricItemSettings().maxCount(1),
                    "tooltip.create-security.shotgun_barrel"));

    public static final Item MINIGUN_BARREL = registerItem("minigun_barrel",
            new TooltipItem(new FabricItemSettings().maxCount(1),
                    "tooltip.create-security.minigun_barrel"));

    public static final Item AUTOMATIC_BARREL = registerItem("automatic_barrel",
            new TooltipItem(new FabricItemSettings().maxCount(1),
                    "tooltip.create-security.automatic_barrel"));

    public static final Item REGULAR_556_AMMO = registerItem("regular_5.56mm_ammo",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.regular_5.56mm_ammo"));

    public static final Item INCENDIARY_BUCKSHOT_12G = registerItem("12_gauge_incendiary_buckshot",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.12_gauge_incendiary_buckshot"));

    public static final Item HE_ROCKET = registerItem("he_rocket",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.he_rocket"));

    public static final Item SLUG_12G = registerItem("12_gauge_slug",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.12_gauge_slug"));

    public static final Item BUCKSHOT_12G = registerItem("12_gauge_buckshot",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.12_gauge_buckshot"));

    public static final Item INCENDIARY_556_AMMO = registerItem("incendiary_5.56mm_ammo",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.incendiary_5.56mm_ammo"));

    public static final Item REGULAR_762_AMMO = registerItem("regular_7.62x51mm_ammo",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.regular_7.62x51mm_ammo"));

    public static final Item ROCKET_ROUND = registerItem("rocket",
            new TooltipItem(new FabricItemSettings(), "tooltip.create-security.rocket"));

    public static final Item ROCKET_BARREL = registerItem("rocket_barrel",
            new TooltipItem(new FabricItemSettings().maxCount(1),
                    "tooltip.create-security.rocket_barrel"));

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
