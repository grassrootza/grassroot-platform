alter table notification drop column gcm_registration_id;
alter table notification add column group_log_id bigint;
alter table notification add column event_id bigint;
ALTER TABLE ONLY notification ADD CONSTRAINT fk_notification_group_log FOREIGN KEY (group_log_id) REFERENCES group_log(id);
ALTER TABLE ONLY notification ADD CONSTRAINT fk_event FOREIGN KEY (event_id) REFERENCES event(id);

alter table event_log drop column message_default;

alter table log_book_log drop column group_id;
alter table log_book_log drop column message_to;
