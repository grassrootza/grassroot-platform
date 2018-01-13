alter index uk_broadcast_uid rename to uk_n_template_uid;
alter index broadcast_pkey rename to notification_template_pkey;

alter table broadcast rename constraint fk_broadcast_account_id to fk_ntemplate_account_id;
alter table broadcast rename constraint fk_broadcast_group_id to fk_ntemplate_group_id;

alter table broadcast rename broadcast_type to tigger_type;

alter table broadcast drop column email_content;
alter table broadcast drop column facebook_page_id;
alter table broadcast drop column facebook_post;
alter table broadcast drop column facebook_link;
alter table broadcast drop column facebook_image_key;
alter table broadcast drop column facebook_image_caption;
alter table broadcast drop column facebook_post_succeeded;

alter table broadcast drop column twitter_post;
alter table broadcast drop column twitter_image_key;
alter table broadcast drop column twitter_post_succeeded;

alter table broadcast drop constraint fk_broadcast_campaign_id;
alter table broadcast drop column campaign_id;

alter table broadcast rename to notification_template;