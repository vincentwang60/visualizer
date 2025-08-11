#version 310 es
precision mediump float;

#define MAX_BUBBLES 20
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
uniform vec2 u_lightPosition;

out vec4 fragColor;

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.x + vec3(1.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0) - 1.0;
    p = c.z * mix(vec3(1.0), clamp(p, 0.0, 1.0), c.y);
    return p;
}

float smoothMin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

vec2 watercolor_noise(vec2 _uv, float time, int num_iterations, float seed) {
    float safe_time = time + seed;
    for (int i = 1; i < num_iterations; i++) {
        float factor = 1. / float(i);
        float freq = float(i) * 2.5;
        float sin_val = sin(freq * _uv.y + safe_time);
        float cos_val = cos(freq * _uv.x + safe_time);
        _uv.x += factor * sin_val + seed * 10.;
        _uv.y += factor * cos_val + seed * 10.;
    }
    return _uv;
}

vec2 posFromCenter(int i, vec2 frag_coord, float aspect_offset, vec2 resolution_ratio) {
    float radius = u_bubbleRadii[i];
    vec2 center = vec2(
        u_bubblePositions[i].x * u_resolution.y / u_resolution.x + aspect_offset,
        u_bubblePositions[i].y
    );
    vec2 pos = (frag_coord / u_resolution - center) * resolution_ratio;
    return pos;
}

vec2 rotateGlobe(float seed, vec2 pos, float radius) {    
    float z = sqrt(max(radius * radius - dot(pos, pos), 0.0));
    vec3 sphere_pos = vec3(pos, z) / radius;
    vec3 rotated = vec3(sphere_pos.x + sphere_pos.z * seed, sphere_pos.y, sphere_pos.z);
    
    return vec2(
        atan(rotated.x, rotated.z) * INV_2PI + 0.5,
        (rotated.y + 1.0) * 0.5
    );
}

void main() {
    float edge_thickness = 0.002;
    float inner_fade_distance = 0.03;
    float blending_factor = 0.03;
    float flow_factor = 1.;
    
    float outline_weight = 0.0;
    vec3 final_color = vec3(0.0);
    vec2 frag_coord = gl_FragCoord.xy;
    float d = 10000.0;
    vec3 final_outline_color = vec3(0.0);
    int num_bubbles = int(u_numBubbles);
    vec2 resolution_ratio = vec2(u_resolution.x / u_resolution.y, 1.0);
    float aspect_offset = 0.5 * (1.0 - u_resolution.y / u_resolution.x);

    float animation_time = u_time * flow_factor;

    // Bubble 
    for (int i = 0; i < MAX_BUBBLES; i++) {
        if (i >= num_bubbles) break;
        vec3 target_color = u_bubbleColors[i];
        vec2 pos = posFromCenter(i, frag_coord, aspect_offset, resolution_ratio);

        float len = length(pos);
        float radius = u_bubbleRadii[i];
        float di = len - radius;
        if (di > blending_factor) continue;

        d = smoothMin(d, di, blending_factor);

        if (length(pos) > radius) {
            pos = normalize(pos) * radius;
        }
        vec2 globe_uv = rotateGlobe(u_bubbleSeeds[i], pos, radius);
        vec2 sphere_pos = vec2(cos(globe_uv.x * 2.0 * PI), sin(globe_uv.x * 2.0 * PI) + globe_uv.y * 2.0);
        vec2 mask_uv = watercolor_noise(sphere_pos, animation_time, 5, u_bubbleSeeds[i]);
        vec3 outline_color = hsv2rgb(vec3(fract((mask_uv.x + mask_uv.y) * 0.15), .7, 1.0));
        outline_color= mix(target_color * 1.2, outline_color, 0.3);
        float outline = 1.0 - di / blending_factor;

        final_outline_color += outline_color * outline;
        outline_weight += outline;

        vec3 interior = hsv2rgb(vec3(fract((mask_uv.x + mask_uv.y)), 1.0, 1.0));
        interior *= smoothstep(0.9, 1.0, sin(dot(mask_uv, vec2(1.2 + u_bubbleSeeds[i] * 0.5, 1.7 - u_bubbleSeeds[i] * 0.3))));
        float interior_strength = clamp(-di / (radius + 1e-6), 0.0, 1.0);
        final_color += interior * interior_strength;
    }

    final_outline_color = final_outline_color / max(outline_weight, 1e-5);
    float abs_d = abs(d);
    float outline = 1.0 - smoothstep(0.0, edge_thickness, abs_d);
    float inner_mask = .5 * smoothstep(-inner_fade_distance, 0.0, d) * step(d, 0.0);
    vec3 color = mix(final_color, final_outline_color, outline);
    color = mix(color, final_outline_color, inner_mask);    
    
    // Specular highlight
    if (abs_d < inner_fade_distance && d < 0.0) {
        vec2 grad = vec2(0.0);
        float total_influence = 0.0;
        
        for (int i = 0; i < MAX_BUBBLES; i++) {
            if (i >= num_bubbles) break;
            
            vec2 pos = posFromCenter(i, frag_coord, aspect_offset, resolution_ratio);
            float len = length(pos);
            float di = len - u_bubbleRadii[i];
            
            if (di > 0.1) continue;
            
            float influence = 1.0 / (abs(di) + 0.01);
            grad += (pos / max(len, 0.001)) * influence;
            total_influence += influence;
        }
        grad = total_influence > 0.001 ? normalize(grad / total_influence) : vec2(0.0, 1.0);
        vec2 light_dir = normalize(u_lightPosition - frag_coord / u_resolution);
        float specular = 1. + (smoothstep(0.0, 0.5, max(dot(grad, light_dir) - 0.5, 0.0)));
        color *= specular * specular;
    }
    fragColor = vec4(color, 1.0);
}