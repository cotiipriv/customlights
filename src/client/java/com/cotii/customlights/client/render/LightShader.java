package com.cotii.customlights.client.render;

import com.cotii.customlights.Customlights;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A light program compiled from {@code assets/customlights/shaders/light/} with plain OpenGL: the fullscreen triangle vertex
 * shader and a fragment shader prefixed with {@code common.glsl}.
 */
final class LightShader {
    final int program;
    private final Map<String, Integer> locations = new HashMap<>();

    private LightShader(int program) {
        this.program = program;
    }

    int location(String name) {
        return locations.computeIfAbsent(name, key -> GL20.glGetUniformLocation(program, key));
    }

    void set1i(String name, int value) {
        GL20.glUniform1i(location(name), value);
    }

    void set1f(String name, float value) {
        GL20.glUniform1f(location(name), value);
    }

    void set2f(String name, float x, float y) {
        GL20.glUniform2f(location(name), x, y);
    }

    void set3f(String name, float x, float y, float z) {
        GL20.glUniform3f(location(name), x, y, z);
    }

    void set4f(String name, float x, float y, float z, float w) {
        GL20.glUniform4f(location(name), x, y, z, w);
    }

    void setMatrix3(String name, float[] values) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(9);
            buffer.put(values).flip();
            GL20.glUniformMatrix3fv(location(name), false, buffer);
        }
    }

    void setMatrix(String name, FloatBuffer buffer, boolean mat4) {
        if (mat4) {
            GL20.glUniformMatrix4fv(location(name), false, buffer);
        } else {
            GL20.glUniformMatrix3fv(location(name), false, buffer);
        }
    }

    /** The compiled program, or null (logged) when the driver rejects it. */
    static LightShader create(String fragmentName) {
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, read("light.vsh"));
            // The shared code reads depth the way this game version stores it
            String defines = (com.cotii.customlights.client.compat.DepthConvention.reversed() ? "#define REVERSED_DEPTH\n" : "")
                    + (com.cotii.customlights.client.compat.DepthConvention.zeroToOne() ? "#define DEPTH_ZERO_TO_ONE\n" : "");
            String fragmentSource = "#version 150\n" + defines + read("common.glsl") + "\n#line 1\n" + read(fragmentName);
            fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException("link failed: " + GL20.glGetProgramInfoLog(program, 32768));
            }
            return new LightShader(program);
        } catch (Exception exception) {
            Customlights.LOGGER.error("CustomLights could not build its {} shader, lights are disabled", fragmentName, exception);
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            return null;
        } finally {
            if (vertex != 0) {
                GL20.glDeleteShader(vertex);
            }
            if (fragment != 0) {
                GL20.glDeleteShader(fragment);
            }
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader, 32768);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("compile failed: " + log);
        }
        return shader;
    }

    private static String read(String name) throws IOException {
        String path = "/assets/" + Customlights.MOD_ID + "/shaders/light/" + name;
        try (InputStream stream = LightShader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("missing " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
