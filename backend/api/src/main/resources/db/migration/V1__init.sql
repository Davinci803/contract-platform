CREATE TABLE contracts
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    type       VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE TABLE contract_versions
(
    id          BIGSERIAL PRIMARY KEY,
    contract_id BIGINT       NOT NULL REFERENCES contracts (id),
    version     VARCHAR(64)  NOT NULL,
    content     TEXT         NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL
);

CREATE TABLE generation_jobs
(
    id                  BIGSERIAL PRIMARY KEY,
    contract_version_id BIGINT      NOT NULL REFERENCES contract_versions (id),
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP,
    log                 TEXT
);

CREATE TABLE generated_artifacts
(
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT       NOT NULL UNIQUE REFERENCES generation_jobs (id),
    coordinates     VARCHAR(512) NOT NULL,
    publication_url VARCHAR(1024) NOT NULL,
    schema_subject  VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL
);

CREATE TABLE compatibility_reports
(
    id                   BIGSERIAL PRIMARY KEY,
    contract_version_id  BIGINT      NOT NULL REFERENCES contract_versions (id),
    level                VARCHAR(32) NOT NULL,
    semver_recommendation VARCHAR(16) NOT NULL,
    findings             TEXT        NOT NULL,
    migration_advice     TEXT        NOT NULL,
    created_at           TIMESTAMP   NOT NULL
);

CREATE TABLE publication_logs
(
    id         BIGSERIAL PRIMARY KEY,
    job_id     BIGINT       NOT NULL REFERENCES generation_jobs (id),
    target     VARCHAR(64)  NOT NULL,
    status     VARCHAR(32)  NOT NULL,
    message    TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_contract_versions_contract_id ON contract_versions (contract_id);
CREATE INDEX idx_generation_jobs_contract_version_id ON generation_jobs (contract_version_id);
CREATE INDEX idx_compatibility_reports_version_id ON compatibility_reports (contract_version_id);
