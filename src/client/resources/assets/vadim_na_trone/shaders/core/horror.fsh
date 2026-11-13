#version 330

// Стрипухоед: цветокор, виньетка, плёночное зерно, эффект погони, лучи фонарей в тумане.
// 1.21.11: свой RenderPipeline (HorrorPost), данные — UBO StripPost.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform StripPost {
    mat4 InvViewProj;
    vec4 FlashA[9];
    vec4 FlashB[9];
    vec4 Params;     // Darkness, Chase, Cozy, Time
    vec2 OutSize;
};

#define Darkness Params.x
#define Chase Params.y
#define Cozy Params.z
#define NightCam 0.0
#define Time Params.w
#define DiffuseSampler InSampler

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}


// --- Лучи фонариков в тумане (по глубине кадра) ---
vec3 strip_world(vec2 uv, float depth) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float strip_beam(vec4 A, vec4 B, vec3 s) {
    if (A.w <= 0.0) return 0.0;
    vec3 t = s - A.xyz;
    float d = length(t);
    vec3 L = t / max(d, 0.001);
    float c = dot(L, normalize(B.xyz));
    if (B.w > 1.5) return 0.0;   // мигалки катсцены — без луча в тумане
    if (B.w > 0.5) {
        // Фонарный столб: широкий мягкий конус вниз, короткий.
        float cone = smoothstep(0.45, 0.85, c);
        float att = 1.0 / (1.0 + 0.25 * d + 0.12 * d * d);
        float fade = 1.0 - smoothstep(4.0, 7.0, d);
        return cone * att * fade * A.w * 1.6;
    }
    float cone = smoothstep(0.84, 0.97, c);
    float att = 1.0 / (1.0 + 0.08 * d + 0.02 * d * d);
    float fade = 1.0 - smoothstep(26.0, 38.0, d);
    float self = length(A.xyz) < 1.2 ? 0.35 : 1.0;
    return cone * att * fade * self * A.w;
}

float lamp = 0.0;

float strip_scatter(vec2 uv, float jitter) {
    float depth = texture(DepthSampler, uv).r;
    vec3 p = strip_world(uv, depth);
    float dist = min(length(p), 38.0);
    vec3 rd = normalize(p);
    const int N = 12;
    float stepL = dist / float(N);
    float acc = 0.0;
    lamp = 0.0;
    for (int i = 0; i < N; i++) {
        vec3 s = rd * ((float(i) + jitter) * stepL);
        acc += strip_beam(FlashA[0], FlashB[0], s) + strip_beam(FlashA[1], FlashB[1], s) + strip_beam(FlashA[2], FlashB[2], s)
             + strip_beam(FlashA[3], FlashB[3], s) + strip_beam(FlashA[4], FlashB[4], s);
        lamp += strip_beam(FlashA[5], FlashB[5], s) + strip_beam(FlashA[6], FlashB[6], s) + strip_beam(FlashA[7], FlashB[7], s) + strip_beam(FlashA[8], FlashB[8], s);
    }
    lamp *= stepL;
    return acc * stepL;
}

void main() {
    vec2 uv = texCoord;
    vec2 c = uv - 0.5;
    float r = length(c);

    // Погоня: края «плывут» по цвету и пульсируют
    float pulse = 0.5 + 0.5 * sin(Time * 6.2831 * 1.4);
    float ab = Chase * (0.004 + 0.004 * pulse) * r * 2.0;
    vec3 col;
    col.r = texture(DiffuseSampler, uv + c * ab).r;
    col.g = texture(DiffuseSampler, uv).g;
    col.b = texture(DiffuseSampler, uv - c * ab).b;

    // Видимый луч фонаря в тумане
    float scatter = strip_scatter(uv, hash(uv * OutSize + fract(Time) * 31.0));
    col += vec3(1.0, 0.90, 0.72) * scatter * mix(0.025, 0.09, Darkness);
    // Тёплые лучи фонарных столбов (~3000 K)
    col += vec3(1.0, 0.62, 0.30) * lamp * 0.10;

    // Цветокор: приглушённые цвета, холодные тени
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, mix(mix(0.70, 0.55, Darkness), 1.05, Cozy));
    col *= mix(vec3(1.0), vec3(0.86, 0.94, 1.08), (1.0 - luma) * mix(0.7, 0.35, Cozy));
    col = (col - 0.5) * 1.06 + 0.5;
    col *= mix(mix(1.0, 0.85, Darkness), 1.0, Cozy);

    // Виньетка
    float vig = smoothstep(0.80, 0.20, r * mix(1.0, 1.2, Darkness) * (1.0 + 0.15 * Chase * pulse));
    col *= mix(1.0, vig, (0.50 + 0.25 * Darkness + 0.20 * Chase) * mix(1.0, 0.65, Cozy));

    // Красный оттенок по краям при погоне
    col = mix(col, col * vec3(1.15, 0.80, 0.80), Chase * smoothstep(0.25, 0.7, r) * pulse);

    // Плёночное зерно
    float g = hash(floor(uv * OutSize) + fract(Time * 13.0) * 97.0) - 0.5;
    col += g * (0.03 + 0.03 * Darkness);

    // Ночной режим камеры: зелёная зернистая картинка, тьма вытянута, полосы развёртки
    if (NightCam > 0.5) {
        vec3 raw = texture(DiffuseSampler, uv).rgb;
        float l = dot(raw, vec3(0.299, 0.587, 0.114));
        l = 1.0 - exp(-l * 7.0);
        float scan = 0.90 + 0.10 * sin(uv.y * OutSize.y * 1.4 + Time * 50.0);
        float gn = hash(floor(uv * OutSize * 0.5) + fract(Time * 17.0) * 61.0) - 0.5;
        l = clamp(l * scan + gn * 0.20 + 0.03, 0.0, 1.0);
        float v2 = smoothstep(0.85, 0.35, r);
        col = vec3(0.22, 1.0, 0.32) * l * mix(0.55, 1.0, v2);
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
