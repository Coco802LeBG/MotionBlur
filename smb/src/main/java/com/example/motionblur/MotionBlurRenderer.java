package com.example.motionblur;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Flou par accumulation, en OpenGL brut (indépendant du système de rendu interne de Minecraft) :
 * image finale = image actuelle * (1 - a) + image précédente (déjà floutée) * a
 * Tout l'état OpenGL modifié est restauré à la fin.
 */
public final class MotionBlurRenderer {
    private static int prevTex, program, vao, uAlpha, texW, texH;
    private static boolean hasPrevious, failed;
    private static long lastNs;

    private static final String VERT = """
            #version 330 core
            out vec2 uv;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                uv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAG = """
            #version 330 core
            in vec2 uv;
            uniform sampler2D tex;
            uniform float alpha;
            out vec4 outColor;
            void main() { outColor = vec4(texture(tex, uv).rgb, alpha); }
            """;

    private MotionBlurRenderer() {}

    public static void apply() {
        if (failed) return;
        if (!MotionBlurConfig.enabled || MotionBlurConfig.strength <= 0f) {
            hasPrevious = false;
            lastNs = 0;
            return;
        }
        try {
            run();
        } catch (Throwable t) {
            failed = true; // on désactive l'effet plutôt que de faire planter le jeu
            t.printStackTrace();
        }
    }

    private static void run() {
        var window = MinecraftClient.getInstance().getWindow();
        int w = window.getFramebufferWidth(), h = window.getFramebufferHeight();
        if (w <= 0 || h <= 0) return;

        long now = System.nanoTime();
        float dtMs = lastNs == 0 ? 16.667f : (now - lastNs) / 1_000_000f;
        lastNs = now;
        if (dtMs > 250f) hasPrevious = false; // gros trou (menu, F1...) : on repart propre

        if (program == 0) init();

        // ---- sauvegarde de l'état GL ----
        int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        // ---- (re)création de la texture d'accumulation ----
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
        if (w != texW || h != texH) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            texW = w; texH = h;
            hasPrevious = false;
        }

        // ---- dessin de l'image précédente par-dessus l'image actuelle ----
        if (hasPrevious) {
            float a = MotionBlurConfig.strength;
            if (MotionBlurConfig.fpsIndependent) {
                a = (float) Math.pow(a, Math.max(0.05f, dtMs / 16.667f));
            }
            GL11.glViewport(0, 0, w, h);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            GL20.glUseProgram(program);
            GL20.glUniform1f(uAlpha, a);
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        }

        // ---- copie du résultat pour la frame suivante ----
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawFbo);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        hasPrevious = true;

        // ---- restauration de l'état GL ----
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glBindVertexArray(oldVao);
        GL20.glUseProgram(oldProgram);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTex);
        GL13.glActiveTexture(oldActive);
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
        set(GL11.GL_BLEND, blend);
        set(GL11.GL_DEPTH_TEST, depth);
        set(GL11.GL_CULL_FACE, cull);
        set(GL11.GL_SCISSOR_TEST, scissor);
        GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
    }

    private static void set(int cap, boolean on) {
        if (on) GL11.glEnable(cap); else GL11.glDisable(cap);
    }

    private static void init() {
        int vs = compile(GL20.GL_VERTEX_SHADER, VERT);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAG);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Link error: " + GL20.glGetProgramInfoLog(p));
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);

        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(p);
        GL20.glUniform1i(GL20.glGetUniformLocation(p, "tex"), 0);
        uAlpha = GL20.glGetUniformLocation(p, "alpha");
        GL20.glUseProgram(oldProgram);

        vao = GL30.glGenVertexArrays();

        int oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        prevTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12ClampToEdge());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12ClampToEdge());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTex);
        GL13.glActiveTexture(oldActive);

        program = p;
    }

    private static int GL12ClampToEdge() { return 0x812F; }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Shader error: " + GL20.glGetShaderInfoLog(s));
        }
        return s;
    }
}
