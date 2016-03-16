DELETE FROM event WHERE applies_to_group IS NULL;

ALTER TABLE event ADD COLUMN type varchar(50);
UPDATE event SET type = 'MEETING' where event_type = 0 or event_type = 2 or event_type = 3;
UPDATE event SET type = 'VOTE' where event_type = 1;
ALTER TABLE event ALTER COLUMN type SET NOT NULL;
ALTER TABLE event DROP COLUMN event_type;

ALTER TABLE event ADD COLUMN uid varchar(50);
UPDATE event SET uid = 'auto_' || nextval('event_id_seq');
ALTER TABLE event ALTER COLUMN uid SET NOT NULL;
ALTER TABLE ONLY event ADD CONSTRAINT uk_event_uid UNIQUE (uid);

UPDATE event SET noreminderssent = 0 WHERE noreminderssent is null;

ALTER TABLE event DROP COLUMN date_time_string;
ALTER TABLE event ALTER COLUMN name SET NOT NULL;
ALTER TABLE event ALTER COLUMN created_date_time SET NOT NULL;
ALTER TABLE event ALTER COLUMN created_by_user SET NOT NULL;
ALTER TABLE event ALTER COLUMN includesubgroups SET NOT NULL;
ALTER TABLE event ALTER COLUMN rsvprequired SET NOT NULL;
ALTER TABLE event ALTER COLUMN can_relay SET NOT NULL;
ALTER TABLE event ALTER COLUMN send_blocked SET NOT NULL;
ALTER TABLE event ALTER COLUMN noreminderssent SET NOT NULL;

ALTER TABLE event ADD COLUMN reminder_type varchar(50);
UPDATE event SET reminder_type = 'DISABLED';
ALTER TABLE event ALTER COLUMN reminder_type SET NOT NULL;

ALTER TABLE event ADD COLUMN custom_reminder_minutes int4;
UPDATE event SET custom_reminder_minutes = 0;
ALTER TABLE event ALTER COLUMN custom_reminder_minutes SET NOT NULL;

