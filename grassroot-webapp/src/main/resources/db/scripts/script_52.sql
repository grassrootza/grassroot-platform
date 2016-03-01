-- Since we haven't started creating more than a couple of accounts, delete them then run this

ALTER TABLE paid_account ADD COLUMN uid varchar(50);
ALTER TABLE paid_account ALTER COLUMN uid SET NOT NULL;
ALTER TABLE ONLY paid_account ADD CONSTRAINT uk_account_uid UNIQUE (uid);

ALTER TABLE paid_group ADD COLUMN uid varchar(50);
ALTER TABLE paid_group DROP COLUMN archived_account_id;
ALTER TABLE paid_group ALTER COLUMN uid SET NOT NULL;
ALTER TABLE ONLY paid_group ADD CONSTRAINT uk_paidgroup_uid UNIQUE (uid);

