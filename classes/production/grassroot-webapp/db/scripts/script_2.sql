ALTER TABLE event ADD COLUMN includesubgroups boolean;
update event set includesubgroups = false;