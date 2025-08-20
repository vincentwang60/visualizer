#version 310 es
precision mediump float;

#define MAX_BUBBLES 16
#define PI 3.14159265
#define TWO_PI 6.28318531
#define INV_2PI 0.159155
#define OCT_MAX_SIZE 0.5

uniform vec2 u_resolution;
uniform highp float u_time;
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

// Precomputed constants
vec2 g_resRatio;
float g_aspectOffset;
vec2 g_resHalf;
float g_invResY;
float g_time10;
float g_sinTime10;
float g_cosTime10;
mat2 g_rotMatrix;
float g_angle;
float g_sinAngle;
float g_cosAngle;

void initGlobals() {
    g_resRatio = vec2(u_resolution.x / u_resolution.y, 1.0);
    g_aspectOffset = 0.5 * (1.0 - u_resolution.y / u_resolution.x);
    g_resHalf = u_resolution * 0.5;
    g_invResY = 1.0 / u_resolution.y;
    g_time10 = mod(u_time * 10.0, TWO_PI);
    g_sinTime10 = sin(g_time10);
    g_cosTime10 = cos(g_time10);
    float rotAngle = u_tilt * g_sinTime10 * 0.523599;
    float sinRot = sin(rotAngle), cosRot = cos(rotAngle);
    g_rotMatrix = mat2(cosRot, -sinRot, sinRot, cosRot);
    
    g_angle = mod(u_time * 2.5 + u_smoothEnergy, TWO_PI);
    g_sinAngle = sin(-g_angle);
    g_cosAngle = cos(g_angle);
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.x + vec3(1.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0) - 1.0;
    return c.z * mix(vec3(1.0), clamp(p, 0.0, 1.0), c.y);
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

vec2 applyBubbleRotation(vec2 coord) {
    return g_resHalf + g_rotMatrix * (coord - g_resHalf);
}

vec3 renderOctagon(vec2 pos) {
    float mask = 0.0;
    float baseAngle = atan(pos.x, pos.y) + g_angle;
    float r = PI / 4.0;
    float len = length(pos);
    
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
    return vec3(mask * 0.2);
}

vec3 renderDots(vec2 coord) {
    coord = vec2(coord.x * g_cosAngle - coord.y * g_sinAngle, coord.x * g_sinAngle + coord.y * g_cosAngle);
    float len = length(coord);
    float spiral = atan(coord.y, coord.x) * 5.0 + len * 25.0;
    float dotSize = (sin(spiral + g_time10 + u_smoothEnergy * 10.0) + 0.8) * 0.03;
    vec2 gridPos = fract(coord * (20.0 + u_smoothEnergy * 20.0)) - 0.5;
    float intensity = step(length(gridPos), dotSize);
    return intensity * hsv2rgb(vec3(fract(spiral * 0.1), 0.4, (0.5 + u_smoothEnergy * 3.0) * (len - OCT_MAX_SIZE)));
}

float smoothMin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

vec2 posFromCenter(int i, vec2 fragCoord) {
    vec2 center = vec2(u_bubblePositions[i].x * u_resolution.y / u_resolution.x + g_aspectOffset, u_bubblePositions[i].y);
    return (fragCoord / u_resolution - center) * g_resRatio;
}

vec3 renderBubbles(vec2 fragCoord) {
    float outlineWeight = 0.0, d = 10000.0;
    vec3 finalColor = vec3(0.0), finalOutlineColor = vec3(0.0);
    int numBubbles = int(u_numBubbles);

    for (int i = 0; i < MAX_BUBBLES; i++) {
        if (i >= numBubbles) break;
        
        vec2 pos = posFromCenter(i, fragCoord);
        float lenSq = dot(pos, pos);  // Avoid sqrt in length()
        float len = sqrt(lenSq);     // Only compute when needed
        float radius = u_bubbleRadii[i];
        float di = len - radius;
        
        if (di > 0.05) continue;
        
        d = smoothMin(d, di, 0.05);

        if (lenSq > radius * radius) {
            float invLen = 1.0 / len;
            pos *= radius * invLen;
        }
        
        float radiusSq = radius * radius;
        float zSq = radiusSq - lenSq;
        int watercolorIters = 2 + 3 * int(step(float(i), 0.0));
        if (zSq <= 0.0) {
            vec2 maskUV = pos * 2.0 + u_bubbleSeeds[i];
            maskUV = watercolorNoise(maskUV, watercolorIters, u_bubbleSeeds[i]);
            vec3 outlineColor = mix(u_bubbleColors[i] * 1.2, hsv2rgb(vec3(fract((maskUV.x + maskUV.y) * 0.15), 0.7, 1.0)), 0.5);
            float outline = 1.0 - di / 0.05;
            finalOutlineColor += outlineColor * outline;
            outlineWeight += outline;
            continue;
        }
        
        float z = sqrt(zSq);
        vec3 spherePos = vec3(pos, z) / radius;
        vec3 rotated = vec3(spherePos.x + spherePos.z * u_bubbleSeeds[i] * 0.5, spherePos.y, spherePos.z);
        vec2 globeUV = vec2(atan(rotated.x, rotated.z) * INV_2PI + 0.5, (rotated.y + 1.0) * 0.5);
        vec2 spherePos2D = vec2(cos(globeUV.x * TWO_PI), sin(globeUV.x * TWO_PI) + globeUV.y * 2.0);
        vec2 maskUV = watercolorNoise(spherePos2D, watercolorIters, u_bubbleSeeds[i]);
        float maskSum = maskUV.x + maskUV.y;
        vec3 outlineColor = mix(u_bubbleColors[i] * 1.2, hsv2rgb(vec3(fract(maskSum * 0.15), 0.7, 1.0)), 0.5);
        float outline = 1.0 - di / 0.05;
        
        finalOutlineColor += outlineColor * outline;
        outlineWeight += outline;
        
        vec3 interior = hsv2rgb(vec3(fract(maskSum), 1.0, 1.0));
        interior *= smoothstep(0.9, 1.0, sin(dot(maskUV, vec2(1.2 + u_bubbleSeeds[i] * 0.5, 1.7 - u_bubbleSeeds[i] * 0.3))));
        interior *= step(float(i), 0.0) + (step(u_bubbleRadii[0], length(posFromCenter(0, fragCoord))));
        finalColor += interior * clamp(-di / (radius + 1e-6), 0.0, 1.0);
    }

    finalOutlineColor /= max(outlineWeight, 1e-5);
    float absD = abs(d);
    float outline = 1.0 - smoothstep(0.0, 0.002, absD);
    float innerMask = 0.5 * smoothstep(-0.03, 0.0, d) * step(d, 0.0);
    vec3 color = mix(mix(finalColor, finalOutlineColor, outline), finalOutlineColor, innerMask);
    
    // Specular highlight - restore original logic
    if (absD < 0.03 && d < 0.0) {
        vec2 grad = vec2(0.0);
        float totalInfluence = 0.0;
        
        for (int i = 0; i < MAX_BUBBLES; i++) {
            if (i >= numBubbles) break;
            vec2 pos = posFromCenter(i, fragCoord);
            float len = length(pos);
            float di = len - u_bubbleRadii[i];
            if (di > 0.1) continue;
            float influence = 1.0 / (abs(di) + 0.01);
            grad += (pos / max(len, 0.001)) * influence;
            totalInfluence += influence;
        }
        
        grad = totalInfluence > 0.001 ? normalize(grad / totalInfluence) : vec2(0.0, 1.0);
        float specular = 1.0 + smoothstep(0.0, 0.5, max(dot(grad, normalize(u_lightPosition - fragCoord / u_resolution)) - 0.5, 0.0));
        color *= specular * specular;
    }
    
    return color * u_strobe;
}

vec3 renderWithCA(vec2 uv) {
    vec2 caDir = vec2(g_cosTime10, g_sinTime10);

    vec2 rUV = uv * (1.0 + caDir * u_chromaticAberration) * u_resolution;
    vec2 gUV = uv * u_resolution;
    vec2 bUV = uv * (1.0 - caDir * u_chromaticAberration) * u_resolution;

    vec2 rUVBubbles = applyBubbleRotation(rUV);
    vec2 gUVBubbles = applyBubbleRotation(gUV);
    vec2 bUVBubbles = applyBubbleRotation(bUV);

    vec2 centeredR = (rUV - g_resHalf) * g_invResY;
    vec2 centeredG = (gUV - g_resHalf) * g_invResY;
    vec2 centeredB = (bUV - g_resHalf) * g_invResY;

    vec3 bubbles = vec3(renderBubbles(rUVBubbles).r, renderBubbles(gUVBubbles).g, renderBubbles(bUVBubbles).b);
    vec3 octagons = vec3(renderOctagon(centeredR).r, renderOctagon(centeredG).g, renderOctagon(centeredB).b);
    vec3 dots = vec3(renderDots(centeredR).r, renderDots(centeredG).g, renderDots(centeredB).b);

    return max(bubbles, max(octagons, dots));
}

void main() {
    initGlobals();
    
    if (u_chromaticAberration > 0.0) {
        fragColor = vec4(renderWithCA(gl_FragCoord.xy / u_resolution), 1.0);
    } else {
        vec2 bubbleCoord = applyBubbleRotation(gl_FragCoord.xy);
        vec2 centeredCoord = (gl_FragCoord.xy - g_resHalf) * g_invResY;
        fragColor = vec4(max(renderBubbles(bubbleCoord), max(renderOctagon(centeredCoord), renderDots(centeredCoord))), 1.0);
    }
}