ALTER TABLE user_profile DROP COLUMN last_ussd_menu;

ALTER TABLE user_profile ADD COLUMN message_preference integer;
UPDATE user_profile SET message_preference = 0;
ALTER TABLE user_profile ALTER COLUMN message_preference SET NOT NULL;

ALTER TABLE event_log ADD COLUMN uid varchar(50);
UPDATE event_log SET uid = 'auto_' || nextval('event_log_id_seq');
ALTER TABLE event_log ALTER COLUMN uid SET NOT NULL;
ALTER TABLE ONLY event_log ADD CONSTRAINT uk_eventlog_uid UNIQUE (uid);

ALTER TABLE event_log ADD COLUMN message_default integer;
UPDATE event_log SET message_default = 0;
ALTER TABLE event_log ALTER COLUMN message_default SET NOT NULL;