package ru.vkr.contracts.api.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "contract_versions")
public class ContractVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private EntityContract contract;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String authorName;

    @Column(nullable = false)
    private Instant createdAt;

    protected ContractVersion() {
    }

    public ContractVersion(EntityContract contract, String version, String content, String authorName) {
        this.contract = contract;
        this.version = version;
        this.content = content;
        this.authorName = authorName;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public EntityContract getContract() {
        return contract;
    }

    public String getVersion() {
        return version;
    }

    public String getContent() {
        return content;
    }
}
