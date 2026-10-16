package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class SmoothThirdPerson extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("follow-speed").description("How fast the camera catches up with you. Lower = more lag.").defaultValue(6).range(1, 30).sliderRange(2, 15).build());

    private final Setting<Double> maxLag = sg.add(new DoubleSetting.Builder()
        .name("max-lag").description("The camera never trails more than this many blocks behind.").defaultValue(2.5).range(0.2, 8).sliderRange(0.5, 5).build());

    private Vec3d focus;
    private long last;

    public SmoothThirdPerson() {
        super(AddonTemplate.VISUAL, "smooth-third-person", "The third-person camera trails you with a soft, cinematic lag.");
    }

    @Override
    public void onActivate() {
        focus = null;
        last = 0;
    }

    public Vec3d apply(Vec3d cam, Entity e, float tickProgress) {
        Vec3d eye = e.getCameraPosVec(tickProgress);
        long now = System.nanoTime();
        double dt = last == 0 ? 0.016 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (focus == null || focus.distanceTo(eye) > 16) focus = eye;
        focus = focus.lerp(eye, 1 - Math.exp(-dt * speed.get()));
        Vec3d lag = focus.subtract(eye);
        if (lag.length() > maxLag.get()) focus = eye.add(lag.normalize().multiply(maxLag.get()));

        Vec3d want = focus.add(cam.subtract(eye));
        var hit = e.getEntityWorld().raycast(new RaycastContext(eye, want, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, e));
        if (hit.getType() != HitResult.Type.MISS) want = hit.getPos().add(eye.subtract(want).normalize().multiply(0.2));
        return want;
    }
}
