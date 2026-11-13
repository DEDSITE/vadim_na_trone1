#version 330

layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
    // --- Стрипухоед: фонарики (0–4 игроки, 5–8 фонари/катсцена). Пишет FogRendererMixin. ---
    vec4 StripFlashA[9];
    vec4 StripFlashB[9];
};

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }

    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

float total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd) {
    return max(linear_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd), linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd));
}

vec4 apply_fog(vec4 inColor, float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd, vec4 fogColor) {
    float fogValue = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, environmentalStart, environmantalEnd, renderDistanceStart, renderDistanceEnd);
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

float fog_spherical_distance(vec3 pos) {
    return length(pos);
}

float fog_cylindrical_distance(vec3 pos) {
    float distXZ = length(pos.xz);
    float distY = abs(pos.y);
    return max(distXZ, distY);
}

// --- Стрипухоед: конус света фонариков ---
vec3 strip_flash(vec4 A, vec4 B, vec3 pos, vec3 normal) {
    if (A.w <= 0.0) return vec3(0.0);
    vec3 toFrag = pos - A.xyz;
    float d = length(toFrag);
    vec3 L = toFrag / max(d, 0.001);
    float c = dot(L, normalize(B.xyz));
    float ndl = 1.0;
    if (dot(normal, normal) > 0.01) {
        ndl = clamp(dot(normalize(normal), -L), 0.0, 1.0) * 0.75 + 0.25;
    }
    if (B.w > 1.5) {
        // Катсцена: мигалки (2 — красная, 3 — синяя) — цветной точечный свет без конуса.
        if (d > 18.0) return vec3(0.0);
        float attenC = 1.0 / (1.0 + 0.12 * d + 0.06 * d * d);
        float fadeC = 1.0 - smoothstep(12.0, 18.0, d);
        vec3 tintC = B.w > 2.5 ? vec3(0.18, 0.32, 1.0) : vec3(1.0, 0.10, 0.06);
        return tintC * attenC * fadeC * ndl * A.w * 2.2;
    }
    if (B.w > 0.5) {
        // Уличный фонарь: широкий тёплый конус вниз (~3000 K), пятно света на земле.
        if (d > 16.0) return vec3(0.0);
        float cone = smoothstep(0.30, 0.78, c);
        float atten = 1.0 / (1.0 + 0.10 * d + 0.05 * d * d);
        float fade = 1.0 - smoothstep(10.0, 16.0, d);
        return vec3(1.0, 0.66, 0.36) * cone * atten * fade * ndl * A.w * 1.5;
    }
    if (d > 36.0) return vec3(0.0);
    float cone = smoothstep(0.84, 0.97, c);
    float hot = smoothstep(0.965, 0.995, c) * 0.6;
    float atten = 1.0 / (1.0 + 0.04 * d + 0.007 * d * d);
    float fade = 1.0 - smoothstep(24.0, 36.0, d);
    return vec3(1.0, 0.90, 0.72) * (cone + hot) * atten * fade * ndl * A.w * 2.4;
}

vec3 strip_flash_all(vec3 pos, vec3 normal) {
    vec3 s = vec3(0.0);
    for (int i = 0; i < 9; i++) s += strip_flash(StripFlashA[i], StripFlashB[i], pos, normal);
    return s;
}
