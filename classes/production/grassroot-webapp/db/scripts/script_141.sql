alter table event_log add column tag VARCHAR(255) default '';
update event_log set tag = '';
alter table event_log alter column tag set not null;
create index event_log_tag_index on event_log (tag);