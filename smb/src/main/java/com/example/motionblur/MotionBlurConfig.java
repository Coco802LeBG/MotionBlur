package com.example.motionblur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MotionBlurConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("simplemotionblur.json");

    public static boolean enabled = true;
    /** 0.0 = aucun flou, 0.95 = très fort */
    public static float strength = 0.6f;
    /** Garde le même rendu quel que soit le nombre de FPS */
    public static boolean fpsIndependent = true;

    private static class Data {
        boolean enabled = MotionBlurConfig.enabled;
        float strength = MotionBlurConfig.strength;
        boolean fpsIndependent = MotionBlurConfig.fpsIndependent;
    }

    public static void load() {
        if (!Files.exists(PATH)) { save(); return; }
        try {
            Data d = GSON.fromJson(Files.readString(PATH), Data.class);
            if (d == null) return;
            enabled = d.enabled;
            strength = Math.max(0f, Math.min(0.95f, d.strength));
            fpsIndependent = d.fpsIndependent;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(new Data()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
