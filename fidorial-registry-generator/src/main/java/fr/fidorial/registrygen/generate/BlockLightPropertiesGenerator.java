package fr.fidorial.registrygen.generate;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import fr.fidorial.registrygen.model.BlockReportDefinition;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Generates {@code BlockStateLightData}: per-BlockState opacity and light
 * emission, indexed by network state id.
 *
 * <p>This data isn't present in {@code reports/blocks.json} — it comes from a
 * light-data JSON dump produced by attaching a javaagent to the data generator
 * at bootstrap time (see {@code fr.fidorial.registrygen.agent.LightDataAgent}).</p>
 *
 * @since 0.1.0
 */
public final class BlockLightPropertiesGenerator {

    public static final String LIGHT_DATA_PACKAGE = "fr.euphyllia.fidorial.server.registry.data";

    private static final String CLASS_NAME = "BlockStateLightProperties";
    private static final int MAX_LITERAL_CHARS = 60_000;

    public void generate(final List<BlockReportDefinition> blocks, final Path lightDataFile, final Path outputDirectory) throws IOException {

        final Map<String, BlockLightPropertiesReportParser.Entry> lightData = BlockLightPropertiesReportParser.read(lightDataFile);

        int maxStateId = -1;
        for (final BlockReportDefinition block : blocks) {
            for (final int stateId : block.stateIdsInOrder()) {
                maxStateId = Math.max(maxStateId, stateId);
            }
        }

        final byte[] packed = new byte[maxStateId + 1];

        for (final BlockReportDefinition block : blocks) {

            final BlockLightPropertiesReportParser.Entry entry = lightData.get(block.identifier());

            if (entry == null) {
                throw new IllegalStateException("No light data for block '" + block.identifier() + "'; is light-data.json stale?");
            }

            final int[] stateIds = block.stateIdsInOrder();

            for (int ordinal = 0; ordinal < stateIds.length; ordinal++) {

                final int opacity = entry.opacity()[ordinal];
                final int emission = entry.emission()[ordinal];

                if (opacity < 0 || opacity > 15 || emission < 0 || emission > 15) {
                    throw new IllegalStateException("Block '" + block.identifier() + "' state " + stateIds[ordinal]
                            + " has opacity/emission outside 0-15 (opacity=" + opacity + ", emission=" + emission + ")");
                }

                packed[stateIds[ordinal]] = (byte) ((opacity << 4) | emission);
            }
        }

        final String encoded = Base64.getEncoder().encodeToString(packed);

        final TypeSpec type = TypeSpec.classBuilder(CLASS_NAME)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Per-BlockState opacity and light emission, indexed by network state id.\n\n")
                .addJavadoc("<p>Generated from Mojang's blocks report and a bootstrap-time light-data dump; do not edit.</p>\n")
                .addJavadoc("\n<p>Values are packed one byte per state (opacity in the high nibble, emission in the\n")
                .addJavadoc("low nibble) and stored Base64-encoded.")
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .addStatement("throw new $T()", UnsupportedOperationException.class)
                        .build())
                .addField(FieldSpec.builder(byte[].class, "PACKED", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer(decodeInitializer(encoded))
                        .build())
                .addMethod(MethodSpec.methodBuilder("opacity")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(int.class)
                        .addParameter(int.class, "stateId")
                        .addStatement("return (PACKED[stateId] >> 4) & 0xF")
                        .build())
                .addMethod(MethodSpec.methodBuilder("emission")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(int.class)
                        .addParameter(int.class, "stateId")
                        .addStatement("return PACKED[stateId] & 0xF")
                        .build())
                .build();

        JavaFile.builder(LIGHT_DATA_PACKAGE, type)
                .indent("    ")
                .skipJavaLangImports(true)
                .build()
                .writeTo(outputDirectory);
    }

    private static CodeBlock decodeInitializer(final String encoded) {

        if (encoded.length() <= MAX_LITERAL_CHARS) {
            return CodeBlock.of("$T.getDecoder().decode($S)", Base64.class, encoded);
        }

        final CodeBlock.Builder initializer = CodeBlock.builder()
                .add("$T.getDecoder().decode(\n", Base64.class)
                .indent();

        for (int start = 0; start < encoded.length(); start += MAX_LITERAL_CHARS) {
            final int end = Math.min(start + MAX_LITERAL_CHARS, encoded.length());
            initializer.add("$S", encoded.substring(start, end));
            initializer.add(end < encoded.length() ? " +\n" : ")");
        }

        return initializer.unindent().build();
    }
}
