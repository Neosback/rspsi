package com.rspsi.renderer.opengl.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GlShaderProgramTest {
    @Test
    void compileDiagnosticsNumberExpandedSourceLines() {
        String numbered = GlShaderProgram.numberedSource(
                "#version 330 core\nvoid main() {\n}\n");

        assertTrue(numbered.contains("   1 | #version 330 core"));
        assertTrue(numbered.contains("   2 | void main() {"));
        assertTrue(numbered.contains("   3 | }"));
    }
}
