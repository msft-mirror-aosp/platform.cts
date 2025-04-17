#version 450

layout(location = 0) out vec4 outColor;

void main() {
    vec2 pixelCoord = gl_FragCoord.xy;

    vec2 normalizedCoord = mod(pixelCoord, 100.0) / 100.0;

    float r = normalizedCoord.x;
    float g = normalizedCoord.y;
    float b = (r + g) * 0.5;

    outColor = vec4(r, g, b, 1.0);
}