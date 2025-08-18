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

vec3 renderSquare(vec2 pos) {
    float edge_thickness = 0.004;
    float mask = 0.0;
    float base_angle = atan(pos.x, pos.y);
    //float base_angle = atan(pos.x, pos.y) + u_time / 4.0 + u_smoothEnergy * 2.0;
    
    float r = PI / 4.0;
    for (int i = 0; i < 8; i++) {
        float fftSize = 0.05;
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

void main() {
    vec2 pos = (gl_FragCoord.xy - 0.5 * u_resolution) / u_resolution.y;
    fragColor = vec4(renderSquare(pos), 1.0);
}