ALTER TABLE event ADD COLUMN version integer;
ALTER TABLE event ALTER COLUMN version SET DEFAULT 0;
update event set version = 0;
ALTER TABLE group_profile ADD COLUMN version integer;
ALTER TABLE group_profile ALTER COLUMN version SET DEFAULT 0;
update group_profile set version = 0;

