package ru.vkr.contracts.api.domain;

import jakarta.persistence.*;
import ru.vkr.contracts.shared.model.ContractType;

import java.time.Instant;

@Entity
@Table(name = "contracts")
public class EntityContract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractType type;

    @Column(nullable = false)
    private Instant createdAt;

    protected EntityContract() {
    }

    public EntityContract(String name, ContractType type) {
        this.name = name;
        this.type = type;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ContractType getType() {
        return type;
    }
}
