package kombuchamc.createsecurity.ponder;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class CreateSecurityPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return CreateSecurity.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<Identifier> helper) {
        Identifier camera = Registries.BLOCK.getId(RegisterModBlocks.CAMERA_BLOCK);
        Identifier cameraDisplay = Registries.BLOCK.getId(RegisterModBlocks.CAMERA_DISPLAY_BLOCK);
        Identifier cameraLink = Registries.BLOCK.getId(RegisterModBlocks.CAMERA_LINK_BLOCK);

        add(helper, camera, "camera/1", CameraScenes::cameraFunctions);
        add(helper, camera, "camera/2", CameraScenes::cameraLinking);

        add(helper, cameraDisplay, "camera/1", CameraScenes::cameraFunctions);
        add(helper, cameraDisplay, "camera/2", CameraScenes::cameraLinking);

        add(helper, cameraLink, "camera/1", CameraScenes::cameraFunctions);
        add(helper, cameraLink, "camera/2", CameraScenes::cameraLinking);
    }

    private static void add(PonderSceneRegistrationHelper<Identifier> helper, Identifier component,
                            String schematicPath, PonderStoryBoard board) {
        helper.addStoryBoard(component, schematicPath, board);
    }
}
