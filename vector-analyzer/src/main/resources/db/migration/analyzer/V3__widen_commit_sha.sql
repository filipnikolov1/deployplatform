-- Widen commit_sha from VARCHAR(40) to TEXT to handle any edge-case data.
ALTER TABLE analyzer.timeline_event ALTER COLUMN commit_sha TYPE TEXT;
