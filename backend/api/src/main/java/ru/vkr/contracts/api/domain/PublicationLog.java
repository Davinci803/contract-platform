package ru.vkr.contracts.api.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "publication_logs")
public class PublicationLog {
    public static final String ERROR_CATEGORY_NONE = "NONE";
    public static final String ERROR_CATEGORY_TECHNICAL = "TECHNICAL";
    public static final String ERROR_CATEGORY_BUSINESS = "BUSINESS";

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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String errorCategory;

    @Column(nullable = false, length = 64)
    private String correlationId;

    @Column(nullable = false)
    private Instant createdAt;

    protected PublicationLog() {
    }

    public PublicationLog(GenerationJob job, String target, String status, String message) {
        this(
                job,
                target,
                status,
                message,
                "GENERAL",
                status != null && status.startsWith("FAILED_") ? ERROR_CATEGORY_TECHNICAL : ERROR_CATEGORY_NONE
        );
    }

    public PublicationLog(
            GenerationJob job,
            String target,
            String status,
            String message,
            String eventType,
            String errorCategory
    ) {
        this.job = job;
        this.target = target;
        this.status = status;
        this.message = message;
        this.eventType = eventType;
        this.errorCategory = errorCategory;
        this.correlationId = job.getCorrelationId();
        this.createdAt = Instant.now();
    }

    public String getTarget() {
        return target;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getEventType() {
        return eventType;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
