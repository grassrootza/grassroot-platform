ALTER TABLE paid_account ADD COLUMN logbook_extra boolean DEFAULT false;
UPDATE paid_account SET logbook_extra = false;