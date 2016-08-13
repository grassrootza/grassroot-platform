ALTER TABLE verification_token_code DROP COLUMN token_type;
ALTER TABLE verification_token_code ALTER COLUMN username DROP NOT NULL;
ALTER TABLE verification_token_code ALTER COLUMN creation_date DROP NOT NULL;