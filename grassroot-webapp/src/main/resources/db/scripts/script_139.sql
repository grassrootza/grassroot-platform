alter table address_log add column type varchar(50);
update address_log set type = 'REVISED_DESCRIPTION'; -- arbitrary, as none added prior to this script
alter table address_log alter column type set not null;

alter table address_log add column user_id bigint;
alter table address_log add constraint fk_address_log_user foreign key (user_id) references user_profile;

alter table address add column is_primary boolean default false;
update address set is_primary = true; -- will have to manually delete duplicates

alter table live_wire_alert add column tags text[] not null default '{}';
alter table live_wire_alert add column reviewed boolean default false;
create index lwalert_tag_index on live_wire_alert using gin(tags);

-- create index dsub_push_emails on data_subscriber using gin(push_emails);