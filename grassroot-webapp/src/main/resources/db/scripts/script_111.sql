ALTER TABLE ONLY paid_account ADD COLUMN version INTEGER DEFAULT 0;
ALTER TABLE ONLY paid_account ADD COLUMN created_by_user bigint;

ALTER TABLE ONLY paid_account ADD CONSTRAINT fk_paid_account_creating_user FOREIGN KEY (created_by_user) REFERENCES user_profile(id);

DELETE FROM verification_token_code WHERE code IS null;
ALTER TABLE ONLY verification_token_code ALTER COLUMN code SET NOT NULL;
