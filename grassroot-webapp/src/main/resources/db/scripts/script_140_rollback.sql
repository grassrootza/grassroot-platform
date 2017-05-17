delete from notification where livewire_log_id is not null;
alter table notification drop column livewire_log_id;

drop table live_wire_log;