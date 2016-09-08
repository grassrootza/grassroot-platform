delete from notification;

alter table notification add column user_log_id bigint;
ALTER TABLE ONLY notification ADD CONSTRAINT fk_notification_user_log FOREIGN KEY (user_log_id) REFERENCES user_log(id);

alter table notification add column last_attempt_time timestamp;
alter table notification add column next_attempt_time timestamp;
alter table notification add column attempt_count int4 not null;
