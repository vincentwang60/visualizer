#version 310 es
precision mediump float;

#define MAX_BUBBLES 16
#define PI 3.14159265
#define INV_PI 0.318310
#define INV_2PI 0.159155

uniform vec2 u_resolution;
uniform float u_time;

out vec4 fragColor;

float g_time10;

void initGlobals() {
    g_time10 = mod(u_time * 10.0, 6.28318531);
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
        uv.x += factor * sin(freq * uv.y + u_time) + seedOffset;
        uv.y += factor * cos(freq * uv.x + u_time) + seedOffset;
    }
    return uv;
}

vec2 friggeNoise(vec2 uv, int iters) {
    float len = length(uv);
    float atan = atan(uv.x, uv.y);
    for (int i = 1; i < iters; i++) {
        float factor = 1.0 / float(i);
        float freq = float(i) * 2.;
        float ripple1 = sin(freq * uv.y + u_time);
        float ripple2 = sin(freq * uv.x + u_time);
        float ripple3 = 0.0;
        uv.x += factor * (ripple2 + ripple3);
        uv.y += factor * (ripple1 + ripple3);
        len += factor * ripple1;
    }
    return uv;
}

void main() {
    initGlobals();
    vec2 pos = (gl_FragCoord.xy - 0.5 * u_resolution) / u_resolution.y;
    vec2 maskUV = friggeNoise(pos, 5);
    float r = cos(maskUV.x + maskUV.y + 1.0) * .5 + .5;
    float g = sin(maskUV.x + maskUV.y + 1.0) * .5 + .5;
    float b = (sin(maskUV.x + maskUV.y) + cos(maskUV.x + maskUV.y)) * .3 + .5;
    fragColor = vec4(r, g, b, 1.0);
}