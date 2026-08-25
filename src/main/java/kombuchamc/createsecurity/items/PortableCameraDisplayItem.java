package kombuchamc.createsecurity.items;

import kombuchamc.createsecurity.block.RegisterModBlocks;
import kombuchamc.createsecurity.block.camera.CameraBlock;
import kombuchamc.createsecurity.block.entity.CameraBlockEntity;
import kombuchamc.createsecurity.network.ModPackets;
import kombuchamc.createsecurity.network.PortableCameraViewEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class PortableCameraDisplayItem extends Item {

    public static final int MAX_BOUND_CAMERAS = 10;

    private static final String NBT_DISPLAY_ID = "DisplayId";
    private static final String NBT_CAMERAS = "Cameras";

    private static final Style BODY_COLOR      = Style.EMPTY.withColor(TextColor.fromRgb(13211468));
    private static final Style HIGHLIGHT_COLOR = Style.EMPTY.withColor(TextColor.fromRgb(15850873));

    public PortableCameraDisplayItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        if (player == null || !player.isSneaking()) return ActionResult.PASS;
        if (!world.getBlockState(pos).isOf(RegisterModBlocks.CAMERA_BLOCK)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof CameraBlockEntity camera)) return ActionResult.PASS;

        if (!camera.isOwner(player)
                && !kombuchamc.createsecurity.config.CSConfigs.canBypassOwner(player)) {
            player.sendMessage(Text.translatable("message.create-security.not_camera_owner")
                    .formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }

        ItemStack stack = context.getStack();
        UUID displayId = getOrCreateDisplayId(stack);
        List<Long> cameras = getBoundCameraList(stack);
        Long key = pos.asLong();
        UUID boundTo = camera.getPortableDisplayId();

        if (displayId.equals(boundTo)) {
            camera.setPortableDisplayId(null);
            cameras.remove(key);
            writeBoundCameras(stack, cameras);
            player.sendMessage(Text.translatable("message.create-security.portable_unbound",
                    cameras.size(), MAX_BOUND_CAMERAS), true);
        } else if (boundTo != null) {
            player.sendMessage(Text.translatable("message.create-security.camera_already_bound")
                    .formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        } else if (cameras.size() >= MAX_BOUND_CAMERAS) {
            player.sendMessage(Text.translatable("message.create-security.portable_full",
                    MAX_BOUND_CAMERAS).formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        } else {
            camera.setPortableDisplayId(displayId);
            if (!cameras.contains(key)) cameras.add(key);
            writeBoundCameras(stack, cameras);
            player.sendMessage(Text.translatable("message.create-security.portable_bound",
                    cameras.size(), MAX_BOUND_CAMERAS), true);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) return TypedActionResult.pass(stack);
        if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity serverPlayer) {
            openViewer(serverPlayer, serverWorld, stack);
        }
        return TypedActionResult.success(stack);
    }

    private static void openViewer(ServerPlayerEntity player, ServerWorld world, ItemStack stack) {
        UUID displayId = getDisplayId(stack);
        List<Long> cameras = getBoundCameraList(stack);
        List<PortableCameraViewEntry> entries = new ArrayList<>();
        boolean pruned = false;

        if (displayId != null) {
            Iterator<Long> it = cameras.iterator();
            while (it.hasNext()) {
                BlockPos pos = BlockPos.fromLong(it.next());
                if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {

                    entries.add(new PortableCameraViewEntry(pos, null, false, null));
                    continue;
                }
                BlockState state = world.getBlockState(pos);
                BlockEntity be = world.getBlockEntity(pos);
                if (!state.isOf(RegisterModBlocks.CAMERA_BLOCK)
                        || !(be instanceof CameraBlockEntity camera)
                        || !displayId.equals(camera.getPortableDisplayId())) {

                    it.remove();
                    pruned = true;
                    continue;
                }

                Direction lensDir = state.get(CameraBlock.FACING).getOpposite();
                entries.add(new PortableCameraViewEntry(pos, lensDir,
                        camera.isFisheyeLensInstalled(), camera.getCustomName()));
            }
        }
        if (pruned) writeBoundCameras(stack, cameras);

        if (entries.isEmpty()) {
            player.sendMessage(Text.translatable("message.create-security.portable_no_cameras")
                    .formatted(Formatting.RED), true);
            return;
        }
        ModPackets.sendOpenPortableView(player, entries);
    }

    private static UUID getOrCreateDisplayId(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        if (!nbt.containsUuid(NBT_DISPLAY_ID)) {
            nbt.putUuid(NBT_DISPLAY_ID, UUID.randomUUID());
        }
        return nbt.getUuid(NBT_DISPLAY_ID);
    }

    @Nullable
    private static UUID getDisplayId(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.containsUuid(NBT_DISPLAY_ID) ? nbt.getUuid(NBT_DISPLAY_ID) : null;
    }

    private static List<Long> getBoundCameraList(ItemStack stack) {
        List<Long> list = new ArrayList<>();
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            for (long l : nbt.getLongArray(NBT_CAMERAS)) list.add(l);
        }
        return list;
    }

    private static void writeBoundCameras(ItemStack stack, List<Long> cameras) {
        stack.getOrCreateNbt().putLongArray(NBT_CAMERAS, cameras);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        int bound = getBoundCameraList(stack).size();
        if (bound > 0) {
            tooltip.add(Text.translatable("tooltip.create-security.portable_bound_count",
                    bound, MAX_BOUND_CAMERAS).formatted(Formatting.GRAY));
        }
        if (Screen.hasShiftDown()) {
            tooltip.add(Text.translatable("tooltip.create-security.holding_shift"));
            tooltip.add(Text.literal(""));
            addParsedTooltip(tooltip, Text.translatable("tooltip.create-security.portable_camera_display").getString());
        } else {
            tooltip.add(Text.translatable("tooltip.create-security.hold_shift"));
        }
    }

    private static void addParsedTooltip(List<Text> tooltip, String raw) {
        for (String line : raw.split("/n", -1))
            tooltip.add(parseHighlighted(line));
    }

    private static MutableText parseHighlighted(String raw) {
        MutableText result = Text.literal("");
        String[] parts = raw.split("_", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            Style style = (i % 2 == 1) ? HIGHLIGHT_COLOR : BODY_COLOR;
            result.append(Text.literal(parts[i]).setStyle(style));
        }
        return result;
    }
}

