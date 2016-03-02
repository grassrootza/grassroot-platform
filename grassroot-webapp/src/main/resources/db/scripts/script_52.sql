-- Since we haven't started creating more than a couple of accounts, delete them then run this
TRUNCATE table paid_account;
ALTER TABLE paid_account ADD COLUMN uid varchar(50) not null;
ALTER TABLE ONLY paid_account ADD CONSTRAINT uk_account_uid UNIQUE (uid);

TRUNCATE table paid_group;
ALTER TABLE paid_group ADD COLUMN uid varchar(50) not null;
ALTER TABLE paid_group DROP COLUMN archived_account_id;
ALTER TABLE ONLY paid_group ADD CONSTRAINT uk_paidgroup_uid UNIQUE (uid);

