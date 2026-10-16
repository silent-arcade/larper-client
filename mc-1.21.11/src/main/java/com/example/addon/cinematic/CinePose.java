package com.example.addon.cinematic;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public final class CinePose {
    public static final float END = 3.2f;
    public static final float ANGEL_AT = 2.2f;

    private CinePose() {
    }

    static float smooth(float a, float b, float t) {
        float x = Math.max(0, Math.min(1, (t - a) / (b - a)));
        return x * x * (3 - 2 * x);
    }

    static float fall(float t) {
        float x = Math.max(0, Math.min(1, (t - 1.45f) / 0.6f));
        return x * x;
    }

    public static void state(LivingEntityRenderState s, float t) {
        float look = smooth(0.15f, 0.7f, t) * (1 - smooth(1.3f, 1.9f, t));
        float slump = smooth(1.8f, 2.4f, t);
        s.pitch = 55 * look;
        s.relativeHeadYaw = 28 * look + 65 * slump;
        s.deathTime = 0;
        s.hurt = t < 0.15f;
        s.limbSwingAmplitude = 0;
    }

    public static void body(LivingEntityRenderState s, MatrixStack m, float t) {
        float jolt = (float) Math.sin(Math.PI * Math.min(1, t / 0.25f)) * 7;
        float hunch = 14 * smooth(0.15f, 0.9f, t);
        float kneel = smooth(1.1f, 1.45f, t);
        float f = fall(t);
        float bounce = t > 2.05f && t < 2.35f ? (float) Math.sin(Math.PI * (t - 2.05f) / 0.3f) * 6 : 0;
        if (s instanceof BipedEntityRenderState) {
            float hip = s.height * 0.385f;
            float tilt = jolt - hunch * (1 - f) - 90 * f + bounce;
            m.translate(0, -hip * kneel + 0.13f * f, 0);
            m.translate(0, hip, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tilt));
            m.translate(0, -hip, 0);
        } else {
            m.translate(0, -s.height * 0.2f * kneel, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(jolt - hunch * (1 - f)));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90 * f - bounce));
        }
    }

    public static void limbs(BipedEntityModel<?> model, float t) {
        float reach = smooth(0.15f, 0.8f, t) * (1 - smooth(1.2f, 1.6f, t));
        float relax = smooth(0.1f, 0.9f, t);
        float kneel = smooth(1.1f, 1.45f, t) * (1 - fall(t));
        set(model.rightArm, mix(model.rightArm.pitch * (1 - relax), -0.55f, reach), mix(model.rightArm.yaw * (1 - relax), -0.65f, reach), 0.05f);
        set(model.leftArm, model.leftArm.pitch * (1 - relax) + 0.1f * relax, model.leftArm.yaw * (1 - relax), -0.08f);
        float twitch = t > 2.45f && t < 2.75f ? (float) Math.sin(Math.PI * (t - 2.45f) / 0.15f) * 0.22f : 0;
        set(model.rightLeg, 1.45f * kneel + twitch, 0.05f, 0.04f);
        set(model.leftLeg, 1.45f * kneel - twitch * 0.6f, -0.05f, -0.04f);
    }

    public static void angel(BipedEntityModel<?> model, float t) {
        float flap = (float) Math.sin(t * 2.2) * 0.08f;
        set(model.rightArm, 0, 0, 1.15f + flap);
        set(model.leftArm, 0, 0, -1.15f - flap);
        set(model.rightLeg, 0.08f, 0, 0.03f);
        set(model.leftLeg, 0.08f, 0, -0.03f);
    }

    private static float mix(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private static void set(ModelPart p, float pitch, float yaw, float roll) {
        p.pitch = pitch;
        p.yaw = yaw;
        p.roll = roll;
    }
}
