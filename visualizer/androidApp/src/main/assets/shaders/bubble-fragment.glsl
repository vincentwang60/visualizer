#version 310 es
precision mediump float;

#define MAX_BUBBLES 16
#define PI 3.14159265
#define INV_PI 0.318310
#define INV_2PI 0.159155
#define OCT_MAX_SIZE 0.5

uniform vec2 u_resolution;
uniform float u_time;
uniform float u_numBubbles;
uniform vec3 u_bubbleColors[MAX_BUBBLES];
uniform float u_bubbleRadii[MAX_BUBBLES];
uniform vec2 u_bubblePositions[MAX_BUBBLES];
uniform float u_bubbleSeeds[MAX_BUBBLES];
uniform vec2 u_lightPosition;
uniform float u_tilt;
uniform float u_chromaticAberration;
uniform float u_strobe;
uniform float u_fft[8];
uniform float u_smoothEnergy;

out vec4 fragColor;

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.x + vec3(1.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0) - 1.0;
    return c.z * mix(vec3(1.0), clamp(p, 0.0, 1.0), c.y);
}

float smoothMin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

vec2 watercolor_noise(vec2 _uv, int num_iterations, float seed) {
    float time_seed = u_time * 10.0;
    for (int i = 1; i < num_iterations; i++) {
        float factor = 1.0 / float(i);
        float freq = float(i) * 2.0;
        _uv.x += factor * sin(freq * _uv.y + time_seed) + seed * 5.0;
        _uv.y += factor * cos(freq * _uv.x + time_seed) + seed * 5.0;
    }
    return _uv;
}

vec2 posFromCenter(int i, vec2 frag_coord, float aspect_offset, vec2 resolution_ratio) {
    vec2 center = vec2(u_bubblePositions[i].x * u_resolution.y / u_resolution.x + aspect_offset, u_bubblePositions[i].y);
    return (frag_coord / u_resolution - center) * resolution_ratio;
}

vec2 rotateGlobe(float seed, vec2 pos, float radius) {    
    float z = sqrt(max(radius * radius - dot(pos, pos), 0.0));
    vec3 sphere_pos = vec3(pos, z) / radius;
    vec3 rotated = vec3(sphere_pos.x + sphere_pos.z * seed * 0.5, sphere_pos.y, sphere_pos.z);
    
    return vec2(atan(rotated.x, rotated.z) * INV_2PI + 0.5, (rotated.y + 1.0) * 0.5);
}

vec2 applyRotate(vec2 fragCoord) {
    vec2 offsetFromCenter = fragCoord - u_resolution * 0.5;
    float rotationAngle = u_tilt * sin(u_time * 10.0) * radians(30.0);
    
    mat2 rotMatrix = mat2(cos(rotationAngle), -sin(rotationAngle), sin(rotationAngle), cos(rotationAngle));
    return u_resolution * 0.5 + rotMatrix * offsetFromCenter;
}

vec3 renderSquare(vec2 pos) {
    float edge_thickness = 0.004;
    float mask = 0.0;
    float base_angle = atan(pos.x, pos.y) + u_time * 2.5 + u_smoothEnergy * 2.0;
    
    float r = PI / 4.0;
    for (int i = 0; i < 8; i++) {
        float fftSize = (u_fft[i] + u_fft[(i + 4) % 8]) * 0.05;
        float edge_length = OCT_MAX_SIZE;
        float theta = base_angle + PI * float(i) / 16.0;
        float d = cos(floor(0.5 + theta / r) * r - theta) * length(pos);
        mask = max(mask, smoothstep(edge_length - edge_thickness, edge_length, d) - smoothstep(edge_length, edge_length + edge_thickness, d));
        d = cos(floor(0.5 + (theta + PI / 8.0) / (2.0 * r)) * (2.0 * r) - (theta + PI / 8.0)) * length(pos);        
        mask = max(mask, smoothstep(edge_length / 1.31 - edge_thickness, edge_length / 1.31, d) - smoothstep(edge_length / 1.31, edge_length / 1.31 + edge_thickness, d));
        mask *= 1.0 + fftSize * 5.0;
    }
    return vec3(mask * 0.2);
}

vec3 renderDots(vec2 frag_coord) {
    float angle = u_time * 2.5 + u_smoothEnergy * 1.0;
    float s = sin(-angle);
    float c = cos(angle);
    frag_coord = vec2(frag_coord.x * c - frag_coord.y * s, frag_coord.x * s + frag_coord.y * c);
    float length = length(frag_coord);

    float spiral = atan(frag_coord.y, frag_coord.x) * 5.0 + length(frag_coord) * 25.0;
    float dot_size = (sin(spiral + u_time * 20.0 + u_smoothEnergy * 10.0) + 0.8) * 0.03;
    vec2 grid_pos = fract(frag_coord * (20.0 + u_smoothEnergy * 20.0)) - 0.5;
    float dist = length(grid_pos);
    
    float intensity = step(dist, dot_size);
    float hue = fract(spiral * 0.1);
    vec3 color = hsv2rgb(vec3(hue, 0.4, (0.5 + u_smoothEnergy * 3.0) * (length - OCT_MAX_SIZE)));
    
    return step(dist, dot_size) * color;
}

