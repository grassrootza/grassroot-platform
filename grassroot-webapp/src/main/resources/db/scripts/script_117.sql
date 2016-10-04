ALTER TABLE paid_account ADD COLUMN free_form_cost integer DEFAULT 0;

ALTER TABLE paid_account ADD COLUMN account_type VARCHAR(50);
-- just to default any existing accounts to top tier (adjust if needed after)
UPDATE paid_account SET account_type = 'ENTERPRISE';
ALTER TABLE paid_account ALTER COLUMN account_type SET NOT NULL;
