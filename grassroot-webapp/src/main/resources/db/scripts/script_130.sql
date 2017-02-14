alter table paid_group add column status varchar(50);

update paid_group set status = 'ACTIVE' where expire_date_time > current_timestamp;
update paid_group set status = 'REMOVED' where expire_date_time < current_timestamp;

alter table paid_group alter column status set not null;

create index idx_paid_group_status on paid_group (status);