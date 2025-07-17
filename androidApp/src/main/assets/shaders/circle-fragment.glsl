#version 310 es
precision mediump float;
uniform vec2 u_resolution;
uniform vec2 u_center;
uniform float u_radius;
uniform vec4 u_color;
out vec4 fragColor;
void main() {
    vec2 st = (gl_FragCoord.xy / u_resolution);
    vec2 center = u_center / u_resolution;
    float dist = distance(st, center);
    if (dist < u_radius / min(u_resolution.x, u_resolution.y)) {
        fragColor = u_color;
    } else {
        fragColor = vec4(0.1, 0.1, 0.8, 1.0); // blue background
    }
} 