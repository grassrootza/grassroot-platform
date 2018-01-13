alter table user_profile add column province varchar(50);
ALTER TABLE user_profile ALTER COLUMN phone_number DROP NOT NULL;

create temporary table to_delete (email_address varchar not null, min_id bigint not null);
insert into to_delete(email_address, min_id)
  select email_address, MIN(id) from user_profile where email_address is not null group by email_address having count(*) > 1;
update user_profile set email_address = null where
  exists (select * from to_delete where to_delete.email_address = user_profile.email_address and to_delete.min_id <> user_profile.id);
drop table to_delete;

ALTER TABLE user_profile ADD CONSTRAINT uk_email_address_constraint UNIQUE (email_address);
create unique index uk_email_address_lower on user_profile(lower(email_address));

alter table group_log add column target_user_id bigint;
alter table group_log add column target_group_id bigint;
alter table group_log add column target_account_id bigint;

update group_log set target_user_id = user_or_sub_group_id
  where user_or_sub_group_id != 0 and group_log_type in (
    'GROUP_MEMBER_ADDED', 'GROUP_MEMBER_REMOVED', 'GROUP_MEMBER_ROLE_CHANGED',
    'GROUP_MEMBER_ADDED_VIA_JOIN_CODE', 'GROUP_MEMBER_ADDED_VIA_CAMPAIGN',
    'GROUP_MEMBER_ADDED_AT_CREATION', 'CHANGED_ALIAS', 'USER_SENT_UNKNOWN_RESPONSE'
);

update group_log set target_group_id = user_or_sub_group_id
  where user_or_sub_group_id != 0 and group_log_type in (
    'SUBGROUP_ADDED', 'SUBGROUP_REMOVED', 'PARENT_CHANGED'
);

-- note: corruption in prior account adds mean that can't run above for account based logs
-- so will have to just grandfather this change in

alter table group_log add constraint fk_group_log_target_user_id foreign key (target_user_id) references user_profile(id);
alter table group_log add constraint fk_group_log_target_group_id foreign key (target_group_id) references group_profile(id);
alter table group_log add constraint fk_group_log_target_account_id foreign key (target_account_id) references paid_account(id);
