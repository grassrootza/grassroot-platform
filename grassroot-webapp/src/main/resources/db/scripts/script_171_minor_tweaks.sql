alter table campaign alter column description type text;
alter table campaign add column default_language varchar(10);

alter table event rename column importance to special_form;
update event set special_form = 'IMPORTANT_MEETING' where special_form = 'SPECIAL';
