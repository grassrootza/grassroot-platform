delete from campaign_message;

alter table campaign_message add column message_group_id varchar(50) not null;
create index campaign_msg_sets_index on campaign_message(message_group_id);

update campaign_message set action_type = 'SHARE_PROMPT' where action_type = 'SHARE';

alter table campaign add column sharing_enabled boolean default false;
alter table campaign add column sharing_budget bigint default 0;
alter table campaign add column sharing_spent bigint default 0;

alter table campaign rename column url to landing_url;
alter table campaign add column petition_api text;
alter table campaign add column petition_result_api text;

alter table campaign add column account_uid varchar(50);
alter table campaign ADD CONSTRAINT fk_campaign_account FOREIGN KEY (account_uid) REFERENCES paid_account(uid);

alter table campaign add column image_record_uid varchar(50);
alter table campaign add constraint fk_campaign_image foreign key (image_record_uid) references media_file(uid);

create index campaign_code_index on campaign(code);

alter table campaign_log add column user_interface_channel varchar(50);
create index campaign_log_channel_index on campaign_log(user_interface_channel);

drop table campaign_message_action;

alter table user_profile add column whatsapp boolean default false;
update user_profile set whatsapp = false;

create index whatsapp_index on user_profile(whatsapp);

alter table group_profile alter column description type text;
alter table event_request alter column name type varchar(255);