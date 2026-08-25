package kombuchamc.createsecurity.block.camera;

import kombuchamc.createsecurity.block.entity.CameraBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class CameraBlockModel extends GeoModel<CameraBlockEntity> {
    private static final Identifier MODEL = new Identifier("create-security", "geo/camera_horizontal.geo.json");
    private static final Identifier TEXTURE = new Identifier("create-security", "textures/block/camera.png");
    private static final Identifier ANIMATION = new Identifier("create-security", "animations/camera_horizontal.animation.json");

    @Override
    public Identifier getModelResource(CameraBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(CameraBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(CameraBlockEntity animatable) {
        return ANIMATION;
    }
}

