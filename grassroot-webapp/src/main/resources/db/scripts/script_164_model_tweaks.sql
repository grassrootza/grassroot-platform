delete from campaign_message;

alter table campaign_message add column message_group_id varchar(50) not null;
create index campaign_msg_sets_index on campaign_message(campaign_message);

alter table campaign add column sharing_enabled boolean default false;
alter table campaign add column sharing_budget bigint default 0;
alter table campaign add column sharing_spent bigint default 0;

alter table campaign rename column url to landing_url;
alter table campaign add column petition_api text;

alter table user_profile add column whatsapp boolean default false;
update user_profile set whatsapp = false;

create index whatsapp_index on user_profile(whatsapp);