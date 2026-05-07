CREATE OR REPLACE FUNCTION notify_deployment_event()
RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('deployment_events', NEW.id::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
