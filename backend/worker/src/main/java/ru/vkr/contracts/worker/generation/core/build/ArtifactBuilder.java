package ru.vkr.contracts.worker.generation.core.build;

import org.springframework.stereotype.Component;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

@Component
public class ArtifactBuilder {

    public Path buildJar(GenerationPaths paths, String artifactId, String version, StringBuilder log) throws IOException {
        appendStage(log, "build", "compiling generated sources");
        Files.createDirectories(paths.targetRoot());
        Path classesDir = paths.targetRoot().resolve("classes");
        Files.createDirectories(classesDir);
        compileJavaSources(paths.sourceRoot(), classesDir);
        Path jarFile = paths.targetRoot().resolve(artifactId + "-" + version + ".jar");
        writeJar(classesDir, jarFile);
        appendStage(log, "build", "jar created: " + jarFile.getFileName());
        return jarFile;
    }

    public Path buildPom(Path targetRoot, String groupId, String artifactId, String version, StringBuilder log) throws IOException {
        Files.createDirectories(targetRoot);
        String pom = """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(groupId, artifactId, version);
        Path pomFile = targetRoot.resolve(artifactId + "-" + version + ".pom");
        Files.writeString(pomFile, pom, StandardCharsets.UTF_8);
        appendStage(log, "build", "pom created: " + pomFile.getFileName());
        return pomFile;
    }

    private void compileJavaSources(Path sourceRoot, Path outputDir) throws IOException {
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            javaFiles = walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
        if (javaFiles.isEmpty()) {
            throw new IllegalStateException("No generated Java files for build stage");
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler available in runtime");
        }
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromPaths(javaFiles);
            List<String> options = List.of("-d", outputDir.toString(), "-encoding", StandardCharsets.UTF_8.name());
            Boolean result = compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();
            if (!Boolean.TRUE.equals(result)) {
                throw new IllegalStateException("Generated sources compilation failed");
            }
        }
    }

    private void writeJar(Path classesDir, Path jarFile) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
            try (Stream<Path> walk = Files.walk(classesDir)) {
                for (Path path : walk.filter(Files::isRegularFile).toList()) {
                    String entryName = classesDir.relativize(path).toString().replace('\\', '/');
                    JarEntry entry = new JarEntry(entryName);
                    entry.setTime(Instant.now().toEpochMilli());
                    jarOutputStream.putNextEntry(entry);
                    jarOutputStream.write(Files.readAllBytes(path));
                    jarOutputStream.closeEntry();
                }
            }
        }
    }

    private void appendStage(StringBuilder log, String stage, String message) {
        if (!log.isEmpty()) {
            log.append('\n');
        }
        log.append('[').append(stage).append("] ").append(message);
    }
}
