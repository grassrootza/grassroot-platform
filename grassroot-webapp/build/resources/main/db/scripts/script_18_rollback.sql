ALTER TABLE user_profile DROP COLUMN account_administered;
ALTER TABLE group_profile DROP COLUMN paid_for;

DROP TABLE paid_account;
DROP TABLE paid_group;