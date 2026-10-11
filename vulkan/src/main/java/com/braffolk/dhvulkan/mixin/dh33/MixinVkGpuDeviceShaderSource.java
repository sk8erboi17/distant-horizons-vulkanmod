package com.braffolk.dhvulkan.mixin.dh33;

import com.braffolk.dhvulkan.compat.VulkanGlslCompat;
import net.vulkanmod.render.engine.VkGpuDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds VulkanMod's shader converter a source it can parse.
 *
 * With DH 3.1+ on its Blaze3D engine, DH's LOD shaders reach VulkanMod as
 * ordinary Blaze3D pipelines; getCachedShaderSrc returns the resolved GLSL right
 * before VulkanMod's GLSLParser reads it. See {@link VulkanGlslCompat}.
 * Only applied when DH 3.1+ is installed (DhVulkanMixinPlugin).
 */
@Mixin(value = VkGpuDevice.class, remap = false)
public class MixinVkGpuDeviceShaderSource {

    @Inject(method = "getCachedShaderSrc", at = @At("RETURN"), cancellable = true, require = 0)
    private void dhvulkan$patchForVulkanModParser(CallbackInfoReturnable<String> cir) {
        String source = cir.getReturnValue();
        String patched = VulkanGlslCompat.patch(source);
        if (patched != source) {
            cir.setReturnValue(patched);
        }
    }
}
