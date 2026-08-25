package kombuchamc.createsecurity.ponder;

import com.simibubi.create.foundation.ponder.PonderPalette;
import com.simibubi.create.foundation.ponder.SceneBuilder;
import com.simibubi.create.foundation.ponder.SceneBuildingUtil;
import com.simibubi.create.foundation.ponder.Selection;
import com.simibubi.create.foundation.ponder.element.InputWindowElement;
import com.simibubi.create.foundation.utility.Pointing;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import kombuchamc.createsecurity.items.RegisterModItems;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static kombuchamc.createsecurity.block.CameraDisplay.FACING;

public class CameraScenes {

    private static void revealAll(SceneBuilder scene, SceneBuildingUtil util) {
        scene.world.showSection(util.select.everywhere(), Direction.UP);
    }

    private static Vec3d povEndpoint(BlockPos cameraPos, Direction facing,
                                      float angleDeg, double length) {

        double yaw = Math.toRadians(facing.asRotation() + angleDeg);
        double dx = -Math.sin(yaw) * length;
        double dz =  Math.cos(yaw) * length;

        return Vec3d.ofCenter(cameraPos).add(dx, 0.15, dz);
    }

    public static void cameraFunctions(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("camera_functions", "The Camera");

        BlockPos sideCameraPos = util.grid.at(2, 2, 2);
        BlockPos verticalCameraPos = util.grid.at(1, 2, 3);
        BlockPos verticalSupportPos = verticalCameraPos.down();
        Selection verticalSel = util.select.fromTo(verticalSupportPos, verticalCameraPos);

        Direction sideCameraFacing = Direction.NORTH;
        Vec3d camFront = util.vector.blockSurface(sideCameraPos, sideCameraFacing);
        Vec3d smallPovRightLine = povEndpoint(sideCameraPos, sideCameraFacing,  25f, 2.0);
        Vec3d smallPovLeftLine  = povEndpoint(sideCameraPos, sideCameraFacing, -25f, 2.0);
        Vec3d bigPovRightLine   = povEndpoint(sideCameraPos, sideCameraFacing,  60f, 2.0);
        Vec3d bigPovLeftLine    = povEndpoint(sideCameraPos, sideCameraFacing, -60f, 2.0);

        scene.world.showSection(util.select.everywhere().substract(verticalSel), Direction.SOUTH);
        scene.idle(20);

        scene.overlay.showOutline(PonderPalette.GREEN, "side", util.select.position(sideCameraPos), 80);
        scene.overlay.showText(80)
                .text("The Camera can be placed on the side of a block")
                .pointAt(util.vector.blockSurface(sideCameraPos, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world.showSection(verticalSel, Direction.NORTH);
        scene.idle(15);

        scene.overlay.showOutline(PonderPalette.GREEN, "vert", util.select.position(verticalCameraPos), 100);
        scene.overlay.showText(100)
                .text("Additionally, it can also be placed on top or on the bottom of a block")
                .pointAt(util.vector.blockSurface(verticalCameraPos, Direction.UP))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);
        scene.world.hideSection(verticalSel, Direction.SOUTH);
        scene.idle(10);

        scene.overlay.showLine(PonderPalette.BLUE,
                util.vector.blockSurface(sideCameraPos, Direction.NORTH),
                util.vector.centerOf(sideCameraPos.offset(Direction.NORTH, 2)),
                80);
        scene.overlay.showText(80)
                .text("Its lens points in the FACING direction")
                .pointAt(util.vector.blockSurface(sideCameraPos, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay.showOutline(PonderPalette.GREEN, "click", util.select.position(sideCameraPos), 60);
        scene.overlay.showControls((new InputWindowElement(util.vector.blockSurface(sideCameraPos, Direction.UP), Pointing.DOWN)).rightClick(), 60);
        scene.overlay.showText(60)
                .text("Clicking on the camera opens its GUI")
                .pointAt(util.vector.blockSurface(sideCameraPos, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        scene.overlay.showText(90)
                .text("The two left-hand slots are used to set a frequency")
                .pointAt(util.vector.blockSurface(sideCameraPos, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay.showText(100)
                .text("The third slot accepts a Fisheye Lens for a wider field of view")
                .pointAt(util.vector.blockSurface(sideCameraPos, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.overlay.showControls((new InputWindowElement(util.vector.blockSurface(sideCameraPos, Direction.UP), Pointing.DOWN))
                .withItem(RegisterModItems.FISHEYE_LENS.getDefaultStack()), 115);

        Vec3d camFront1 = camFront.add(0, 0.0001, 0);
        Vec3d camFront2 = camFront.add(0, 0.0002, 0);
        Vec3d camFront3 = camFront.add(0, 0.0003, 0);
        Vec3d camFront4 = camFront.add(0, 0.0004, 0);
        scene.overlay.showLine(PonderPalette.RED,   camFront1, smallPovRightLine, 115);
        scene.overlay.showLine(PonderPalette.RED,   camFront2, smallPovLeftLine,  115);
        scene.overlay.showLine(PonderPalette.GREEN, camFront3, bigPovRightLine,   115);
        scene.overlay.showLine(PonderPalette.GREEN, camFront4, bigPovLeftLine,    115);
        scene.idle(150);
    }

    public static void cameraLinking(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("camera_display_intro", "The Camera");
        BlockPos displayPos = util.grid.at(3, 1, 1);
        BlockPos linkPos = util.grid.at(4, 1, 1);
        Selection bigDisplayPos = util.select.fromTo(3, 1, 0, 3, 2, 2);
        scene.world.showSection(util.select.everywhere().substract(util.select.position(linkPos)).substract(bigDisplayPos), Direction.SOUTH);
        scene.idle(20);

        scene.world.showSection(util.select.position(displayPos), Direction.WEST);
        scene.world.replaceBlocks(util.select.position(displayPos), RegisterModBlocks.CAMERA_DISPLAY_BLOCK.getDefaultState().with(FACING, Direction.WEST), false);
        scene.idle(20);

        scene.overlay.showOutline(PonderPalette.GREEN, "disp", util.select.position(displayPos), 100);
        scene.overlay.showText(100)
                .text("To display the live feed from the camera, start by placing a Camera Display block")
                .pointAt(util.vector.blockSurface(displayPos, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.world.showSection(bigDisplayPos, Direction.WEST);
        scene.world.restoreBlocks(util.select.position(displayPos));
        scene.idle(20);

        scene.overlay.showOutline(PonderPalette.GREEN, "disp", bigDisplayPos, 95);
        scene.overlay.showText(95)
                .text("Camera Displays can be scaled infinitely if they are placed in a 3:2 ratio")
                .pointAt(util.vector.blockSurface(displayPos, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(105);

        scene.world.hideSection(bigDisplayPos.substract(util.select.position(displayPos)), Direction.EAST);
        scene.world.replaceBlocks(util.select.position(displayPos), RegisterModBlocks.CAMERA_DISPLAY_BLOCK.getDefaultState().with(FACING, Direction.WEST), false);
        scene.idle(20);

        scene.rotateCameraY(180);
        scene.idle(20);

        scene.world.showSection(util.select.position(linkPos), Direction.DOWN);
        scene.overlay.showOutline(PonderPalette.GREEN, "disp", util.select.position(linkPos), 90);
        scene.overlay.showText(90)
                .text("Now, place a Camera Link behind the Camera Display block")
                .pointAt(util.vector.centerOf(linkPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay.showControls((new InputWindowElement(util.vector.blockSurface(linkPos, Direction.UP), Pointing.DOWN)).rightClick(), 95);
        scene.overlay.showText(95)
                .text("Clicking the Camera Link opens its GUI where you can set a frequency")
                .pointAt(util.vector.centerOf(linkPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(105);

        scene.overlay.showText(130)
                .text("Make sure that the frequency is the same as the camera you want to display if not it won't work")
                .pointAt(util.vector.centerOf(linkPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(140);
    }
}

