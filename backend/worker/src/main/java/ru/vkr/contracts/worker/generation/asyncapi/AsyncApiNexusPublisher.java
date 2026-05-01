package ru.vkr.contracts.worker.generation.asyncapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.vkr.contracts.worker.generation.core.publish.NexusPublisher;

import java.net.http.HttpClient;
import java.nio.file.Path;

@Component
public class AsyncApiNexusPublisher {
    private final NexusPublisher delegate;

    @Autowired
    public AsyncApiNexusPublisher(
            @Value("${generation.async-api.nexus.base-url:http://localhost:8081}") String nexusBaseUrl,
            @Value("${generation.async-api.nexus.repository:maven-releases}") String nexusRepository,
            @Value("${generation.async-api.nexus.username:}") String nexusUsername,
            @Value("${generation.async-api.nexus.password:}") String nexusPassword
    ) {
        this.delegate = new NexusPublisher(
                nexusBaseUrl,
                nexusRepository,
                nexusUsername,
                nexusPassword,
                HttpClient.newBuilder().build()
        );
    }

    public AsyncApiNexusPublisher(
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
