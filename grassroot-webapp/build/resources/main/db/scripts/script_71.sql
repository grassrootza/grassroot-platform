DELETE FROM event_log WHERE event_id IN (SELECT id FROM event WHERE start_date_time IS NULL);
DELETE FROM event WHERE start_date_time IS NULL;
ALTER TABLE event ALTER COLUMN start_date_time SET NOT NULL;