create table movement (
  id bigserial not null,
  uid varchar(50) not null,
  creation_time timestamp not null,
  created_by_user bigint not null,
  name text not null,
  description text,
  version int default 0,
  primary key (id),
  unique (uid)
);

create table movement_organizers (
  movement_id bigint not null,
  user_id bigint not null,
  primary key (movement_id, user_id));
