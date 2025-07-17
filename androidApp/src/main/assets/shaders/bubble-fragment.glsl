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

vec2 watercolor(in vec2 _st, in float _time){
    float speed = 10.0;
    float scale = 0.002;

    for (int i = 1; i < 10; i++) {
        _st.x += 0.3 / float(i) * sin(float(i) * 3.0 * _st.y + _time * speed) + 1. / 1000.0;
        _st.y += 0.3 / float(i) * cos(float(i) * 3.0 * _st.x + _time * speed) + 2. / 1000.0;
    }

    float x = smoothstep(0.95, 1.0, cos(_st.x + _st.y + 1.0) * 0.5 + 0.5);
    float y = x * (sin(_st.x * _st.y) * 0.5 + 0.5);
    return vec2(x, y);
}

float circle(in vec2 _st, in float _radius, in vec2 _center){
    vec2 dist = _st - _center;
    float distance = length(dist);
    return 1.0 - smoothstep(_radius - (_radius * 0.01), _radius + (_radius * 0.01), distance);
}

void main() {
    vec2 st = gl_FragCoord.xy; // in pixels
    vec3 bg_color = vec3(0.8, 0.0, 0.2); // background: black
    vec3 bubble_color = vec3(0.0);
    float in_bubble = 0.0;
    
    vec2 pct = watercolor(st, u_time);
    float r=cos(pct.x+pct.y+1.)*.5+.5;
    float g=sin(pct.x+pct.y+1.)*.5+.5;
    float b=(sin(pct.x+pct.y)+cos(pct.x+pct.y))*.3+.5;
    fragColor = vec4(vec3(r, g, b), 1.0);   
    // for (int i = 0; i < MAX_BUBBLES; ++i) {
    //     if (i >= u_numBubbles) break;
    //     float circle = circle(st, u_bubbleRadii[i], u_bubblePositions[i]);
    //     bubble_color = mix(bubble_color, u_bubbleColors[i], step(0.001, circle) * 0.5);
    //     in_bubble = max(in_bubble, circle);
    // }

    // fragColor = vec4(mix(bg_color, bubble_color, step(0.001, in_bubble)), 1.0);
}