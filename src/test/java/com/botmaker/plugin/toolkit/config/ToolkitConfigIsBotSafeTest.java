package com.botmaker.plugin.toolkit.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This package runs in a bot, and a bot has neither the plugin contract nor JavaFX.
 *
 * <p>Everything else in this module is a widget: it takes a {@code ValueContext}, returns a
 * {@code javafx.scene.Node}, and assumes an editor is running. Both of those dependencies are
 * {@code provided}, which is right for a plugin — the host supplies them — and false for a bot, which has
 * only what its plugins bring at {@code compile} scope. So {@code com.botmaker.plugin.toolkit.config} is the
 * one package here that must name neither, and this test is what makes that a rule rather than a habit.
 *
 * <p><b>It is a source scan, not a classpath scan, and that is deliberate.</b> A {@code provided} dependency
 * is on this module's own test classpath, so every one of these classes loads perfectly here whatever it
 * names — the failure only appears in a stranger's bot, as a {@code NoClassDefFoundError} at the first
 * settings read. That is the same shape as the three {@code optional}-means-not-transitive bugs this project
 * has shipped, and the same reason {@code StudioSourcesTest} reads source: the way to break the rule is
 * invisible to a build that has the thing being banned.
 */
class ToolkitConfigIsBotSafeTest {

    private static final Path PACKAGE =
            Path.of("src/main/java/com/botmaker/plugin/toolkit/config");

    /** What a bot's classpath does not have. Javadoc mentions are fine; a source reference is not. */
    private static final List<String> BANNED = List.of("com.botmaker.plugin.api", "javafx.");

    @Test
    void thePackageIsWhereItSaysItIs() throws IOException {
        assertTrue(Files.isDirectory(PACKAGE),
                "expected " + PACKAGE.toAbsolutePath() + " — has the package moved?");
        assertEquals(3, javaFiles().size(), "Settings, ProjectValues and ValueGrammar");
    }

    @Test
    void nothingHereNamesATypeABotWillNotHave() throws IOException {
        List<String> offences = new ArrayList<>();
        for (Path file : javaFiles()) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isComment(line)) continue;
                for (String banned : BANNED) {
                    if (line.contains(banned)) {
                        offences.add(file.getFileName() + ":" + (i + 1) + " names " + banned);
                    }
                }
            }
        }
        assertEquals(List.of(), offences,
                "com.botmaker.plugin.toolkit.config runs in a bot, which has neither the contract nor JavaFX");
    }

    /**
     * Whether the line is javadoc or a comment. Crude on purpose: a false <em>negative</em> here is a
     * spurious failure somebody reads and fixes, while the alternative — parsing Java — is a second compiler.
     */
    private static boolean isComment(String line) {
        String trimmed = line.strip();
        return trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//");
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.list(PACKAGE)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }
}
