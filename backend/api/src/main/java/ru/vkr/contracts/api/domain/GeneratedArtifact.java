package ru.vkr.contracts.api.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "generated_artifacts")
public class GeneratedArtifact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", unique = true)
    private GenerationJob job;

    @Column(nullable = false)
    private String coordinates;

    @Column(nullable = false)
    private String publicationUrl;

    @Column
    private String schemaSubject;

    @Column(nullable = false)
    private Instant createdAt;

    protected GeneratedArtifact() {
    }

    public GeneratedArtifact(GenerationJob job, String coordinates, String publicationUrl, String schemaSubject) {
        this.job = job;
        this.coordinates = coordinates;
        this.publicationUrl = publicationUrl;
        this.schemaSubject = schemaSubject;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getJobId() {
        return job.getId();
    }

    public GenerationJob getJob() {
        return job;
    }

    public String getCoordinates() {
        return coordinates;
    }

    public String getPublicationUrl() {
        return publicationUrl;
    }

    public String getSchemaSubject() {
        return schemaSubject;
    }
}
