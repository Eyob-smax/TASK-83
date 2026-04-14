ALTER TABLE crawl_jobs
    ADD COLUMN priority INT NOT NULL DEFAULT 100 AFTER status;

CREATE INDEX idx_crawl_job_queue ON crawl_jobs(status, priority, created_at);
