drop table live_wire_media_files;
drop table media_file;

alter table only live_wire_alert drop column description;
alter table only live_wire_alert rename column headline to description;

alter table only group_profile drop column last_task_creation_time;
alter table only group_profile drop column last_log_creation_time;