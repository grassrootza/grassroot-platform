ALTER TABLE event ADD COLUMN send_blocked boolean DEFAULT false;
UPDATE event SET send_blocked = false;