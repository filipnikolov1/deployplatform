-- Add parent_sha to commit_cache so the analyzer can walk the commit graph
-- without an extra GitHub API call. Root commits have no parent, hence nullable.
ALTER TABLE analyzer.commit_cache ADD COLUMN parent_sha VARCHAR(80);
