ALTER TABLE event ADD COLUMN rsvprequired boolean;
ALTER TABLE event ALTER COLUMN rsvprequired SET DEFAULT FALSE;
update event set rsvprequired = false;