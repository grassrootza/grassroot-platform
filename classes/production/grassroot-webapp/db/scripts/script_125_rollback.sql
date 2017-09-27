update event set importance = '1';
alter table event alter column importance type integer using cast(importance as integer);