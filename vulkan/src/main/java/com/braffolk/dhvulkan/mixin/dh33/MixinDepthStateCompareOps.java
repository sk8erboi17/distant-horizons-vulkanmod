package com.braffolk.dhvulkan.mixin.dh33;

import org.lwjgl.vulkan.VK10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the depth compare functions VulkanMod's DepthState.glToVulkan lacks.
 *
 * VulkanMod maps GL_EQUAL..GL_ALWAYS (514-519) and throws "unknown blend
 * factor.." for anything else. Vanilla never asks for GL_LESS, but DH 3.x draws
 * its LODs with the Blaze3D LESS_DEPTH_TEST. VulkanMod keeps the standard
 * (not reversed) depth range, so GL_LESS is VK_COMPARE_OP_LESS.
 * Only applied when DH 3.1+ is installed.
 */
@Mixin(targets = "net.vulkanmod.vulkan.shader.PipelineState$DepthState", remap = false)
public class MixinDepthStateCompareOps {

    private static final int GL_NEVER = 0x0200;
    private static final int GL_LESS = 0x0201;

    @Inject(method = "glToVulkan(I)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dhvulkan$lessAndNever(int glDepthFunc, CallbackInfoReturnable<Integer> cir) {
        if (glDepthFunc == GL_LESS) {
            cir.setReturnValue(VK10.VK_COMPARE_OP_LESS);
        } else if (glDepthFunc == GL_NEVER) {
            cir.setReturnValue(VK10.VK_COMPARE_OP_NEVER);
        }
    }
}
