CREATE OR REPLACE FUNCTION notify_deployment_event()
RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify(
        'deployment_events',
        json_build_object(
            'id',         NEW.id,
            'app_name',   NEW.app_name,
            'event_type', NEW.event_type,
            'status',     NEW.status,
            'created_at', NEW.created_at
        )::text
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notify_deployment_event
    AFTER INSERT ON deployment_event
    FOR EACH ROW EXECUTE FUNCTION notify_deployment_event();
