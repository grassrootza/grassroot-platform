ALTER TABLE event ADD COLUMN can_relay boolean;
ALTER TABLE event ALTER COLUMN can_relay SET DEFAULT FALSE;
update event set can_relay = false;