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

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern FRAG_COORD_REDECLARATION = Pattern.compile(
            "(?m)^[ \\t]*(layout\\s*\\([^)]*\\)\\s*)?in\\s+vec4\\s+gl_FragCoord\\s*;[^\\n]*$");
    // layout(location = N) [flat|smooth|noperspective] in|out ...
    private static final Pattern LAYOUT_ON_VARYING = Pattern.compile(
            "layout\\s*\\([^)]*\\)\\s*((?:flat|smooth|noperspective)\\s+)?(in|out)\\b");
    // [flat|...] in|out type name;   (after the layout qualifiers are gone)
    private static final Pattern QUALIFIED_VARYING = Pattern.compile(
            "(?m)^([ \\t]*)(flat|smooth|noperspective)\\s+(in|out)\\s+(\\w+)\\s+(\\w+)\\s*;");
    private static final Pattern MAIN_DECLARATION = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)");
    private static final Pattern IN_PARAMETER = Pattern.compile(
            "([(,]\\s*(?:const\\s+)?)in\\s+(?=\\w+\\s+\\w+\\s*[,)])");
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
        // VulkanMod's lexer has no /* */ comments: it tokenizes their text, so an
        // apostrophe stops it and words like "out" read as declarations.
        String out = BLOCK_COMMENT.matcher(source).replaceAll("");
        out = FRAG_COORD_REDECLARATION.matcher(out).replaceAll("");
        out = LAYOUT_ON_VARYING.matcher(out).replaceAll("$1$2");
        out = qualifiedVaryings(out);
        // "const in float x" in a parameter list: VulkanMod reads that "in" as a
        // varying declaration. "in" is the default parameter direction, so drop it.
        out = IN_PARAMETER.matcher(out).replaceAll("$1");
        out = stripCommentsInUniformBlocks(out);
        out = boolBlockMembersToInt(out);
        return out.equals(source) ? source : out;
    }

    private static final Pattern LINE_OR_BLOCK_COMMENT = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    /** VulkanMod's uniform-block parser expects "type name;" tokens only, no comments. */
    private static String stripCommentsInUniformBlocks(String source) {
        Matcher block = UNIFORM_BLOCK.matcher(source);
        StringBuilder out = new StringBuilder(source.length());
        int last = 0;
        while (block.find()) {
            out.append(source, last, block.start(1));
            out.append(LINE_OR_BLOCK_COMMENT.matcher(block.group(1)).replaceAll(""));
            last = block.end(1);
        }
        out.append(source, last, source.length());
        return out.toString();
    }

    /**
     * VulkanMod reads varyings only as "in|out type name;", so interpolation
     * qualifiers must go. Float varyings simply lose them. Integer varyings must be
     * flat in GLSL, so they travel as a float carrier instead: the original name
     * becomes a plain global, filled from / copied into "name_dhf" by a wrapper
     * main(). DH gives every vertex of a face the same value (normal index,
     * texture tile), so the interpolated carrier is that value and round() is exact.
     */
    private static String qualifiedVaryings(String source) {
        Matcher m = QUALIFIED_VARYING.matcher(source);
        StringBuilder out = new StringBuilder(source.length() + 256);
        StringBuilder beforeMain = new StringBuilder();
        StringBuilder afterMain = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String indent = m.group(1);
            String dir = m.group(3);
            String type = m.group(4);
            String name = m.group(5);
            out.append(source, last, m.start());
            String carrier = floatCarrier(type);
            if (carrier == null) {
                out.append(indent).append(dir).append(' ').append(type).append(' ').append(name).append(';');
            } else {
                String carrierName = name + "_dhf";
                out.append(indent).append(dir).append(' ').append(carrier).append(' ').append(carrierName).append(";\n")
                   .append(indent).append(type).append(' ').append(name).append(';');
                if (dir.equals("in")) {
                    beforeMain.append("    ").append(name).append(" = ").append(type)
                              .append("(round(").append(carrierName).append("));\n");
                } else {
                    afterMain.append("    ").append(carrierName).append(" = ").append(carrier)
                             .append('(').append(name).append(");\n");
                }
            }
            last = m.end();
        }
        out.append(source, last, source.length());
        if (beforeMain.isEmpty() && afterMain.isEmpty()) {
            return out.toString();
        }
        String renamed = MAIN_DECLARATION.matcher(out).replaceFirst("void dhvulkan_main()");
        return renamed + "\nvoid main() {\n" + beforeMain + "    dhvulkan_main();\n" + afterMain + "}\n";
    }

    /** @return the float type that can carry {@code type}, or null if it is not integral */
    private static String floatCarrier(String type) {
        switch (type) {
            case "int": case "uint": return "float";
            case "ivec2": case "uvec2": return "vec2";
            case "ivec3": case "uvec3": return "vec3";
            case "ivec4": case "uvec4": return "vec4";
            default: return null;
        }
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
