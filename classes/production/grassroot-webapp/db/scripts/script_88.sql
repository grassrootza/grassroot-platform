alter table notification add column account_log_id bigint;
ALTER TABLE ONLY notification ADD CONSTRAINT fk_notification_account_log FOREIGN KEY (account_log_id) REFERENCES account_log(id);

alter table notification add column account_id bigint;
ALTER TABLE ONLY notification ADD CONSTRAINT fk_notification_account FOREIGN KEY (account_id) REFERENCES paid_account(id);
