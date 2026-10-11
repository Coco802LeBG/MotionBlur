package com.example.motionblur;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class MotionBlurConfigScreen extends Screen {
    private static final float MAX = 0.95f;
    private final Screen parent;

    public MotionBlurConfigScreen(Screen parent) {
        super(Text.literal("Motion Blur"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 200, x = width / 2 - w / 2, y = height / 4;

        addDrawableChild(ButtonWidget.builder(enabledText(), btn -> {
            MotionBlurConfig.enabled = !MotionBlurConfig.enabled;
            btn.setMessage(enabledText());
        }).dimensions(x, y, w, 20).build());

        addDrawableChild(new SliderWidget(x, y + 28, w, 20, strengthText(), MotionBlurConfig.strength / MAX) {
            @Override protected void updateMessage() { setMessage(strengthText()); }
            @Override protected void applyValue() { MotionBlurConfig.strength = (float) (value * MAX); }
        });

        addDrawableChild(ButtonWidget.builder(fpsText(), btn -> {
            MotionBlurConfig.fpsIndependent = !MotionBlurConfig.fpsIndependent;
            btn.setMessage(fpsText());
        }).dimensions(x, y + 56, w, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Terminé"), btn -> close())
                .dimensions(x, y + 96, w, 20).build());
    }

    private Text enabledText() {
        return Text.literal("Motion blur : " + (MotionBlurConfig.enabled ? "ON" : "OFF"));
    }

    private Text strengthText() {
        return Text.literal("Intensité : " + Math.round(MotionBlurConfig.strength / MAX * 100) + "%");
    }

    private Text fpsText() {
        return Text.literal("Indépendant des FPS : " + (MotionBlurConfig.fpsIndependent ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 4 - 24, 0xFFFFFF);
    }

    @Override
    public void close() {
        MotionBlurConfig.save();
        client.setScreen(parent);
    }
}
