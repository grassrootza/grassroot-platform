alter table broadcast add column title varchar(255);

alter table broadcast add column tags text[] default '{}';
update broadcast set tags = '{}';
create index broadcast_tags_index on broadcast using gin(tags);

alter table broadcast alter column msg_template1 drop not null;
alter table broadcast alter column send_delay drop not null;

alter table broadcast rename broadcast_type to broadcast_schedule;
alter table broadcast add column scheduled_send_time timestamp;
alter table broadcast add column sent_time timestamp;

alter table broadcast add column skip_sms_if_email boolean default false;
update broadcast set skip_sms_if_email = false;

alter table broadcast add column email_delivery_route varchar(50);
alter table broadcast add column email_image_key varchar(255);

alter table broadcast rename facebook_link to facebook_link_url;
alter table broadcast add column facebook_link_name varchar(255);

alter table group_log add column broadcast_id bigint;
alter table group_log add constraint fk_group_log_broadcast_id foreign key (broadcast_id) references broadcast(id);

alter table account_log add column broadcast_id bigint;
alter table account_log add constraint fk_account_log_broadcast_id foreign key (broadcast_id) references broadcast(id);

alter table campaign_log add column broadcast_id bigint;
alter table campaign_log add constraint fk_campaign_log_broadcast_id foreign key (broadcast_id) references broadcast(id);

alter table notification add column campaign_log_id bigint;
alter table notification add constraint fk_notification_campaign_log foreign key (campaign_log_id) references campaign_log(id);

alter table notification add column broadcast_id bigint;
alter table notification add constraint fk_notification_broadcast foreign key (broadcast_id) references broadcast(id);

update user_profile set message_preference = 'EMAIL_GRASSROOT' where message_preference = 'EMAIL';
update notification set delivery_channel = 'EMAIL_GRASSROOT' where delivery_channel = 'EMAIL';