drop table data_subscriber;
alter table address alter column resident_user_id set not null;
alter table address drop column latitude;
alter table address drop column longitude;
alter table address drop column location_source;
drop table address_log;