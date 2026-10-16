#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

layout(std140) uniform LarperFx {
    vec4 Tint;
    vec4 Grade;
    vec4 Lens;
    vec4 Extra;
};

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;

    if (Lens.z > 0.0) {
        vec2 c = uv * 2.0 - 1.0;
        c *= 1.0 + Lens.z * dot(c, c) * 0.25;
        uv = c * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
    }

    vec2 dir = uv - 0.5;
    float ca = Lens.x * 0.012;
    vec3 col;
    col.r = texture(InSampler, uv + dir * ca).r;
    col.g = texture(InSampler, uv).g;
    col.b = texture(InSampler, uv - dir * ca).b;

    if (Grade.w > 0.0) {
        vec2 px = 4.0 / InSize;
        vec3 glow = vec3(0.0);
        for (int i = 0; i < 12; i++) {
            float a = float(i) * 0.5236;
            vec2 o = vec2(cos(a), sin(a)) * (1.0 + float(i % 3));
            glow += max(texture(InSampler, uv + o * px).rgb - 0.55, 0.0);
        }
        col += glow / 12.0 * Grade.w * 2.5;
    }

    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, Grade.x);
    col = (col - 0.5) * Grade.y + 0.5 + Grade.z;

    col += vec3(0.10, 0.03, -0.09) * (luma - 0.5) * 2.0 * Extra.w;

    col = mix(col, col * Tint.rgb * 1.35 + Tint.rgb * 0.08, Tint.a);

    if (Extra.z > 1.0) col = floor(col * Extra.z + 0.5) / Extra.z;

    if (Lens.w > 0.0) col *= 1.0 - Lens.w * 0.35 * (0.5 + 0.5 * sin(uv.y * OutSize.y * 3.14159));

    float vig = smoothstep(0.95, 0.25, length(dir) * 1.25);
    col *= mix(1.0, vig, Lens.y);

    float t = GameTime * 24000.0;
    if (Extra.x > 0.0) col += (hash(uv * OutSize + fract(t * 0.37) * 100.0) - 0.5) * Extra.x * 0.12;
    if (Extra.y > 0.0) col *= 1.0 - Extra.y * 0.04 * (0.5 + 0.5 * sin(t * 3.7));

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
