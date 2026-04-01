package ru.vkr.contracts.api.domain;

import jakarta.persistence.*;
import ru.vkr.contracts.shared.model.JobStatus;

import java.time.Instant;

@Entity
@Table(name = "generation_jobs")
public class GenerationJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_version_id")
    private ContractVersion contractVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String log;

    protected GenerationJob() {
    }

    public GenerationJob(ContractVersion contractVersion) {
        this.contractVersion = contractVersion;
        this.status = JobStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public ContractVersion getContractVersion() {
        return contractVersion;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getLog() {
        return log;
    }

    public void markRunning() {
        this.status = JobStatus.RUNNING;
        this.updatedAt = Instant.now();
    }

    public void markSuccess(String log) {
        this.status = JobStatus.SUCCESS;
        this.log = log;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String log) {
        this.status = JobStatus.FAILED;
        this.log = log;
        this.updatedAt = Instant.now();
    }
}
