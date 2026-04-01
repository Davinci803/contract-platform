package ru.vkr.contracts.api.domain;

import jakarta.persistence.*;
import ru.vkr.contracts.shared.model.CompatibilityLevel;

import java.time.Instant;

@Entity
@Table(name = "compatibility_reports")
public class CompatibilityReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_version_id")
    private ContractVersion contractVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompatibilityLevel level;

    @Column(nullable = false)
    private String semverRecommendation;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String findings;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String migrationAdvice;

    @Column(nullable = false)
    private Instant createdAt;

    protected CompatibilityReport() {
    }

    public CompatibilityReport(
            ContractVersion contractVersion,
            CompatibilityLevel level,
            String semverRecommendation,
            String findings,
            String migrationAdvice
    ) {
        this.contractVersion = contractVersion;
        this.level = level;
        this.semverRecommendation = semverRecommendation;
        this.findings = findings;
        this.migrationAdvice = migrationAdvice;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CompatibilityLevel getLevel() {
        return level;
    }

    public String getSemverRecommendation() {
        return semverRecommendation;
    }

    public String getFindings() {
        return findings;
    }

    public String getMigrationAdvice() {
        return migrationAdvice;
    }
}
