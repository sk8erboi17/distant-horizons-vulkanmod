package com.braffolk.dhvulkan.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites GLSL that VulkanMod's shader converter cannot parse into equivalent
 * GLSL it can.
 *
 * DH 3.1+ renders LODs through Blaze3D; on VulkanMod every Blaze3D pipeline goes
 * through VulkanMod's own GLSL parser (GLSLParser) before shaderc. That parser
 * is narrower than GLSL, and DH's shaders use three constructs it rejects:
 *
 *   1. an interpolation qualifier before the storage qualifier
 *      ("layout(location = 4) flat in uint x;"): the parser expects in/out right
 *      after layout(...). GLSL 4.20+ accepts any order, so "in flat" is equivalent;
 *   2. a redeclared built-in ("layout(location = 6) in vec4 gl_FragCoord;"),
 *      which Vulkan GLSL rejects; dropping it keeps the built-in;
 *   3. bool members in uniform blocks: VulkanMod's UBO layout only knows
 *      float/int/vec/ivec/mat. std140 stores a bool as a 4-byte value, exactly
 *      like an int, so the member becomes an int and every later use is
 *      rewritten to (name != 0).
 *
 * Each rewrite only fires on text VulkanMod would refuse anyway, so shaders that
 * already parse come back unchanged.
 */
public final class VulkanGlslCompat {

    private static final Pattern QUALIFIER_BEFORE_STORAGE = Pattern.compile(
            "(layout\\s*\\([^)]*\\)\\s*)(flat|smooth|noperspective)(\\s+)(in|out)\\b");
    private static final Pattern FRAG_COORD_REDECLARATION = Pattern.compile(
            "(?m)^[ \\t]*(layout\\s*\\([^)]*\\)\\s*)?in\\s+vec4\\s+gl_FragCoord\\s*;[^\\n]*$");
    private static final Pattern UNIFORM_BLOCK = Pattern.compile(
            "uniform\\s+\\w+\\s*\\{([^}]*)\\}");
    private static final Pattern BOOL_MEMBER = Pattern.compile("\\bbool(\\s+)(\\w+)(\\s*;)");

    private VulkanGlslCompat() {
    }

    /**
     * @param source GLSL source as VulkanMod is about to parse it; may be null
     * @return the rewritten source, or {@code source} itself when nothing applies
     */
    public static String patch(String source) {
        if (source == null) {
            return null;
        }
        String out = QUALIFIER_BEFORE_STORAGE.matcher(source).replaceAll("$1$4$3$2");
        out = FRAG_COORD_REDECLARATION.matcher(out).replaceAll("");
        return boolBlockMembersToInt(out);
    }

    private static String boolBlockMembersToInt(String source) {
        Matcher block = UNIFORM_BLOCK.matcher(source);
        StringBuilder rewritten = new StringBuilder(source.length() + 64);
        List<String> boolNames = new ArrayList<>();
        int copiedUpTo = 0;
        while (block.find()) {
            String body = block.group(1);
            Matcher member = BOOL_MEMBER.matcher(body);
            if (!member.find()) {
                continue;
            }
            member.reset();
            while (member.find()) {
                boolNames.add(member.group(2));
            }
            rewritten.append(source, copiedUpTo, block.start(1));
            rewritten.append(BOOL_MEMBER.matcher(body).replaceAll("int$1$2$3"));
            copiedUpTo = block.end(1);
        }
        if (boolNames.isEmpty()) {
            return source;
        }

        // Uses always follow the block declarations, so only the text after the
        // last rewritten block needs (name != 0); the blocks keep the bare names.
        String tail = source.substring(copiedUpTo);
        for (String name : boolNames) {
            tail = tail.replaceAll("\\b" + Pattern.quote(name) + "\\b",
                    Matcher.quoteReplacement("(" + name + " != 0)"));
        }
        rewritten.append(tail);
        return rewritten.toString();
    }
}
