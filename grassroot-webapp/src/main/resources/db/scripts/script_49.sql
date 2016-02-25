ALTER TABLE group_profile ADD COLUMN uid varchar(50);
UPDATE group_profile SET uid = 'auto_' || nextval('group_profile_id_seq');
ALTER TABLE group_profile ALTER COLUMN uid SET NOT NULL;
ALTER TABLE ONLY group_profile ADD CONSTRAINT uk_group_uid UNIQUE (uid);


