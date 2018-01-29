alter table campaign_message drop column active;
alter table campaign_message drop column deactive_time;

alter table campaign_message drop column action_type;

drop index campaign_msg_type;
drop index campaign_lang_type;

alter table campaign_message drop column next_actions;
