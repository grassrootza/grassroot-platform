alter table campaign drop column account_uid;
alter table campaign drop constraint if exists fk_campaign_account;

alter table campaign add column account_id bigint;

-- set to created by user
update campaign set account_id = (select primary_account from user_profile where campaign.created_by_user = user_profile.id);

-- drop as irredemiably lost any that we can't set this for
delete from campaign_message where campaign_id in (select id from campaign where account_id is null);
delete from campaign_log where campaign_id in (select id from campaign where account_id is null);
delete from campaign where account_id is null;

alter table campaign alter column account_id set not null;

alter table campaign ADD CONSTRAINT fk_campaign_account_id FOREIGN KEY (account_id) REFERENCES paid_account(id);