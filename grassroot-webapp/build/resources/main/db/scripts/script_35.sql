ALTER TABLE log_book ADD COLUMN recorded boolean DEFAULT true;
UPDATE log_book SET recorded = true;