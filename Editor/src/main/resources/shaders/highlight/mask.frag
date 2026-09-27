#version 330 core

// Writes the highlight channel of one draw into the outline mask:
// red = hovered, green = selected. Blending is additive, so a command that is
// both hovered and selected carries both bits.
uniform vec4 uMaskColor;

out vec4 outColor;

void main() {
    outColor = uMaskColor;
}
