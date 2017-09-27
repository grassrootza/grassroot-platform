ALTER TABLE event_log RENAME COLUMN response TO message;
ALTER TABLE event_log ALTER COLUMN message TYPE VARCHAR(255);

UPDATE event_log SET message = 'Yes' WHERE message = 'YES';
UPDATE event_log SET message = 'No' where message = 'NO';
UPDATE event_log SET message = 'Maybe' where message = 'MAYBE';
UPDATE event_log SET message = 'Invalid RSVP' where message = 'INVALID_RESPONSE';
UPDATE event_log SET message = 'No response yet' where message = 'NO_RESPONSE';