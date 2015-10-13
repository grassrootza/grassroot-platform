ALTER TABLE event ADD COLUMN noreminderssent integer;
ALTER TABLE event ALTER COLUMN noreminderssent SET DEFAULT 0;
update event set noreminderssent = 0;