vec3 renderBubbles(vec2 frag_coord) {
    const float edge_thickness = 0.002;
    const float inner_fade_distance = 0.03;
    const float blending_factor = 0.05;
    
    float outline_weight = 0.0;
    vec3 final_color = vec3(0.0);
    float d = 10000.0;
    vec3 final_outline_color = vec3(0.0);
    int num_bubbles = int(u_numBubbles);
    vec2 resolution_ratio = vec2(u_resolution.x / u_resolution.y, 1.0);
    float aspect_offset = 0.5 * (1.0 - u_resolution.y / u_resolution.x);

    for (int i = 0; i < MAX_BUBBLES; i++) {
        if (i >= num_bubbles) break;
        
        vec3 target_color = u_bubbleColors[i];
        vec2 pos = posFromCenter(i, frag_coord, aspect_offset, resolution_ratio);
        float len = length(pos);
        float radius = u_bubbleRadii[i];
        float di = len - radius;
        
        if (di > blending_factor) continue;
        
        d = smoothMin(d, di, blending_factor);
        
        if (len > radius) pos = normalize(pos) * radius;
        
        vec2 globe_uv = rotateGlobe(u_bubbleSeeds[i], pos, radius);
        vec2 sphere_pos = vec2(cos(globe_uv.x * 2.0 * PI), sin(globe_uv.x * 2.0 * PI) + globe_uv.y * 2.0);
        vec2 mask_uv = watercolor_noise(sphere_pos, 5, u_bubbleSeeds[i]);
        
        vec3 outline_color = hsv2rgb(vec3(fract((mask_uv.x + mask_uv.y) * 0.15), 0.7, 1.0));
        outline_color = mix(target_color * 1.2, outline_color, 0.5);
        float outline = 1.0 - di / blending_factor;
        
        final_outline_color += outline_color * outline;
        outline_weight += outline;
        
        vec3 interior = hsv2rgb(vec3(fract(mask_uv.x + mask_uv.y), 1.0, 1.0));
        interior *= smoothstep(0.9, 1.0, sin(dot(mask_uv, vec2(1.2 + u_bubbleSeeds[i] * 0.5, 1.7 - u_bubbleSeeds[i] * 0.3))));
        float interior_strength = clamp(-di / (radius + 1e-6), 0.0, 1.0);
        final_color += interior * interior_strength;
    }

    final_outline_color /= max(outline_weight, 1e-5);
    float abs_d = abs(d);
    float outline = 1.0 - smoothstep(0.0, edge_thickness, abs_d);
    float inner_mask = 0.5 * smoothstep(-inner_fade_distance, 0.0, d) * step(d, 0.0);
    vec3 color = mix(mix(final_color, final_outline_color, outline), final_outline_color, inner_mask);
    
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
        float specular = 1.0 + smoothstep(0.0, 0.5, max(dot(grad, light_dir) - 0.5, 0.0));
        color *= specular * specular;
    }
    
    return color * u_strobe;
}

vec3 renderChromaticAberration(vec2 uv) {
    vec2 caDirection = vec2(cos(u_time * 10.0), sin(u_time * 10.0));

    vec2 redUV = uv * (1.0 + caDirection * u_chromaticAberration);
    vec2 greenUV = uv;
    vec2 blueUV = uv * (1.0 - caDirection * u_chromaticAberration);

    vec3 bubbleColor = vec3(
        renderBubbles(applyRotate(redUV * u_resolution)).r,
        renderBubbles(applyRotate(greenUV * u_resolution)).g,
        renderBubbles(applyRotate(blueUV * u_resolution)).b
    );
    
    vec3 squareColor = vec3(
        renderSquare((redUV * u_resolution - 0.5 * u_resolution) / u_resolution.y).r,
        renderSquare((greenUV * u_resolution - 0.5 * u_resolution) / u_resolution.y).g,
        renderSquare((blueUV * u_resolution - 0.5 * u_resolution) / u_resolution.y).b
    );

    vec3 dotsColor = vec3(
        renderDots((redUV * u_resolution - 0.5 * u_resolution) / u_resolution.y).r,
        renderDots((greenUV * u_resolution - 0.5 * u_resolution) / u_resolution.y).g,
        renderDots((blueUV * u_resolution - 0.5 * u_resolution) / u_resolution.y).b
    );

    return max(bubbleColor, max(squareColor, dotsColor));
}

void main() {
    if (u_chromaticAberration > 10.0) {
        fragColor = vec4(renderChromaticAberration(gl_FragCoord.xy / u_resolution), 1.0);
    } else {
        vec2 frag_coord = applyRotate(gl_FragCoord.xy);
        fragColor = vec4(max(
            renderBubbles(frag_coord), max(
            renderSquare( (gl_FragCoord.xy - 0.5 * u_resolution) / u_resolution.y), 
            renderDots((gl_FragCoord.xy - 0.5 * u_resolution) / u_resolution.y))), 1.0
        );
    }
}