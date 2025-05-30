#version 450

const vec2 positions[3] = vec2[](
    vec2(0.0, 0.577),
    vec2(-0.5, -0.289),
    vec2(0.5, -0.289)
);

const float smallAngle = 0.15;

void main() {
    int triangleIndex = gl_VertexIndex / 3;
    int vertexInTriangle = gl_VertexIndex % 3;

    float rotationAngle = float(triangleIndex) * smallAngle;
    vec2 basePos = positions[vertexInTriangle];
    float cosAngle = cos(rotationAngle);
    float sinAngle = sin(rotationAngle);

    vec2 rotatedPos = vec2(
        basePos.x * cosAngle - basePos.y * sinAngle,
        basePos.x * sinAngle + basePos.y * cosAngle
    );

    gl_Position = vec4(rotatedPos, 0.0, 1.0);
}