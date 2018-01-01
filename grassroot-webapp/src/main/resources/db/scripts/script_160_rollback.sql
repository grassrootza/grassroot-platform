alter table broadcast drop column title;
alter table broadcast drop column tags;
alter table broadcast rename broadcast_schedule to broadcast_type;
alter table broadcast drop column scheduled_send_time;
alter table broadcast drop column sent_time;

alter table broadcast drop column skip_sms_if_email;
alter table broadcast drop column email_delivery_route;
alter table broadcast drop column email_image_key;

alter table broadcast rename facebook_link_url to facebook_link;
alter table broadcast drop column facebook_link_name;

alter table group_log drop column broadcast_id;
alter table account_log drop column broadcast_id;
alter table campaign_log drop column broadcast_id;

alter table notification drop column campaign_log_id;
alter table notification drop column broadcast_id;

update user_profile set message_preference = 'EMAIL' where message_preference = 'EMAIL_GRASSROOT';
update notification set delivery_channel = 'EMAIL' where delivery_channel = 'EMAIL_GRASSROOT';