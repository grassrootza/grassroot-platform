UPDATE event_log SET message = 'YES' WHERE message = 'Yes';
UPDATE event_log SET message = 'NO' where message = 'No';
UPDATE event_log SET message = 'MAYBE' where message = 'Maybe';
UPDATE event_log SET message = 'INVALID_RESPONSE' where message = 'Invalid RSVP';
UPDATE event_log SET message = 'NO_RESPONSE' where message = 'No response yet';
UPDATE event_log SET message = null WHERE length(message) > 50;

ALTER TABLE event_log RENAME COLUMN message TO response;
ALTER TABLE event_log ALTER COLUMN response TYPE VARCHAR(50);