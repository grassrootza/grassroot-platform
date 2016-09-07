ALTER TABLE paid_account DROP COLUMN uid;
ALTER TABLE paid_group DROP COLUMN uid;
ALTER TABLE paid_group ADD COLUMN archived_account_id int8 not null;

