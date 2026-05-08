-- Self-apps used to be auto-pinned by SelfAppBootstrap so the deploy webhook
-- would record UPDATE_AVAILABLE instead of auto-deploying them. We now want
-- non-API self-apps to auto-deploy on webhook. Clear the auto-pin so the
-- "pinned_image" column once again only reflects deliberate user pins.
--
-- vector-api stays effectively pinned in code (the webhook handler treats it
-- as always-pinned because it can't deploy itself in-place), so this is safe
-- without a special-case here.
UPDATE deployment
SET pinned_image = NULL,
    pinned_at    = NULL
WHERE is_self_app = TRUE;
