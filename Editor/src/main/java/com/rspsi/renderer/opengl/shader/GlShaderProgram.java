package com.rspsi.renderer.opengl.shader;

import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;

/** Compiles named GLSL resources with diagnostics that include numbered source. */
public final class GlShaderProgram {
    private GlShaderProgram() {
    }

    public static int link(ShaderSourceLoader.LoadedShader vertex,
                           ShaderSourceLoader.LoadedShader fragment) {
        int vertexShader = compile(GL_VERTEX_SHADER, vertex);
        int fragmentShader = 0;
        int program = 0;
        try {
            fragmentShader = compile(GL_FRAGMENT_SHADER, fragment);
            program = glCreateProgram();
            glAttachShader(program, vertexShader);
            glAttachShader(program, fragmentShader);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
                String log = glGetProgramInfoLog(program);
                glDeleteProgram(program);
                program = 0;
                throw new IllegalStateException(
                        "OpenGL program link failed for " + vertex.name() + " + "
                                + fragment.name() + ":\n" + log);
            }
            return program;
        } finally {
            if (vertexShader != 0) glDeleteShader(vertexShader);
            if (fragmentShader != 0) glDeleteShader(fragmentShader);
            if (program == 0) {
                // Program is deleted above on link failure. Compile failures
                // happen before a program object is created.
            }
        }
    }

    private static int compile(int type, ShaderSourceLoader.LoadedShader shaderSource) {
        int shader = glCreateShader(type);
        glShaderSource(shader, shaderSource.source());
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(
                    "OpenGL shader compile failed for " + shaderSource.name()
                            + ":\n" + log + "\n--- numbered source ---\n"
                            + numberedSource(shaderSource.source()));
        }
        return shader;
    }

    static String numberedSource(String source) {
        String[] lines = source.split("\\R", -1);
        StringBuilder output = new StringBuilder(source.length() + lines.length * 8);
        for (int index = 0; index < lines.length; index++) {
            output.append(String.format("%4d | %s%n", index + 1, lines[index]));
        }
        return output.toString();
    }
}
