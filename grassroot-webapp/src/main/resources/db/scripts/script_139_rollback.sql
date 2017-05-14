alter table address_log drop column type;
alter table address_log drop column user_id;

alter table address drop column is_primary;

alter table live_wire_alert drop column tags;
alter table live_wire_alert drop column reviewed;
alter table live_wire_alert drop column reviewed_by_user_id;

alter table event drop column tags;
alter table event_request drop column tags;
alter table group_profile drop column tags;

alter table user_profile drop column livewire_contact;

alter table data_subscriber drop column can_tag;
alter table data_subscriber drop column can_release;

delete from role where role_name = 'ROLE_LIVEWIRE_USER' and role_type = 'STANDARD';