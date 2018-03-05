alter table campaign drop column account_id;
alter table campaign drop column fk_campaign_account_id;

alter table campaign add column account_uid varchar(50);
alter table campaign ADD CONSTRAINT fk_campaign_account FOREIGN KEY (account_uid) REFERENCES paid_account(uid);