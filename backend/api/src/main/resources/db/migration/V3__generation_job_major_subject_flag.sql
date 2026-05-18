ALTER TABLE generation_jobs
    ADD COLUMN publish_new_major_subject BOOLEAN NOT NULL DEFAULT FALSE;
