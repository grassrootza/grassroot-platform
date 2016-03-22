DELETE FROM log_book WHERE recorded = false;
ALTER TABLE log_book DROP COLUMN recorded;

ALTER TABLE log_book ADD COLUMN uid varchar(50);
UPDATE log_book SET uid = 'auto_' || nextval('log_book_id_seq');
ALTER TABLE log_book ALTER COLUMN uid SET NOT NULL;
ALTER TABLE ONLY log_book ADD CONSTRAINT uk_log_book_uid UNIQUE (uid);

ALTER TABLE log_book ALTER COLUMN group_id SET NOT NULL;
ALTER TABLE log_book ALTER COLUMN created_by_user_id SET NOT NULL;
ALTER TABLE log_book ALTER COLUMN message SET NOT NULL;
ALTER TABLE log_book ALTER COLUMN action_by_date SET NOT NULL;
ALTER TABLE log_book ALTER COLUMN reminder_minutes SET NOT NULL;

UPDATE log_book SET replicated_group_id = null WHERE replicated_group_id = 0;
UPDATE log_book SET assigned_to_user_id = null WHERE assigned_to_user_id = 0;

create table log_book_request (
id  bigserial not null,
uid varchar(255) not null,
action_by_date timestamp,
created_date_time timestamp,
message varchar(255),
reminder_minutes int4,
replicate_to_subgroups boolean not null,
assigned_to_user_id int8,
created_by_user_id int8 not null,
group_id int8 not null,
primary key (id));

alter table log_book_request add constraint UK_log_book_request_uid unique (uid);
alter table log_book_request add constraint FK_log_book_request_assigned_user foreign key (assigned_to_user_id) references user_profile;
alter table log_book_request add constraint FK_log_book_request_created_by_user foreign key (created_by_user_id) references user_profile;
alter table log_book_request add constraint FK_log_book_request_group foreign key (group_id) references group_profile;


