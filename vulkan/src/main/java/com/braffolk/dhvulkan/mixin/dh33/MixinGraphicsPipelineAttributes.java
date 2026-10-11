package com.braffolk.dhvulkan.mixin.dh33;

import com.braffolk.dhvulkan.compat.VulkanVertexFormats;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.vulkanmod.interfaces.VertexFormatMixed;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Builds vertex-attribute descriptions for Blaze3D formats VulkanMod cannot map
 * (DH 3.x's LOD and post-process pipelines); every other format keeps VulkanMod's
 * own code path. Allocation (calloc, owned by the pipeline builder) and offsets
 * (VertexFormatMixed.getOffset) match VulkanMod's implementation.
 * See {@link VulkanVertexFormats}. Only applied when DH 3.1+ is installed.
 */
@Mixin(value = GraphicsPipeline.class, remap = false)
public class MixinGraphicsPipelineAttributes {

    @Inject(method = "getAttributeDescriptions", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dhvulkan$mapExtraVertexElements(VertexFormat format,
            CallbackInfoReturnable<VkVertexInputAttributeDescription.Buffer> cir) {
        if (!VulkanVertexFormats.needsOverride(format)) {
            return;
        }
        List<VertexFormatElement> elements = format.getElements();
        VkVertexInputAttributeDescription.Buffer descriptions = VkVertexInputAttributeDescription.calloc(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            descriptions.get(i)
                    .binding(0)
                    .location(i)
                    .format(VulkanVertexFormats.vkFormat(elements.get(i)))
                    .offset(((VertexFormatMixed) format).getOffset(i));
        }
        cir.setReturnValue(descriptions.rewind());
    }
}
