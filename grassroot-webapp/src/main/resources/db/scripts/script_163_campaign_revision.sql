alter table campaign_message add column active boolean default true;
alter table campaign_message add column deactive_time timestamp;

alter table campaign_message add column action_type varchar(50);
update campaign_message set action_type = 'OPENING';
alter table campaign_message alter column action_type set not null;

create index campaign_msg_type on campaign_message(action_type);
create index campaign_lang_type on campaign_message(campaign_id, locale, action_type, active);

alter table campaign_message add column next_actions text[] default '{}';
create index campaign_msg_actions on campaign_message using gin(next_actions);