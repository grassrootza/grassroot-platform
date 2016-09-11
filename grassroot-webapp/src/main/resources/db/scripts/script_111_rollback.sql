ALTER TABLE ONLY paid_account DROP COLUMN version;

ALTER TABLE ONLY paid_account DROP CONSTRAINT fk_paid_account_creating_user;
ALTER TABLE ONLY paid_account DROP COLUMN created_by_user;

ALTER TABLE ONLY verification_token_code ALTER COLUMN code DROP NOT NULL;
