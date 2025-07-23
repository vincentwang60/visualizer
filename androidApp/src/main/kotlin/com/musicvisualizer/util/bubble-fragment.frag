#version 310 es
precision mediump float;

#define MAX_BUBBLES 10
#define PI 3.14159265
#define INV_PI 0.318310
#define INV_2PI 0.159155

uniform vec2 u_resolution;
uniform float u_time;
uniform float u_numBubbles;
uniform vec3 u_bubbleColors[MAX_BUBBLES];
uniform float u_bubbleRadii[MAX_BUBBLES];
uniform vec2 u_bubblePositions[MAX_BUBBLES];
uniform float u_bubbleSeeds[MAX_BUBBLES];
uniform vec3 u_lightPos;

out vec4 fragColor;

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.x + vec3(1.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0) - 1.0;
    return c.z * mix(vec3(1.0), clamp(p, 0.0, 1.0), c.y);
}

float smoothMin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

vec2 watercolor_noise(vec2 _uv, float time_seed, int num_iterations) {
    for (int i = 1; i < num_iterations; i++) {
        float factor = 1.0 / float(i);
        float freq = float(i) * 1.8;
        float sin_val = sin(freq * _uv.y + time_seed);
        float cos_val = cos(freq * _uv.x + time_seed);
        _uv.x += factor * sin_val;
        _uv.y += factor * cos_val;
    }
    return _uv;
}

vec2 posFromCenter(int i, vec2 frag_coord) {
    float radius = u_bubbleRadii[i];
    vec2 center = vec2(
        u_bubblePositions[i].x * u_resolution.y / u_resolution.x + 0.5 * (1.0 - u_resolution.y / u_resolution.x),
        u_bubblePositions[i].y
    );
    vec2 pos = (frag_coord / u_resolution - center) * vec2(u_resolution.x / u_resolution.y, 1.0);
    return pos;
}

vec2 rotateGlobe(float time_seed, vec2 pos, float radius) {    
    float z = sqrt(radius * radius - dot(pos, pos));
    vec3 sphere_pos = vec3(pos, z) / radius;
    
    float cos_spin = cos(time_seed);
    float sin_spin = sin(time_seed);
    vec3 rotated = vec3(
        sphere_pos.x * cos_spin + sphere_pos.z * sin_spin,
        sphere_pos.y,
        -sphere_pos.x * sin_spin + sphere_pos.z * cos_spin
    );
    return vec2(
        atan(rotated.x, rotated.z) * INV_2PI + 0.5,
        acos(clamp(rotated.y, -1.0, 1.0)) * INV_PI
    );
}

void main() {
    vec3 final_color = vec3(0.0);
    vec2 frag_coord = gl_FragCoord.xy;
    float field = 0.0;
    float d = 10000.0;
    vec3 final_outline_color = vec3(0.0);
    float edge_thickness = 0.005;
    
    int num_bubbles = int(u_numBubbles);
    for (int i = 0; i < MAX_BUBBLES; i++) {
        if (i >= num_bubbles) break;
        float time_seed = u_time * 0.5 + u_bubbleSeeds[i] * 1000.;
        vec2 pos = posFromCenter(i, frag_coord);

        float strength = u_bubbleRadii[i] / length(pos);
        field += strength;

        float di = length(pos) - u_bubbleRadii[i];
        d = smoothMin(d, di, 0.02);

        vec2 globe_uv = rotateGlobe(time_seed, pos, u_bubbleRadii[i]);
        vec2 sphere_pos = vec2(cos(globe_uv.x * 2.0 * PI), sin(globe_uv.x * 2.0 * PI)) + vec2(0.0, globe_uv.y * 2.0);
        vec2 mask_uv = watercolor_noise(sphere_pos, time_seed, 3);
        vec3 outline_color = hsv2rgb(vec3(fract((mask_uv.x + mask_uv.y) * 0.15 + u_time * 0.5 + time_seed), 0.6, 1.0));
        final_outline_color = max(final_outline_color, outline_color);

        if (dot(pos, pos) > u_bubbleRadii[i] * u_bubbleRadii[i]) continue;
        vec2 color_uv = watercolor_noise(mask_uv, time_seed, 5);
        vec3 interior = hsv2rgb(vec3(fract((color_uv.x + color_uv.y) * 0.15 + u_time * 0.5 + time_seed), 0.4, 1.0));
        interior *= smoothstep(0.90, 1.0, sin(mask_uv.x * 1.2 + mask_uv.y * 1.7 + cos(mask_uv.x - mask_uv.y)));
        final_color += interior * strength;
    }
    final_color = final_color / max(field, 1e-5);
    float outline = 1.0 - smoothstep(0.0, edge_thickness, abs(d));
    vec3 color = mix(final_color, final_outline_color, outline);
    
    fragColor = vec4(color, 1.0);
}