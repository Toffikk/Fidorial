package fr.fidorial.registrygen.agent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

// Attaches to the data generator JVM and dumps per-BlockState opacity/emission,
// since reports/blocks.json doesn't include that. Usage: -javaagent:jar=<output.json>
public final class BlockLightPropertiesAgent {

    private static Instrumentation instrumentation;

    private BlockLightPropertiesAgent() {
    }

    public static void premain(final String agentArgs, final Instrumentation inst) {

        if (agentArgs == null || agentArgs.isBlank()) {
            return;
        }

        instrumentation = inst;

        final Path outputFile = Path.of(agentArgs);
        final Path logFile = outputFile.resolveSibling(outputFile.getFileName() + ".agent.log");

        // System.out/err can be torn down by other shutdown hooks before ours runs,
        // so we log to disk instead
        log(logFile, "premain invoked, waiting for shutdown");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                extract(outputFile, logFile);
                log(logFile, "wrote light data to " + outputFile);
            } catch (final Throwable t) {
                log(logFile, "extraction failed:\n" + stackTraceOf(t));
            }
        }, "LightDataAgent"));
    }

    private static void extract(final Path outputFile, final Path logFile) throws Exception {

        // Minecraft's server bundler loads game classes through its own URLClassLoader,
        // not the system one, so we find the class first and borrow its loader
        final Class<?> builtInRegistries = findLoadedClass("net.minecraft.core.registries.BuiltInRegistries");

        if (builtInRegistries == null) {
            throw new IllegalStateException("BuiltInRegistries was never loaded - did the data generator reach bootstrap?");
        }

        final ClassLoader loader = builtInRegistries.getClassLoader();

        final Class<?> blockClass = Class.forName("net.minecraft.world.level.block.Block", true, loader);
        final Class<?> blockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState", true, loader);
        final Class<?> registryClass = Class.forName("net.minecraft.core.Registry", true, loader);
        final Class<?> stateDefinitionClass = Class.forName("net.minecraft.world.level.block.state.StateDefinition", true, loader);

        final Object blockRegistry = builtInRegistries.getField("BLOCK").get(null);

        final Method getStateDefinition = blockClass.getMethod("getStateDefinition");
        final Method getPossibleStates = stateDefinitionClass.getMethod("getPossibleStates");
        final Method getKey = findMethod(registryClass, 1, "getKey");
        final Method getLightBlock = findMethod(blockStateClass, 0, "getLightDampening");
        final Method getLightEmission = findMethod(blockStateClass, 0, "getLightEmission");

        if (getKey == null || getLightBlock == null || getLightEmission == null) {
            if (getLightBlock == null) {
                log(logFile, "no light-block getter matched known names, dumping candidates on " + blockStateClass.getName() + ":");
                dumpCandidates(logFile, blockStateClass);
            }
            throw new IllegalStateException("couldn't resolve getLightEmission, check the agent log");
        }

        final Map<String, int[][]> result = new LinkedHashMap<>();

        for (final Object block : (Iterable<?>) blockRegistry) {

            final Collection<?> states = (Collection<?>) getPossibleStates.invoke(getStateDefinition.invoke(block));

            final int[] opacity = new int[states.size()];
            final int[] emission = new int[states.size()];

            int i = 0;
            for (final Object state : states) {
                opacity[i] = (int) getLightBlock.invoke(state);
                emission[i] = (int) getLightEmission.invoke(state);
                i++;
            }

            result.put(getKey.invoke(blockRegistry, block).toString(), new int[][] { opacity, emission });
        }

        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        Files.writeString(outputFile, toJson(result), StandardCharsets.UTF_8);
    }

    private static Class<?> findLoadedClass(final String name) {
        for (final Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded.getName().equals(name)) {
                return loaded;
            }
        }
        return null;
    }

    private static Method findMethod(final Class<?> owner, final int paramCount, final String... names) {
        for (final String name : names) {
            for (final Method method : owner.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                    return method;
                }
            }
        }
        return null;
    }

    private static void dumpCandidates(final Path logFile, final Class<?> owner) {
        for (final Method method : owner.getMethods()) {
            if (method.getParameterCount() == 0 && (method.getReturnType() == int.class || method.getReturnType() == boolean.class)) {
                log(logFile, "  " + method);
            }
        }
    }

    private static void log(final Path logFile, final String message) {
        try {
            Files.createDirectories(logFile.toAbsolutePath().getParent());
            Files.writeString(logFile, "[" + Instant.now() + "] " + message + "\n",
                    StandardCharsets.UTF_8,
                    Files.exists(logFile) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
        } catch (final Exception ignored) {
            // best effort
        }
    }

    private static String stackTraceOf(final Throwable t) {
        final StringWriter writer = new StringWriter();
        t.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String toJson(final Map<String, int[][]> data) {

        final StringBuilder json = new StringBuilder("{\n");
        int i = 0;

        for (final Map.Entry<String, int[][]> entry : data.entrySet()) {
            json.append("  \"").append(entry.getKey()).append("\": {\n")
                    .append("    \"opacity\": ").append(arrayToJson(entry.getValue()[0])).append(",\n")
                    .append("    \"emission\": ").append(arrayToJson(entry.getValue()[1])).append("\n")
                    .append("  }")
                    .append(++i < data.size() ? ",\n" : "\n");
        }

        return json.append("}\n").toString();
    }

    private static String arrayToJson(final int[] values) {
        final StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            builder.append(values[i]);
            if (i < values.length - 1) {
                builder.append(", ");
            }
        }
        return builder.append("]").toString();
    }
}
