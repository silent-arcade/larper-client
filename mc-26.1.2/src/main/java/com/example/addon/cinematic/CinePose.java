package com.example.addon.cinematic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * The death cinematic, as poses over time (seconds):
 *   0.00-0.15  the knife goes in: a jolt and a red flash
 *   0.15-1.10  the victim looks down at the knife, one hand reaching for it
 *   1.10-1.45  the knees give out
 *   1.45-2.05  goes limp and falls face down (side for non-humanoids), with a small bounce
 *   2.05-END   lies still, head rolled to the side
 */
public final class CinePose {
    public static final float END = 3.2f;
    public static final float ANGEL_AT = 2.2f; // when a killed player's angel rises out of the body

    private CinePose() {
    }

    static float smooth(float a, float b, float t) {
        float x = Math.max(0, Math.min(1, (t - a) / (b - a)));
        return x * x * (3 - 2 * x);
    }

    // Accelerates like a falling body
    static float fall(float t) {
        float x = Math.max(0, Math.min(1, (t - 1.45f) / 0.6f));
        return x * x;
    }

    /** Head and flags, before the model is posed. */
    public static void state(LivingEntityRenderState s, float t) {
        float look = smooth(0.15f, 0.7f, t) * (1 - smooth(1.3f, 1.9f, t));
        float slump = smooth(1.8f, 2.4f, t);
        s.xRot = 55 * look;
        s.yRot = 28 * look + 65 * slump; // toward the wound on their right
        s.deathTime = 0; // our own fall replaces the vanilla tip-over
        s.hasRedOverlay = t < 0.15f;
        s.walkAnimationSpeed = 0;
    }

    /** Whole-body motion, right after vanilla's body rotation (origin at the feet, y up, the face toward -z). */
    public static void body(LivingEntityRenderState s, PoseStack m, float t) {
        float jolt = (float) Math.sin(Math.PI * Math.min(1, t / 0.25f)) * 7;
        float hunch = 14 * smooth(0.15f, 0.9f, t);
        float kneel = smooth(1.1f, 1.45f, t);
        float f = fall(t);
        float bounce = t > 2.05f && t < 2.35f ? (float) Math.sin(Math.PI * (t - 2.05f) / 0.3f) * 6 : 0;
        if (s instanceof HumanoidRenderState) {
            float hip = s.boundingBoxHeight * 0.385f;
            float tilt = jolt - hunch * (1 - f) - 90 * f + bounce;
            m.translate(0, -hip * kneel + 0.13f * f, 0);
            m.translate(0, hip, 0);
            m.mulPose(Axis.XP.rotationDegrees(tilt)); // negative = top of the body toward the face
            m.translate(0, -hip, 0);
        } else {
            m.translate(0, -s.boundingBoxHeight * 0.2f * kneel, 0);
            m.mulPose(Axis.XP.rotationDegrees(jolt - hunch * (1 - f)));
            m.mulPose(Axis.ZP.rotationDegrees(90 * f - bounce));
        }
    }

    /** Arms and legs of humanoids, after the model set its own angles. */
    public static void limbs(HumanoidModel<?> model, float t) {
        float reach = smooth(0.15f, 0.8f, t) * (1 - smooth(1.2f, 1.6f, t));
        float relax = smooth(0.1f, 0.9f, t);
        float kneel = smooth(1.1f, 1.45f, t) * (1 - fall(t));
        set(model.rightArm, mix(model.rightArm.xRot * (1 - relax), -0.55f, reach), mix(model.rightArm.yRot * (1 - relax), -0.65f, reach), 0.05f);
        set(model.leftArm, model.leftArm.xRot * (1 - relax) + 0.1f * relax, model.leftArm.yRot * (1 - relax), -0.08f);
        float twitch = t > 2.45f && t < 2.75f ? (float) Math.sin(Math.PI * (t - 2.45f) / 0.15f) * 0.22f : 0;
        set(model.rightLeg, 1.45f * kneel + twitch, 0.05f, 0.04f);
        set(model.leftLeg, 1.45f * kneel - twitch * 0.6f, -0.05f, -0.04f);
    }

    /** Angel: arms spread, legs together, gently floating. */
    public static void angel(HumanoidModel<?> model, float t) {
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
        p.xRot = pitch;
        p.yRot = yaw;
        p.zRot = roll;
    }
}
