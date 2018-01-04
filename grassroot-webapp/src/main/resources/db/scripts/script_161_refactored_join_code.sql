-- overdue adjustment (needed now that notifications are getting big)
alter table notification alter column message type text;
alter table notification add column read_receipt_fetches integer default 0;
update notification set notification.read_receipt_fetches = 0;


create table group_join_code (
  id bigserial not null,
  creation_time timestamp not null,
  user_uid varchar(50) not null,
  group_uid varchar(50) not null,
  code varchar(50),
  type varchar(50) not null,
  active boolean default true,
  closed_time timestamp,
  closing_user_uid varchar(50),
  version integer
);

create unique index gjc_unique_active on group_join_code(lower(code)) where active = true;

-- and inserting a role
insert into role(role_name, role_type) values ('ROLE_ALPHA_TESTER', 'STANDARD');