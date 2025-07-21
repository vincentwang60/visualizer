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

// Faster HSV to RGB conversion using fewer operations
vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.x + vec3(1.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0) - 1.0;
    return c.z * mix(vec3(1.0), clamp(p, 0.0, 1.0), c.y);
}

// Optimized watercolor with original loop structure but fewer calculations
vec4 watercolor(vec2 _uv, float _time, float flow_speed, vec2 u_resolution, float seed) {
    vec2 mask_uv = vec2(cos(_uv.x * 2.0 * PI), sin(_uv.x * 2.0 * PI)) + vec2(0.0, _uv.y * 2.0);
    vec2 color_uv = mask_uv;    
    float time_seed = _time * flow_speed + seed * 100.1;
    
    // Original loop structure but optimized calculations
    for (int i = 1; i < 4; i++) {
        float factor = 1.0 / float(i);
        float freq = float(i) * 1.8;
        float sin_val = sin(freq * mask_uv.y + time_seed);
        float cos_val = cos(freq * mask_uv.x + time_seed);
        mask_uv.x += factor * sin_val;
        mask_uv.y += factor * cos_val;
    }
    for (int i = 1; i < 5; i++) {
        float factor = 0.7 / float(i);
        float freq = float(i) * 2.0;
        float sin_val = sin(freq * color_uv.y + time_seed);
        float cos_val = cos(freq * color_uv.x + time_seed);
        color_uv.x += factor * sin_val;
        color_uv.y += factor * cos_val;
    }
    
    vec3 color = hsv2rgb(vec3(fract((color_uv.x + color_uv.y) * 0.15 + _time * flow_speed * 0.1 + seed), 0.4, 1.0));
    float a = smoothstep(0.90, 1.0, sin(mask_uv.x * 1.2 + mask_uv.y * 1.7 + cos(mask_uv.x - mask_uv.y)));
    return vec4(color, a);
}

// Pre-compute trig values once
vec2 rotateGlobe(vec3 pos, float spin) {
    float cos_spin = cos(spin);
    float sin_spin = sin(spin);
    vec3 rotated = vec3(
        pos.x * cos_spin + pos.z * sin_spin,
        pos.y,
        -pos.x * sin_spin + pos.z * cos_spin
    );
    return vec2(
        atan(rotated.x, rotated.z) * INV_2PI + 0.5,
        acos(clamp(rotated.y, -1.0, 1.0)) * INV_PI
    );
}

// Optimized bubble drawing without shine
vec4 bubbleDraw(int i, vec2 frag_coord) {
    vec2 inv_resolution = 1.0 / u_resolution;
    vec2 center = u_bubblePositions[i];
    float radius = u_bubbleRadii[i];
    float radius_sq = radius * radius;
    
    vec2 pos = (frag_coord * inv_resolution - center) * vec2(u_resolution.x / u_resolution.y, 1.0);
    float dist_sq = dot(pos, pos);
    
    if (dist_sq > radius_sq) return vec4(0.0); // Early exit
    
    float z = sqrt(radius_sq - dist_sq);
    vec3 sphere_pos = vec3(pos, z) / radius;
    
    float seed = u_bubbleSeeds[i];
    float spin = 0.5;
    float flow_speed = 0.5;
    
    vec2 globe_uv = rotateGlobe(sphere_pos, u_time * spin);
    vec4 watercolor_result = watercolor(globe_uv, u_time, flow_speed, u_resolution, seed);
    
    vec3 bubble_color = watercolor_result.rgb * watercolor_result.a;
    float edge_factor = sqrt(dist_sq) / radius;
    vec3 edge_color = watercolor_result.rgb * smoothstep(0.85, 1.0, edge_factor);
    
    vec3 color = bubble_color + edge_color;
    float alpha = 1.0 - smoothstep(0.98, 1.0, edge_factor);
    
    return vec4(color, alpha);
}

void main() {
    vec3 final_color = vec3(0.0);
    float final_alpha = 0.0;
    
    vec2 frag_coord = gl_FragCoord.xy;
    
    // Use dynamic loop bounds to avoid unnecessary iterations
    int num_bubbles = int(u_numBubbles);
    for (int i = 0; i < MAX_BUBBLES; i++) {
        if (i >= num_bubbles) break;
        
        vec4 bubble = bubbleDraw(i, frag_coord);
        
        final_color = mix(final_color, bubble.rgb, bubble.a);
        final_alpha = max(final_alpha, bubble.a);
    }
    
    fragColor = vec4(final_color, final_alpha);
}