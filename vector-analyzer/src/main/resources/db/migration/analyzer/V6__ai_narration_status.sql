-- Phase 6: AI narration status tracking. Adds explicit lifecycle column so
-- the frontend can distinguish "still generating" from "AI failed and gave up".
-- Also tracks how many user-triggered regenerations have run, used to enforce
-- the 1-retry-per-crash limit.

ALTER TABLE analyzer.crash_analysis
    ADD COLUMN ai_narration_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN ai_regenerate_count   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN ai_failure_reason     TEXT;

-- Existing rows from Phase 5 already have a populated narration when migration
-- runs in a fresh dev DB (rare). Guard them so the UI doesn't re-trigger.
UPDATE analyzer.crash_analysis
   SET ai_narration_status = 'AVAILABLE'
 WHERE ai_narration IS NOT NULL;
