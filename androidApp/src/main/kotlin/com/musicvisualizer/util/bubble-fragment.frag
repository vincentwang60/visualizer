#version 310 es
precision mediump float;

#define MAX_BUBBLES 10

uniform vec2 u_resolution;
uniform float u_time;
uniform int u_numBubbles;
uniform float u_edgeThickness;
uniform vec3 u_bubbleColors[MAX_BUBBLES];
uniform float u_bubbleRadii[MAX_BUBBLES];
uniform vec2 u_bubblePositions[MAX_BUBBLES];

out vec4 fragColor;

vec3 hsv2rgb(in vec3 c){
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// returns 2 floats varying in a watercolor effect with position and time
vec2 watercolor(in vec2 _st, in float _time){
    float speed = .1;
    float scale = 0.0025;
    _st *= scale;    
    // from https://www.shadertoy.com/view/lsyfWD
    for (int i = 1; i < 9; i++) {
        _st.x += 0.3 / float(i) * sin(float(i) * 3.0 * _st.y + _time * speed);
        _st.y += 0.3 / float(i) * cos(float(i) * 3.0 * _st.x + _time * speed);
    }
    return _st;
}

float circle(in vec2 _st, in float _radius, in vec2 _center){
    vec2 dist = _st - _center;
    float distance = length(dist);
    return 1.0 - smoothstep(_radius - (_radius * 0.01), _radius + (_radius * 0.01), distance);
}

void main() {
    vec2 st = gl_FragCoord.xy;
    vec2 uv = (st / u_resolution) * 2.0 - 1.0;
    vec2 p = watercolor(st, u_time);
    vec3 color = hsv2rgb(vec3(fract((p.x + p.y) * 0.15 + u_time * .1), .4, 1.0));
    float a = sin(p.x * 1.2 + p.y * 1.7 + cos(p.x - p.y));
    float mask = smoothstep(0.8, 1.0, a);
    fragColor = vec4(color, mask);
}