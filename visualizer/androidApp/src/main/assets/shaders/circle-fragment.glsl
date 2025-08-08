#version 310 es
precision mediump float;
uniform vec2 u_resolution;
uniform vec2 u_center;
uniform float u_radius;
uniform vec4 u_color;
uniform float u_time;
uniform float u_fftTest[8];
uniform float u_spikeIntensity[8]; // Active spike count per band (0=white, 1=light pink, 2=light red, 3+=red)
out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / u_resolution.xy;
    
    // Create 8 vertical bars across the screen
    float barWidth = 1.0 / 8.0;
    int barIndex = int(floor(uv.x / barWidth));
    float barX = (uv.x - float(barIndex) * barWidth) / barWidth; // 0-1 within bar
    
    // Get FFT value and spike count for this bar
    float fftValue = u_fftTest[barIndex];
    float spikeCount = u_spikeIntensity[barIndex];
    
    // Bar height based on FFT value (from bottom of screen)
    float barHeight = fftValue * 0.8 + 0.05; // Min height of 0.05, max of 0.85
    
    // Simple bar mask - just check if we're within the bar area
    bool inBar = (barX > 0.1 && barX < 0.9) && (uv.y < barHeight);
    
    // Background color (dark)
    vec3 backgroundColor = vec3(0.1, 0.1, 0.1);
    
    // Simple color scheme based on spike count
    vec3 barColor;
    if (spikeCount < 0.5) {
        // 0 spikes = white
        barColor = vec3(1.0, 1.0, 1.0);
    } else if (spikeCount < 1.5) {
        // 1 spike = light pink
        barColor = vec3(1.0, 0.8, 0.9);
    } else if (spikeCount < 2.5) {
        // 2 spikes = light red
        barColor = vec3(1.0, 0.6, 0.6);
    } else {
        // 3+ spikes = red
        barColor = vec3(1.0, 0.3, 0.3);
    }
    
    // Simple color selection
    vec3 finalColor = inBar ? barColor : backgroundColor;
    
    fragColor = vec4(finalColor, 1.0);
} 