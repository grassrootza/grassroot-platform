alter table paid_account add column enabled_date_time timestamp;
alter table paid_account add column enabled_by_user bigint;
alter table paid_account add column last_payment_date timestamp;
alter table paid_account add column outstanding_balance bigint not null default 0; -- as above
alter table paid_account add column next_billing_date timestamp; -- can be null, e.g., for disabled account
alter table paid_account add column monthly_subscription bigint not null default 0;
alter table paid_account add column visible boolean not null default true;

-- replacing primary email with billing user, and adding email field to user
alter table paid_account add column billing_user bigint;
alter table user_profile add column email_address varchar(255);
alter table paid_account add column payment_reference varchar(255);
alter table paid_account drop column primary_email;

-- assumes that in all legacy systems, first user is admin; also, sets billing user to creating user, to start -- needs to be manually adjust
update paid_account set created_by_user = 1 where created_by_user is null;
update paid_account set enabled_by_user = created_by_user;
update paid_account set billing_user = created_by_user;
update paid_account set enabled_date_time = created_date_time;
update paid_account set disabled_date_time = '2099-12-31 23:59' where disabled_date_time != null; -- in postgresql is not null doesn't work well here
update paid_account set next_billing_date = current_timestamp + interval '1 month';
update paid_account set monthly_subscription = 0;

alter table paid_account alter column created_by_user set not null;
alter table paid_account alter column disabled_date_time set not null;
alter table paid_account alter column enabled_date_time set not null;
alter table paid_account alter column enabled_by_user set not null;
alter table paid_account alter column billing_user set not null;
alter table paid_account alter column monthly_subscription set not null;

alter table paid_account add constraint fk_enabled_by_user foreign key (enabled_by_user) references user_profile;
alter table paid_account add constraint fk_billing_user foreign key (billing_user) references user_profile;

create table paid_account_billing (
  id bigserial not null,
  amount_billed bigint not null,
  billed_balance bigint not null,
  billed_period_end timestamp not null,
  billed_period_start timestamp not null,
  next_payment_date timestamp not null,
  created_date_time timestamp not null,
  opening_balance bigint not null,
  statement_date_time timestamp not null,
  bill_paid boolean default false not null,
  uid varchar(255) not null,
  account_id bigint not null,
  account_log_id bigint not null,
  primary key (id)
);

alter table paid_account_billing add constraint uk_billing_record_uid unique (uid);
alter table paid_account_billing add constraint fk_billing_record_account foreign key (account_id) references paid_account;
alter table paid_account_billing add constraint fk_billing_record_account_log foreign key (account_log_id) references account_log;

alter table account_log add column reference_amount bigint; -- using big int to store this in cents (i.e., / 100 for humans)