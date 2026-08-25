package kombuchamc.createsecurity.ponder;

import com.simibubi.create.foundation.ponder.PonderRegistry;
import com.simibubi.create.foundation.ponder.PonderStoryBoardEntry;
import com.simibubi.create.foundation.ponder.PonderStoryBoardEntry.PonderStoryBoard;
import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class CreateSecurityPonderIndex {

    public static void register() {
        Identifier CAMERA = Registries.BLOCK.getId(RegisterModBlocks.CAMERA_BLOCK);
        Identifier CAMERA_DISPLAY = Registries.BLOCK.getId(RegisterModBlocks.CAMERA_DISPLAY_BLOCK);
        Identifier CAMERA_LINK = Registries.BLOCK.getId(RegisterModBlocks.CAMERA_LINK_BLOCK);

        add("camera/1", CAMERA, CameraScenes::cameraFunctions);
        add("camera/2", CAMERA, CameraScenes::cameraLinking);

        add("camera/1", CAMERA_DISPLAY, CameraScenes::cameraFunctions);
        add("camera/2", CAMERA_DISPLAY, CameraScenes::cameraLinking);

        add("camera/1", CAMERA_LINK, CameraScenes::cameraFunctions);
        add("camera/2", CAMERA_LINK, CameraScenes::cameraLinking);
    }

    private static void add(String schematicPath, Identifier component, PonderStoryBoard board) {
        PonderRegistry.addStoryBoard(new PonderStoryBoardEntry(board, CreateSecurity.MOD_ID, schematicPath, component));
    }
}

