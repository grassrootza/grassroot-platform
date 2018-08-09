drop table if exists account_billing_record;
drop table paid_group;
drop table acc_sponsor_request;

alter table paid_account drop column enabled_date_time;
alter table paid_account drop column max_group_number;
alter table paid_account drop column max_group_size;
alter table paid_account drop column max_sub_group_depth;
alter table paid_account drop column additional_reminders;
alter table paid_account drop column last_payment_date;
alter table paid_account drop column next_billing_date;
alter table paid_account drop column billing_user;
alter table paid_account drop column todos_per_month;
alter table paid_account drop column events_per_month;
