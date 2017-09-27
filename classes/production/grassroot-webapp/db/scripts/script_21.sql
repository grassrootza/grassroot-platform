ALTER TABLE group_profile ADD COLUMN active boolean;
ALTER TABLE group_profile ALTER COLUMN active SET DEFAULT TRUE;
UPDATE group_profile SET active = TRUE;