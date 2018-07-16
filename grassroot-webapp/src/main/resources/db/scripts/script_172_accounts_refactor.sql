alter table group_profile add column account_id bigint;
alter table group_profile add foreign key (account_id) references paid_account;

update group_profile as g
set account_id = a.id
from paid_account as a
  join paid_group as pg
    on pg.account_id = a.id
where pg.group_id = g.id and pg.status = 'ACTIVE';

-- drops will follow in future, when confident of this, but leaving in here for now
-- drop table account_billing_record;
-- drop table paid_group;
-- drop table acc_sponsor_request;

alter table paid_account add column subscription_reference varchar(255) unique;
alter table paid_account add column geo_data_sets text;

alter table paid_account add column closed boolean default false;
update paid_account set closed = false where visible = true;
update paid_account set closed = true where visible = false;
alter table paid_account alter column closed set not null;

alter table paid_account alter column billing_user drop not null;
alter table paid_account alter column billing_cycle drop not null;
alter table paid_account alter column outstanding_balance drop not null;
alter table paid_account alter column todos_per_month drop not null;
alter table paid_account alter column free_form_per_month drop not null;

alter table paid_account add column last_billing_date timestamp;
update paid_account set last_billing_date = greatest(last_payment_date, created_date_time);
alter table paid_account alter column last_billing_date set not null;
alter table paid_account add column primary_email varchar(255);

alter table group_profile rename column avatar_format to profile_image_key;

-- as above, to cull later, but on fresh, can consolidate
-- alter table paid_account drop column last_billing_date;
-- alter table paid_account drop column enabled_date_time;
-- alter table paid_account drop column enabled_by_user;
-- alter table paid_account drop column max_group_number;
-- alter table paid_account drop column max_group_size;
-- alter table paid_account drop column max_sub_group_depth;
-- alter table paid_account drop column additional_reminders;
-- alter table paid_account drop column last_payment_date;
-- alter table paid_account drop column next_billing_date;
-- alter table paid_account drop column billing_user;
-- alter table paid_account drop column todos_per_month;
-- alter table paid_account drop column events_per_month;
