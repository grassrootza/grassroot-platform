alter table paid_account add column todos_per_month integer default 0 not null;

update paid_account set todos_per_month = 4; -- default for all, then set per type
update paid_account set todos_per_month = 8 where account_type = 'LIGHT';
update paid_account set todos_per_month = 16 where account_type = 'STANDARD';
update paid_account set todos_per_month = 50 where account_type = 'HEAVY';
update paid_account set todos_per_month = 999 where account_type = 'ENTERPRISE';

alter table paid_account_billing add column paid_date timestamp;
alter table paid_account_billing add column paid_amount bigint;
alter table paid_account_billing add column payment_id varchar(50);

create index idx_action_todo_ancestor_group_id on action_todo (ancestor_group_id);
create index idx_paid_group_origin_group on paid_group (group_id);
create index idx_paid_group_account on paid_group (account_id);