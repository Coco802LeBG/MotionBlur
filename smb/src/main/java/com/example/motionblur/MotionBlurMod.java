package com.example.motionblur;

import net.fabricmc.api.ClientModInitializer;

public class MotionBlurMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MotionBlurConfig.load();
    }
}
