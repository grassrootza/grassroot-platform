-- maybe - could keep around for later audit, perhaps
alter table group_profile add column account_id bigint;
alter table group_profile add foreign key (account_id) references paid_account;

update group_profile as g
set account_id = a.id
from paid_account as a
  join paid_group as pg
    on pg.account_id = a.id
where pg.group_id = g.id and pg.status = 'ACTIVE';

drop table account_billing_record;
drop table paid_group;
drop table acc_sponsor_request;

alter table paid_account add column subscription_reference varchar(255) unique;
alter table paid_account add column closed boolean default false;
update paid_account set closed = false where visible = true;
update paid_account set closed = true where visible = false;
alter table paid_account alter column closed set not null;

alter table paid_account alter column billing_user drop not null;
alter table paid_account alter column billing_cycle drop not null;
alter table paid_account alter column outstanding_balance drop not null;
alter table paid_account alter column todos_per_month drop not null;
alter table paid_account alter column free_form drop not null;

alter table paid_account add column last_billing_date timestamp;
update paid_account set last_billing_date = greatest(last_payment_date, created_date_time);
alter table paid_account alter column last_billing_date set not null;

-- todo : cull account limit columns, also remove last_billing_date, enabled_date_time, etc