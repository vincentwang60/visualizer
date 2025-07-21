#version 310 es
precision mediump float;

#define MAX_BUBBLES 10
#define PI 3.14159265

uniform vec2 u_resolution;
uniform float u_time;
uniform int u_numBubbles;
uniform vec3 u_bubbleColors[MAX_BUBBLES];
uniform float u_bubbleRadii[MAX_BUBBLES];
uniform vec2 u_bubblePositions[MAX_BUBBLES];
uniform float u_bubbleSeeds[MAX_BUBBLES];
uniform vec3 u_lightPos;

out vec4 fragColor;

vec3 hsv2rgb(in vec3 c){
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// returns a vec4 color (RGBA) with watercolor effect based on normalized position and time
vec4 watercolor(in vec2 _uv, in float _time, in float flow_speed, in vec2 u_resolution, in float seed){
    vec2 mask_uv = vec2(cos(_uv.x * 2.0 * PI), sin(_uv.x * 2.0 * PI)) + vec2(0, _uv.y * 2.0);
    vec2 color_uv = mask_uv;    
    float time_seed = _time * flow_speed + seed * 0.1;
    
    for (int i = 1; i < 4; i++) {
        float factor = 1. / float(i);
        float freq = float(i) * 1.8;
        mask_uv.x += factor * sin(freq * mask_uv.y + time_seed);
        mask_uv.y += factor * cos(freq * mask_uv.x + time_seed);
    }
    for (int i = 1; i < 5; i++) {
        float factor = 0.7 / float(i);
        float freq = float(i) * 2.;
        color_uv.x += factor * sin(freq * color_uv.y + time_seed);
        color_uv.y += factor * cos(freq * color_uv.x + time_seed);
    }
    
    vec3 color = hsv2rgb(vec3(fract((color_uv.x + color_uv.y) * 0.15 + _time * flow_speed * .1 + seed * 0.01), .4, 1.));
    float a = smoothstep(0.90, 1.0, sin(mask_uv.x * 1.2 + mask_uv.y * 1.7 + cos(mask_uv.x - mask_uv.y)));
    return vec4(color, a);
}

vec2 rotateGlobe(vec3 pos, float spin) {
    float cos_spin = cos(spin), sin_spin = sin(spin);
    vec3 rotated = vec3(pos.x * cos_spin + pos.z * sin_spin, pos.y, -pos.x * sin_spin + pos.z * cos_spin);
    return vec2(
        atan(rotated.x, rotated.z) * 0.159155 + 0.5,
        acos(clamp(rotated.y, -1., 1.)) * 0.318310
    );
}

vec3 bubbleShine(vec3 sphere_pos) {
    vec3 grad = normalize(sphere_pos);
    vec2 light_dir = normalize(vec2(0.5, 0.7));
    vec2 shadow_dir = normalize(vec2(-0.5, -0.7));
    float specular = pow(max(dot(grad.xy, light_dir), 0.0), 20.0);
    float shadow = pow(max(dot(grad.xy, shadow_dir), 0.0), 10.0) * 0.4;
    float shine = specular * 2.0 - shadow;
    return clamp(vec3(shine), 0.0, 1.0);
}

void main() {
    float flow_speed = 0.5, seed = 1237.0, spin_speed = .5;
    const float radius = 0.1;
    const float radius_sq = radius * radius;
    
    vec2 center = u_bubblePositions[0];
    vec2 pos = (gl_FragCoord.xy / u_resolution - center) * vec2(u_resolution.x / u_resolution.y, 1.0);
    float dist_sq = dot(pos, pos);
    if (dist_sq > radius_sq) { fragColor = vec4(0); return; }
     
    vec3 sphere_pos = vec3(pos, sqrt(radius_sq - dist_sq)) / radius;    
    vec3 shine = bubbleShine(sphere_pos);
    vec4 watercolor_result = watercolor(rotateGlobe(sphere_pos, u_time * spin_speed), u_time, flow_speed, u_resolution, seed);
    vec3 bubble_color = watercolor_result.rgb * watercolor_result.a;
    float edge_factor = sqrt(dist_sq) / radius;
    vec3 edge_color = watercolor_result.rgb * smoothstep(0.85, 1.0, edge_factor);

    vec3 color = bubble_color + edge_color + shine;
    fragColor = vec4(color, 1.0 - smoothstep(0.98, 1.0, edge_factor));
}