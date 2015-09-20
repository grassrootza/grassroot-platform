ALTER TABLE event ADD COLUMN reminderminutes integer;
ALTER TABLE event ALTER COLUMN reminderminutes SET DEFAULT 0;
update event set reminderminutes = 0;