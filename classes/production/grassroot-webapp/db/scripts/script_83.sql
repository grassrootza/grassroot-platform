alter table log_book add column ancestor_group_id bigint;
alter table log_book ADD CONSTRAINT fk_log_book_ancestor_group FOREIGN KEY (ancestor_group_id) REFERENCES group_profile(id);
update log_book set ancestor_group_id = parent_group_id;
alter table log_book ALTER COLUMN ancestor_group_id set not null;

alter table event add column ancestor_group_id bigint;
alter table event ADD CONSTRAINT fk_event_ancestor_group FOREIGN KEY (ancestor_group_id) REFERENCES group_profile(id);
update event set ancestor_group_id = parent_group_id;
alter table event ALTER COLUMN ancestor_group_id set not null;

-- setting some constraints on log tables...

delete from group_log where group_id is null;
alter table group_log ALTER COLUMN group_id set not null;
alter table group_log ALTER COLUMN created_date_time set not null;
alter table group_log ALTER COLUMN group_log_type set not null;

delete from event_log where event_id is null;
alter table event_log ALTER COLUMN event_id set not null;
alter table event_log ALTER COLUMN created_date_time set not null;
alter table event_log ALTER COLUMN event_log_type set not null;

delete from log_book_log where logbook_id is null;
alter table log_book_log ALTER COLUMN logbook_id set not null;
alter table log_book_log ALTER COLUMN created_date_time set not null;
