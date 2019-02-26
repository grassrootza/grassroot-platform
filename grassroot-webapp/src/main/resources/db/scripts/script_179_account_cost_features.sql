-- adding spending limits to accounts
alter table paid_account add column monthly_spending_limit bigint default 10000000; -- R100,000.00 ie a lot
alter table paid_account add column current_month_spend bigint default 0;
alter table paid_account add column ussd_avg_cost bigint default 60;
alter table paid_account add column monthly_fees bigint default 0;

update paid_account set monthly_spending_limit = 10000000;
update paid_account set current_month_spend = 0;
update paid_account set ussd_avg_cost = 60;
update paid_account set monthly_fees = 0;

alter table paid_account alter column monthly_spending_limit set not null;
alter table paid_account alter column current_month_spend set not null;
alter table paid_account alter column ussd_avg_cost set not null;
alter table paid_account alter column monthly_fees set not null;