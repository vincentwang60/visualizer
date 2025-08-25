#version 310 es
precision mediump float;

#define MAX_BUBBLES 13
#define PI 3.14159265
#define TWO_PI 6.28318531
#define INV_2PI 0.159154943
#define OCT_MAX_SIZE 0.5

uniform vec2 u_resolution;
uniform highp float u_time;
uniform int u_numBubbles;
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

float g_time10, g_sinTime10, g_cosTime10;
vec2 g_normPos, g_uv;

void initGlobals() {
    g_time10 = mod(u_time * 10.0, TWO_PI);
    g_normPos = (gl_FragCoord.xy - u_resolution * 0.5) / u_resolution.y + 0.5;
    g_sinTime10 = sin(g_time10);
    g_cosTime10 = cos(g_time10);
    g_uv = gl_FragCoord.xy / u_resolution;
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.x + vec3(1.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0) - 1.0;
    return c.z * mix(vec3(1.0), clamp(p, 0.0, 1.0), c.y);
}

vec2 posFromBubble(int i, vec2 coord) {
    vec2 pos = u_bubblePositions[i];
    return vec2(coord.x  - pos.x, coord.y - pos.y);
}

vec2 rotateCoord(vec2 coord, float s, float c) {
    vec2 p = coord - 0.5;
    return vec2(p.x * c - p.y * s, p.x * s + p.y * c) + 0.5;
}

vec2 watercolorNoise(vec2 uv, int iters, float seed) {
    for (int i = 1; i < iters; i++) {
        float factor = 1.0 / float(i);
        float freq = float(i) * (7.0 - float(iters));
        float seedOffset = seed * 5.0;
        uv.x += factor * sin(freq * uv.y + g_time10) + seedOffset;
        uv.y += factor * cos(freq * uv.x + g_time10) + seedOffset;
    }
    return uv;
}

vec3 renderDots(vec2 coord) {
    float len = length(coord);
    float spiral = atan(coord.y, coord.x) * 5.0 + len * 25.0;
    float dotSize = (sin(spiral + g_time10 + u_smoothEnergy * 10.0) + 0.8) * 0.03;
    vec2 gridPos = fract(coord * (20.0 + u_smoothEnergy * 20.0)) - 0.5;
    float intensity = step(length(gridPos), dotSize);
    return intensity * hsv2rgb(vec3(fract(spiral * 0.1), 0.4, (0.5 + u_smoothEnergy * 3.0) * (len - OCT_MAX_SIZE)));
}

vec3 renderOctagon(vec2 coord) {
    float mask = 0.0;
    float baseAngle = atan(coord.x, coord.y);
    float r = PI / 4.0;
    float len = length(coord);
    for (int i = 0; i < 8; i++) {
        float fftSize = (u_fft[i] + u_fft[(i + 4) % 8]) * 0.05;
        float theta = baseAngle + PI * float(i) / 16.0;
        float edgeLen = OCT_MAX_SIZE - fftSize;
        
        float d1 = cos(floor(0.5 + theta / r) * r - theta) * len;
        mask = max(mask, smoothstep(edgeLen - 0.004, edgeLen + 0.004, d1) - smoothstep(edgeLen, edgeLen + 0.004, d1));
        
        float d2 = cos(floor(0.5 + (theta + PI / 8.0) / (2.0 * r)) * (2.0 * r) - (theta + PI / 8.0)) * len;
        float edgeLen2 = edgeLen / 1.31;
        mask = max(mask, smoothstep(edgeLen2 - 0.004, edgeLen2 + 0.004, d2) - smoothstep(edgeLen2, edgeLen2 + 0.004, d2));
        mask *= 1.0 + fftSize * 10.0;
    }
    return vec3(mask * 0.1);
}

