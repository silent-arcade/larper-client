package com.example.addon.aura;

import net.minecraft.client.render.model.BakedQuad;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Colors the first-person held item. Item tints only apply to quads that have a tint index, so while a color is
 * set, quads are swapped for copies with tint index 0 (cached per model) and the color is passed as tint 0.
 * Render thread only.
 */
public final class HandTint {
    /** ARGB color for the item being rendered right now, or 0 for none. */
    public static int itemColor;

    private static final Map<List<BakedQuad>, List<BakedQuad>> TINTED = new IdentityHashMap<>();

    private HandTint() {
    }

    public static List<BakedQuad> tintable(List<BakedQuad> quads) {
        List<BakedQuad> cached = TINTED.get(quads);
        if (cached != null) return cached;
        if (TINTED.size() > 1024) TINTED.clear(); // models change on resource reload; don't grow forever
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            out.add(new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(),
                q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(), 0, q.face(), q.sprite(), q.shade(), q.lightEmission()));
        }
        TINTED.put(quads, out);
        return out;
    }
}
