package ru.vkr.contracts.worker.generation.openapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.vkr.contracts.worker.generation.core.publish.NexusPublisher;

import java.net.http.HttpClient;
import java.nio.file.Path;

@Component
public class OpenApiNexusPublisher {
    private final NexusPublisher delegate;

    @Autowired
    public OpenApiNexusPublisher(
            @Value("${generation.open-api.nexus.base-url:http://localhost:8081}") String nexusBaseUrl,
            @Value("${generation.open-api.nexus.repository:maven-releases}") String nexusRepository,
            @Value("${generation.open-api.nexus.username:}") String nexusUsername,
            @Value("${generation.open-api.nexus.password:}") String nexusPassword
    ) {
        this.delegate = new NexusPublisher(
                nexusBaseUrl,
                nexusRepository,
                nexusUsername,
                nexusPassword,
                HttpClient.newBuilder().build()
        );
    }

    public OpenApiNexusPublisher(
            String nexusBaseUrl,
            String nexusRepository,
            String nexusUsername,
            String nexusPassword,
            HttpClient httpClient
    ) {
        this.delegate = new NexusPublisher(
                nexusBaseUrl,
                nexusRepository,
                nexusUsername,
                nexusPassword,
                httpClient
        );
    }

    public String publish(String groupId, String artifactId, String version, Path jarFile, Path pomFile, StringBuilder log) {
        return delegate.publish(groupId, artifactId, version, jarFile, pomFile, log);
    }
}
