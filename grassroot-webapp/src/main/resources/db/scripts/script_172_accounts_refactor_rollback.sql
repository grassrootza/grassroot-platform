alter table group_profile drop column account_id;

alter table paid_account drop column subscription_reference;
alter table paid_account drop column geo_data_sets;

alter table paid_account drop column closed;

alter table paid_account drop column last_billing_date;

alter table group_profile rename column profile_image_key to avatar_format;
