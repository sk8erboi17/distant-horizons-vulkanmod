package com.braffolk.dhvulkan.mixin.dh24;

import com.braffolk.dhvulkan.compat.Compat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shrinks the render distance DH sees to the area the client really holds.
 *
 * DH derives its LOD near clip plane, the vanilla fade and render-section
 * culling from this value. Returning the loaded radius instead of the option
 * value lets LODs fill chunks the server has not sent (or not sent yet).
 * See {@link Compat#loadedRenderDistance(int)}.
 */
@Mixin(targets = "loaderCommon.fabric.com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper", remap = false)
public class MixinMinecraftRenderWrapper {

    @Inject(method = "getRenderDistance()I", at = @At("RETURN"), cancellable = true, require = 0)
    private void dhvulkan$loadedRenderDistance(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Compat.loadedRenderDistance(cir.getReturnValue()));
    }
}
