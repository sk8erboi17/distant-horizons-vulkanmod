package com.braffolk.dhvulkan.compat;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Vulkan vertex-attribute formats for Blaze3D vertex formats VulkanMod does not map.
 *
 * VulkanMod's GraphicsPipeline.getAttributeDescriptions only knows the element
 * kinds vanilla uses and throws "Unknown type" for the rest. DH 3.x's Blaze3D
 * pipelines use USHORT x3 positions, USHORT / BYTE generic attributes, FLOAT x4
 * colours and FLOAT x2 screen positions (VulkanMod reads every FLOAT position as
 * x3, past the end of an 8-byte vertex).
 *
 * {@link #needsOverride} tells whether a format contains any such element; only
 * then is the whole description built here. Supported kinds keep VulkanMod's own
 * choice. Integer attributes get *_UINT formats because every integer input DH
 * declares is uint/uvecN, and Vulkan requires the format's numeric type to match
 * the shader input.
 */
public final class VulkanVertexFormats {

    private VulkanVertexFormats() {
    }

    /** @return true when VulkanMod would reject or mis-size an element of {@code format} */
    public static boolean needsOverride(VertexFormat format) {
        for (VertexFormatElement e : format.getElements()) {
            if (!vulkanModMaps(e)) {
                return true;
            }
        }
        return false;
    }

    /** Mirrors the cases VulkanMod 0.6 maps correctly. */
    private static boolean vulkanModMaps(VertexFormatElement e) {
        VertexFormatElement.Type t = e.type();
        return switch (e.usage()) {
            case POSITION -> (t == VertexFormatElement.Type.FLOAT && e.count() == 3)
                    || t == VertexFormatElement.Type.SHORT || t == VertexFormatElement.Type.BYTE;
            case NORMAL -> t == VertexFormatElement.Type.BYTE;
            case COLOR -> t == VertexFormatElement.Type.UBYTE || t == VertexFormatElement.Type.UINT;
            case UV -> t == VertexFormatElement.Type.FLOAT || t == VertexFormatElement.Type.SHORT
                    || t == VertexFormatElement.Type.USHORT || t == VertexFormatElement.Type.UINT;
            case GENERIC -> e.count() == 1 && (t == VertexFormatElement.Type.SHORT
                    || t == VertexFormatElement.Type.INT || t == VertexFormatElement.Type.FLOAT);
        };
    }

    /** @return the VkFormat for {@code e}; VulkanMod's choice for the kinds it already maps */
    public static int vkFormat(VertexFormatElement e) {
        VertexFormatElement.Type t = e.type();
        int n = e.count();
        switch (e.usage()) {
            case POSITION:
                if (t == VertexFormatElement.Type.SHORT) return VK_FORMAT_R16G16B16A16_SINT;
                if (t == VertexFormatElement.Type.BYTE) return VK_FORMAT_R8G8B8A8_SINT;
                break;
            case NORMAL:
                if (t == VertexFormatElement.Type.BYTE) return VK_FORMAT_R8G8B8A8_SNORM;
                break;
            case COLOR:
                if (t == VertexFormatElement.Type.UBYTE) return VK_FORMAT_R8G8B8A8_UNORM;
                if (t == VertexFormatElement.Type.UINT) return VK_FORMAT_R32_UINT;
                break;
            case UV:
                if (t == VertexFormatElement.Type.SHORT) return VK_FORMAT_R16G16_SINT;
                if (t == VertexFormatElement.Type.USHORT) return VK_FORMAT_R16G16_UINT;
                if (t == VertexFormatElement.Type.UINT) return VK_FORMAT_R32_UINT;
                break;
            case GENERIC:
                if (n == 1 && t == VertexFormatElement.Type.SHORT) return VK_FORMAT_R16_SINT;
                if (n == 1 && t == VertexFormatElement.Type.INT) return VK_FORMAT_R32_SINT;
                break;
        }
        return switch (t) {
            case FLOAT -> pick(n, VK_FORMAT_R32_SFLOAT, VK_FORMAT_R32G32_SFLOAT, VK_FORMAT_R32G32B32_SFLOAT, VK_FORMAT_R32G32B32A32_SFLOAT);
            case UBYTE, BYTE -> pick(n, VK_FORMAT_R8_UINT, VK_FORMAT_R8G8_UINT, VK_FORMAT_R8G8B8_UINT, VK_FORMAT_R8G8B8A8_UINT);
            case USHORT, SHORT -> pick(n, VK_FORMAT_R16_UINT, VK_FORMAT_R16G16_UINT, VK_FORMAT_R16G16B16_UINT, VK_FORMAT_R16G16B16A16_UINT);
            case UINT, INT -> pick(n, VK_FORMAT_R32_UINT, VK_FORMAT_R32G32_UINT, VK_FORMAT_R32G32B32_UINT, VK_FORMAT_R32G32B32A32_UINT);
        };
    }

    private static int pick(int count, int one, int two, int three, int four) {
        return switch (count) {
            case 1 -> one;
            case 2 -> two;
            case 3 -> three;
            case 4 -> four;
            default -> throw new IllegalArgumentException("Unsupported vertex element count: " + count);
        };
    }
}
