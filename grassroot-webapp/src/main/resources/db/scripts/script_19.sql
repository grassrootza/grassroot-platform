ALTER TABLE paid_account ALTER COLUMN enabled SET DEFAULT FALSE;
ALTER TABLE paid_account ALTER COLUMN free_form SET DEFAULT FALSE;
ALTER TABLE paid_account ALTER COLUMN relayable SET DEFAULT FALSE;

ALTER TABLE group_profile ALTER COLUMN paid_for SET DEFAULT FALSE;
update group_profile set paid_for = false;