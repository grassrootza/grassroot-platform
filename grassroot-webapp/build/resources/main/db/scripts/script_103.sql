ALTER TABLE verification_token_code ADD COLUMN token_type varchar(50);
UPDATE verification_token_code SET token_type = 'LONG_AUTH' where length(code) > 10;
UPDATE verification_token_code SET token_type = 'SHORT_OTP' where length(code) < 10;
ALTER TABLE verification_token_code ALTER COLUMN token_type SET NOT NULL;
ALTER TABLE verification_token_code ALTER COLUMN username SET NOT NULL;
ALTER TABLE verification_token_code ALTER COLUMN creation_date SET NOT NULL;