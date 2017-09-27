ALTER TABLE group_profile ADD COLUMN default_image varchar(50);
update group_profile set default_image = 'SOCIAL_MOVEMENT';