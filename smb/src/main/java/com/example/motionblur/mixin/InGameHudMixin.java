package com.example.motionblur.mixin;

import com.example.motionblur.MotionBlurRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Le monde est déjà rendu à ce moment, et le HUD n'est pas encore dessiné : le HUD reste net. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void simplemotionblur$apply(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MotionBlurRenderer.apply();
    }
}
