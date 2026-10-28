-- Phase 7: support bounded rolling-window counts for per-account messaging limits.
-- The sender is the leading column because each rate-limit query filters one sender first.
CREATE INDEX conversation_message_sender_created_idx
    ON conversation_message (sender_id, created_at DESC);
