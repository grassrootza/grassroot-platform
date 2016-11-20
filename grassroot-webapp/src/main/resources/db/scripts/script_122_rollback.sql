alter table paid_account drop column todos_per_month;

drop index if exists idx_action_todo_ancestor_group_id;
drop index if exists idx_paid_group_origin_group;
drop index if exists idx_paid_group_account;

alter table paid_account_billing drop column paid_date;
alter table paid_account_billing drop column paid_amount;
alter table paid_account_billing drop column payment_id;