alter table campaign alter column description type varchar(512);
alter table campaign drop column default_language;

alter table event rename column special_form to importance;
update event set importance = 'SPECIAL' where importance = 'IMPORTANT_MEETING';
update event set importance = null where importance = 'MASS_VOTE';