import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class AnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testAllSamples() throws Exception {
        Path resourcesPath = Paths.get("src/test/resources/test-inputs");
        assertTrue(Files.exists(resourcesPath), "Resources path should exist");

        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(resourcesPath)) {
            javaFiles = walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        assertFalse(javaFiles.isEmpty(), "Should find test files");

        for (Path javaFile : javaFiles) {
            System.out.println("Testing " + javaFile.getFileName());
            compileAndRun(javaFile);
        }
    }

    private void compileAndRun(Path javaFile) throws IOException, ClassNotFoundException {
        // Compile
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
                "-d", tempDir.toString(),
                javaFile.toString());

        assertEquals(0, result, "Compilation failed for " + javaFile);

        // Determine class name (assuming simple structure from filename or content)
        // The files are like 'test1.java' containing 'package test; class test1 ...'
        // So the class file will be in tempDir/test/test1.class

        // We need to pass the class file path to App
        // Find the .class file
        List<Path> classFiles;
        try (Stream<Path> walk = Files.walk(tempDir)) {
             classFiles = walk
                    .filter(p -> p.toString().endsWith(".class"))
                    .collect(Collectors.toList());
        }

        String fileName = javaFile.getFileName().toString();
        String className = fileName.substring(0, fileName.length() - 5);
        Path classFilePath = tempDir.resolve("test").resolve(className + ".class");

        assertTrue(Files.exists(classFilePath), "Class file not found: " + classFilePath);

        // Run App
        App app = new App();
        app.run(new String[]{classFilePath.toAbsolutePath().toString()});
    }
}
