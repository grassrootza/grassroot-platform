alter table paid_account add column payment_type VARCHAR(50);
alter table paid_account add column billing_cycle VARCHAR(50);

update paid_account set billing_cycle = 'MONTHLY';
alter table paid_account alter column billing_cycle set NOT NULL;

alter table paid_account_billing add column payment_type VARCHAR(50);