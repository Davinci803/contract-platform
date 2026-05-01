package ru.vkr.contracts.worker.generation.core.publish;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

public class NexusPublisher {
    private final String nexusBaseUrl;
    private final String nexusRepository;
    private final String nexusUsername;
    private final String nexusPassword;
    private final HttpClient httpClient;

    public NexusPublisher(
            String nexusBaseUrl,
            String nexusRepository,
            String nexusUsername,
            String nexusPassword,
            HttpClient httpClient
    ) {
        this.nexusBaseUrl = stripTrailingSlash(nexusBaseUrl);
        this.nexusRepository = nexusRepository;
        this.nexusUsername = nexusUsername == null ? "" : nexusUsername;
        this.nexusPassword = nexusPassword == null ? "" : nexusPassword;
        this.httpClient = httpClient;
    }

    public String publish(String groupId, String artifactId, String version, Path jarFile, Path pomFile, StringBuilder log) {
        appendStage(log, "publish", "uploading artifact to Nexus");
        String basePath = nexusBaseUrl
                + "/repository/" + nexusRepository
                + "/" + groupId.replace('.', '/')
                + "/" + artifactId
                + "/" + version;
        String jarUrl = basePath + "/" + artifactId + "-" + version + ".jar";
        String pomUrl = basePath + "/" + artifactId + "-" + version + ".pom";
        uploadFile(jarUrl, jarFile, "application/java-archive");
        uploadFile(pomUrl, pomFile, "application/xml");
        appendStage(log, "publish", "uploaded jar and pom");
        return jarUrl;
    }

    private void uploadFile(String targetUrl, Path file, String contentType) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", contentType)
                    .PUT(HttpRequest.BodyPublishers.ofFile(file));
            if (!nexusUsername.isBlank()) {
                String token = Base64.getEncoder().encodeToString((nexusUsername + ":" + nexusPassword).getBytes(StandardCharsets.UTF_8));
                requestBuilder.header("Authorization", "Basic " + token);
            }
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Nexus upload failed [" + response.statusCode() + "] for " + targetUrl + ": " + trim(response.body()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Nexus upload I/O failure for " + targetUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Nexus upload interrupted for " + targetUrl, e);
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8081";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String trim(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 240 ? compact.substring(0, 240) + "..." : compact;
    }

    private void appendStage(StringBuilder log, String stage, String message) {
        if (!log.isEmpty()) {
            log.append('\n');
        }
        log.append('[').append(stage).append("] ").append(message);
    }
}
