alter table address_log drop column type;
alter table address_log drop column user_id;

alter table address drop column is_primary;

alter table live_wire_alert drop column tags;
alter table live_wire_alert drop column reviewed;