vec3 renderBubbles(vec2 coord) {
    vec3 color = vec3(0.0), outlineColor = vec3(0.0);
    vec2 grad = vec2(0.0);  //todo style
    float outlineWeight = 0.0, d = 10000.0, totalInfluence = 0.0;
    for (int i = 0; i < MAX_BUBBLES; i++) {
        if (i >= u_numBubbles) break;

        // Calculate blending amount
        vec2 pos = posFromBubble(i, coord);
        float lenSq = dot(pos, pos), len = sqrt(lenSq);
        float rSq = u_bubbleRadii[i] * u_bubbleRadii[i];
        float di = len - u_bubbleRadii[i];
        if (di > 0.05) continue;
        float h = clamp(0.5 + 0.5 * (di - d) / 0.05, 0.0, 1.0);
        d = mix(di, d, h) - 0.05 * h * (1.0 - h);

        // Calculate watercolor mask
        int watercolorIters = 2 + 3 * int(step(float(i), 0.0));
        vec3 spherePos = vec3(pos, sqrt(rSq - lenSq)) / u_bubbleRadii[i];
        float angle = atan(spherePos.x + spherePos.z * u_bubbleSeeds[i] * 0.5, spherePos.z) + u_bubbleSeeds[i] * TWO_PI;
        vec2 spherePos2D = vec2(cos(angle + PI), sin(angle + PI) + spherePos.y + 1.0);
        vec2 maskUV = watercolorNoise(spherePos2D, watercolorIters, u_bubbleSeeds[i]);
        float maskSum = maskUV.x + maskUV.y;

        // Calculate outline
        vec3 outlineC = mix(u_bubbleColors[i] * 1.2, hsv2rgb(vec3(fract(maskSum * 0.15), 0.7, 1.0)), 0.5);
        float outline = 1.0 - di * 20.0;
        outlineColor += outline * outlineC;
        outlineWeight += outline;

        // Calculate interior
        if (di > 0.0) continue;
        vec3 interior = hsv2rgb(vec3(fract(maskSum), 1.0, 1.0));
        interior *= smoothstep(0.9, 1.0, sin(dot(maskUV, vec2(1.2 + u_bubbleSeeds[i] * 0.5, 1.7 - u_bubbleSeeds[i] * 0.3))));
        interior *= step(float(i), 0.0) + (step(u_bubbleRadii[0], length(posFromBubble(0, coord))));
        color += interior * clamp(-di / (u_bubbleRadii[i] + 1e-6), 0.0, 1.0);

        // Calculate specular
        float influence = 1.0 / (abs(di) + 0.01);
        grad += (pos / max(len, 0.001)) * influence;
        totalInfluence += influence;
    }
    outlineColor /= max(outlineWeight, 1e-5);
    float absD = abs(d);
    float innerMask = 0.5 * smoothstep(-0.03, 0.0, d) * step(d, 0.0);
    color = mix(outlineColor, color, smoothstep(0.0, 0.002, absD) * (1.0 - innerMask));
    if (absD < 0.03 && d < 0.0) {        
        grad = totalInfluence > 0.001 ? normalize(grad / totalInfluence) : vec2(0.0, 1.0);
        float specular = 1.0 + smoothstep(0.0, 0.5, max(dot(grad, normalize(u_lightPosition - gl_FragCoord.xy / u_resolution)) - 0.5, 0.0));
        color *= specular * specular;
    }
    return color * u_strobe;
}

void main() {
    initGlobals();
    vec3 bubbles = vec3(0.0);
    float s = sin(u_tilt + g_sinTime10), c = cos(u_tilt + g_sinTime10);
    if (u_chromaticAberration > 0.0) {
        vec2 caDir = vec2(g_cosTime10 * g_normPos.y, g_sinTime10 * g_normPos.x) * u_chromaticAberration;
        vec2 rUV = rotateCoord(g_normPos + caDir, s, c);
        vec2 gUV = rotateCoord(g_normPos, s, c);
        vec2 bUV = rotateCoord(g_normPos - caDir, s, c);

        bubbles = vec3(renderBubbles(rUV).r, renderBubbles(gUV).g, renderBubbles(bUV).b);
    } else {
        vec2 bubbleCoord = rotateCoord(g_normPos, s, c);    
        bubbles = renderBubbles(bubbleCoord);
    }
    vec2 dotsCoord = rotateCoord(g_normPos, sin(u_time + u_smoothEnergy), cos(u_time + u_smoothEnergy)) - 0.5;
    vec3 dots = renderDots(dotsCoord) * u_strobe;
    vec3 octagon = renderOctagon(dotsCoord);
    fragColor = vec4(bubbles.g > 0.0 ? bubbles : max(dots, octagon), 1.0);
}