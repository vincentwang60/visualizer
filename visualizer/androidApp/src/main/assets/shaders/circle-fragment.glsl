#version 310 es
precision mediump float;
uniform vec2 u_resolution;
uniform vec2 u_center;
uniform float u_radius;
uniform vec4 u_color;
uniform float u_time;
uniform float u_fftTest[8];
out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / u_resolution.xy;
    
    // Create 8 vertical bars across the screen
    float barWidth = 1.0 / 8.0;
    int barIndex = int(floor(uv.x / barWidth));
    float barX = (uv.x - float(barIndex) * barWidth) / barWidth; // 0-1 within bar
    
    // Get FFT value for this bar
    float fftValue = u_fftTest[barIndex];
    
    // Bar height based on FFT value (from bottom of screen)
    float barHeight = fftValue * 0.8 + 0.05; // Min height of 0.05, max of 0.85
    
    // Simple bar mask - just check if we're within the bar area
    bool inBar = (barX > 0.1 && barX < 0.9) && (uv.y < barHeight);
    
    // Background color (dark)
    vec3 backgroundColor = vec3(0.1, 0.1, 0.1);
    
    // White bars
    vec3 barColor = vec3(1.0, 1.0, 1.0);
    
    // Simple color selection
    vec3 finalColor = inBar ? barColor : backgroundColor;
    
    fragColor = vec4(finalColor, 1.0);
} 