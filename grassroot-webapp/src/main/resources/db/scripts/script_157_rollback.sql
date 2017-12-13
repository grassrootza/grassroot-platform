drop index uk_email_address_lower;
alter table user_profile drop constraint uk_email_address_constraint;

ALTER TABLE user_profile ALTER COLUMN phone_number set NOT NULL;