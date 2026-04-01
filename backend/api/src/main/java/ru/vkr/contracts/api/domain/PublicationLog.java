package ru.vkr.contracts.api.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "publication_logs")
public class PublicationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private GenerationJob job;

    @Column(nullable = false)
    private String target;

    @Column(nullable = false)
    private String status;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    protected PublicationLog() {
    }

    public PublicationLog(GenerationJob job, String target, String status, String message) {
        this.job = job;
        this.target = target;
        this.status = status;
        this.message = message;
        this.createdAt = Instant.now();
    }
}
