alter table campaign_message drop column message_group_id;

update campaign_message set action_type = 'SHARE' where action_type = 'SHARE_PROMPT';

alter table campaign drop column sharing_enabled;
alter table campaign drop column sharing_budget;
alter table campaign drop column sharing_spent;

alter table campaign rename column landing_url to url;
alter table campaign drop column petition_api;
alter table campaign drop column petition_result_api;

alter table campaign drop column account_uid;
alter table campaign drop column image_record_uid;

drop index campaign_code_index;

alter table campaign_log drop column user_interface_channel;

alter table user_profile drop column whatsapp;

create table campaign_message_action (
  id bigserial not null,
  version integer,
  uid varchar(50) not null,
  created_date_time timestamp not null,
  parent_message_id bigserial,
  created_by_user bigint,
  action_message_id bigserial,
  action varchar(35),
  primary key (id));

alter table campaign_message_action ADD CONSTRAINT fk_action_message_id FOREIGN KEY (action_message_id) REFERENCES campaign_message(id);
alter table campaign_message_action ADD CONSTRAINT fk_parent_message_id FOREIGN KEY (parent_message_id) REFERENCES campaign_message(id);

alter table group_profile alter column description type varchar(255);
alter table event_request alter column name type varchar(40);