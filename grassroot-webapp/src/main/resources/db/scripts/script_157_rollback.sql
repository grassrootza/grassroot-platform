drop index uk_email_address_lower;
alter table user_profile drop constraint uk_email_address_constraint;

alter table user_profile drop column province;

ALTER TABLE user_profile ALTER COLUMN phone_number set NOT NULL;

alter table group_log drop constraint fk_group_log_target_user_id;
alter table group_log drop constraint fk_group_log_target_group_id;
alter table group_log drop constraint fk_group_log_target_account_id;

alter table group_log drop column target_user_id;
alter table group_log drop column target_group_id;
alter table group_log drop column target_account_id;