alter table paid_account drop column enabled_date_time;
alter table paid_account drop column enabled_by_user;
alter table paid_account drop column last_payment_date;
alter table paid_account drop column last_payment_amount;
alter table paid_account drop column outstanding_balance;

drop table paid_account_billing;

alter table account_log drop column reference_amount;