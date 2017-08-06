alter table live_wire_alert add column destination_type varchar (50);
alter table live_wire_alert add column subscriber_id bigint;

update live_wire_alert set destination_type = 'PUBLIC_LIST';
alter table live_wire_alert alter column destination_type set not null;

alter table data_subscriber add column subscriber_type varchar(50);
update data_subscriber set subscriber_type = 'SYSTEM'; -- default for now (should then be adjusted)
alter table data_subscriber alter column subscriber_type set not null;

alter table live_wire_alert add constraint fk_lwire_target_sub foreign key (subscriber_id) references data_subscriber;