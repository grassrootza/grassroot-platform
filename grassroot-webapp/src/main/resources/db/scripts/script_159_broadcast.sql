alter table notification_template rename to broadcast;

alter index uk_n_template_uid rename to uk_broadcast_uid;
alter index notification_template_pkey rename to broadcast_pkey;

alter table broadcast rename constraint fk_ntemplate_account_id to fk_broadcast_account_id;
alter table broadcast rename constraint fk_ntemplate_group_id to fk_broadcast_group_id;

alter table broadcast rename trigger_type to broadcast_type;
alter table broadcast alter column account_id drop not null;

alter table broadcast add column email_content text;
alter table broadcast add column facebook_page_id varchar(255);
alter table broadcast add column facebook_post text;
alter table broadcast add column facebook_link varchar(255);
alter table broadcast add column facebook_image_key varchar(50);
alter table broadcast add column facebook_image_caption varchar(255);
alter table broadcast add column facebook_post_succeeded boolean;

alter table broadcast add column twitter_post varchar(240);
alter table broadcast add column twitter_image_key varchar(50);
alter table broadcast add column twitter_post_succeeded boolean;

alter table broadcast add column campaign_id bigint;

alter table broadcast add constraint fk_broadcast_campaign_id foreign key (campaign_id) references campaign(id);
