ALTER TABLE group_profile ADD COLUMN description VARCHAR(255);
UPDATE group_profile SET description = '';
ALTER TABLE ONLY group_profile ALTER COLUMN description SET NOT NULL;
