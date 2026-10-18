package com.example.addon.mixin;

import com.example.addon.modules.CameraTilt;
import com.example.addon.modules.SmoothThirdPerson;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(Vec3 pos);

    @Shadow public abstract Vec3 position();

    @Shadow public abstract Entity entity();

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void larper$cameraFx(float tickProgress, CallbackInfo ci) {
        Entity focused = entity();
        if (focused == null || Modules.get().isActive(Freecam.class)) return;
        SmoothThirdPerson trail = Modules.get().get(SmoothThirdPerson.class);
        if (detached && trail != null && trail.isActive()) setPosition(trail.apply(position(), focused, tickProgress));
        com.example.addon.modules.AutoF5 f5 = Modules.get().get(com.example.addon.modules.AutoF5.class);
        if (detached && f5 != null && f5.isActive() && f5.drivesCamera()) setPosition(f5.apply(position(), focused, tickProgress));
        CameraTilt tilt = Modules.get().get(CameraTilt.class);
        if (tilt != null && tilt.isActive()) rotation.rotateZ((float) Math.toRadians(tilt.roll(focused, tickProgress, detached)));
        com.example.addon.modules.Drugs drugs = Modules.get().get(com.example.addon.modules.Drugs.class);
        if (drugs != null && drugs.isActive()) rotation.rotateZ((float) Math.toRadians(drugs.roll()));
    }
}
