alter table address_log add column type varchar(50);
update address_log set type = 'REVISED_DESCRIPTION'; -- arbitrary, as none added prior to this script
alter table address_log alter column type set not null;

alter table address_log add column user_id bigint;
alter table address_log add constraint fk_address_log_user foreign key (user_id) references user_profile;

alter table address add column is_primary boolean default false;
update address set is_primary = true; -- will have to manually delete duplicates

alter table live_wire_alert add column tags text[] default '{}';
alter table live_wire_alert add column complete boolean default false;
alter table live_wire_alert add column reviewed boolean default false;
alter table live_wire_alert add column reviewed_by_user_id bigint;
alter table live_wire_alert add column version integer default 0;

create index lwalert_tag_index on live_wire_alert using gin(tags);
alter table live_wire_alert add constraint fk_lwire_reviewed_user foreign key (reviewed_by_user_id) references user_profile;

alter table event add column tags text[] default '{}';
create index event_tags_index on event using gin(tags);

alter table event_request add column tags text[] default '{}';
create index event_req_tags_index on event_request using gin(tags);

alter table group_profile add column tags text[] default '{}';
create index group_tags_index on group_profile using gin(tags);

alter table user_profile add column livewire_contact boolean default false;

alter table data_subscriber add column can_tag boolean default false;
alter table data_subscriber add column can_release boolean default false;
alter table data_subscriber add column version integer default 0;

insert into role (role_name, role_type) values ('ROLE_LIVEWIRE_USER', 'STANDARD');