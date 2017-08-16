drop table livewire_media_files;
drop table media_file;

alter table only live_wire_alert drop column description;
alter table only live_wire_alert rename column headline to description;
