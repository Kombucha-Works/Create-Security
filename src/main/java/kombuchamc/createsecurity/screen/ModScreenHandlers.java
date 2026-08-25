package kombuchamc.createsecurity.screen;

import kombuchamc.createsecurity.CreateSecurity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreenHandlers {
    public static ScreenHandlerType<CameraScreenHandler> CAMERA_SCREEN_HANDLER;
    public static ScreenHandlerType<CameraLinkScreenHandler> CAMERA_LINK_SCREEN_HANDLER;

    public static void registerScreenHandlers() {
        CAMERA_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(CreateSecurity.MOD_ID, "camera_screen"),
                new ExtendedScreenHandlerType<>(CameraScreenHandler::new)
        );
        CAMERA_LINK_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(CreateSecurity.MOD_ID, "camera_link_screen"),
                new ExtendedScreenHandlerType<>(CameraLinkScreenHandler::new)
        );
    }
}
