ALTER TABLE user_profile ALTER COLUMN phone_number DROP NOT NULL;

create temporary table to_delete (email_address varchar not null, min_id bigint not null);
update user_profile set email_address = null where
  exists (select * from to_delete where to_delete.email_address = user_profile.email_address and to_delete.min_id <> user_profile.id);
drop table to_delete;

ALTER TABLE user_profile ADD CONSTRAINT uk_email_address_constraint UNIQUE (email_address);
create unique index uk_email_address_lower on user_profile(lower(email_address));