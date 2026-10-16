package com.example.addon.mixin;

import com.example.addon.modules.CameraTilt;
import com.example.addon.modules.SmoothThirdPerson;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow @Final private Quaternionf rotation;
    @Shadow private boolean thirdPerson;

    @Shadow protected abstract void setPos(Vec3d pos);

    @Shadow public abstract Vec3d getCameraPos();

    @Inject(method = "update", at = @At("TAIL"))
    private void larper$cameraFx(World area, Entity focused, boolean thirdPersonView, boolean inverseView, float tickProgress, CallbackInfo ci) {
        if (focused == null || Modules.get().isActive(Freecam.class)) return;
        SmoothThirdPerson trail = Modules.get().get(SmoothThirdPerson.class);
        if (thirdPerson && trail != null && trail.isActive()) setPos(trail.apply(getCameraPos(), focused, tickProgress));
        CameraTilt tilt = Modules.get().get(CameraTilt.class);
        if (tilt != null && tilt.isActive()) rotation.rotateZ((float) Math.toRadians(tilt.roll(focused, tickProgress, thirdPerson)));
    }
}
