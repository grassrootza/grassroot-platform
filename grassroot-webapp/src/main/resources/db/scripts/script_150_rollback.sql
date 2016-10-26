alter table paid_account drop column enabled_date_time;
alter table paid_account drop column enabled_by_user;
alter table paid_account drop column last_payment_date;
alter table paid_account drop column last_payment_amount;
alter table paid_account drop column outstanding_balance;
alter table paid_account drop column next_billing_date;
alter table paid_account drop column monthly_subscription;

alter table paid_account drop column billing_user;
alter table user_profile drop column email_address;
alter table paid_account drop column payment_reference;
alter table paid_account add column primary_email varchar(255);

drop table paid_account_billing;

alter table account_log drop column reference_amount;