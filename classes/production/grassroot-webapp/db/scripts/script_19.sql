ALTER TABLE group_profile ADD COLUMN default_language character varying[10];
update group_profile set default_language = null;