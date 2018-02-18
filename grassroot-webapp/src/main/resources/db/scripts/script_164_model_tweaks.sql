delete from campaign_message;

alter table campaign_message add column message_group_id varchar(50) not null;
create index campaign_msg_sets_index on campaign_message(campaign_message);

alter table user_profile add column whatsapp boolean default false;
update user_profile set whatsapp = false;

create index whatsapp_index on user_profile(whatsapp);