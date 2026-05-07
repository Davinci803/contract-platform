ALTER TABLE generation_jobs
    ADD COLUMN correlation_id VARCHAR(64);

UPDATE generation_jobs
SET correlation_id = 'legacy-job-' || id
WHERE correlation_id IS NULL;

ALTER TABLE generation_jobs
    ALTER COLUMN correlation_id SET NOT NULL;

ALTER TABLE publication_logs
    ADD COLUMN event_type VARCHAR(64);

ALTER TABLE publication_logs
    ADD COLUMN error_category VARCHAR(32);

ALTER TABLE publication_logs
    ADD COLUMN correlation_id VARCHAR(64);

UPDATE publication_logs
SET event_type = 'GENERAL'
WHERE event_type IS NULL;

UPDATE publication_logs
SET error_category = CASE
    WHEN status LIKE 'FAILED_%' THEN 'TECHNICAL'
    ELSE 'NONE'
END
WHERE error_category IS NULL;

UPDATE publication_logs p
SET correlation_id = j.correlation_id
FROM generation_jobs j
WHERE p.job_id = j.id
  AND p.correlation_id IS NULL;

ALTER TABLE publication_logs
    ALTER COLUMN event_type SET NOT NULL;

ALTER TABLE publication_logs
    ALTER COLUMN error_category SET NOT NULL;

ALTER TABLE publication_logs
    ALTER COLUMN correlation_id SET NOT NULL;

CREATE INDEX idx_generation_jobs_contract_version_created_at
    ON generation_jobs (contract_version_id, created_at DESC);

CREATE INDEX idx_generation_jobs_correlation_id
    ON generation_jobs (correlation_id);

CREATE INDEX idx_publication_logs_job_created_at
    ON publication_logs (job_id, created_at);

CREATE INDEX idx_publication_logs_correlation_created_at
    ON publication_logs (correlation_id, created_at);

CREATE INDEX idx_compatibility_reports_version_created_at
    ON compatibility_reports (contract_version_id, created_at DESC);
