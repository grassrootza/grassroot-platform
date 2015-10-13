ALTER TABLE group_profile ADD COLUMN reminderminutes integer;
ALTER TABLE group_profile ALTER COLUMN reminderminutes SET DEFAULT 0;
update group_profile set reminderminutes = 0;