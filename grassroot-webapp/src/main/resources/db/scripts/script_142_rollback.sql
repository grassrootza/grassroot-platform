alter table live_wire_alert drop constraint fk_lwire_target_sub;

alter table live_wire_alert drop column destination_type;
alter table live_wire_alert drop column subscriber_id;

alter table data_subscriber drop column subscriber_type;