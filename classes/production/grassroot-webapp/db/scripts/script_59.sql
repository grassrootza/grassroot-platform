CREATE TABLE user_create_request (
  id  bigserial not null,
  creation_time timestamp not null,
  uid varchar(50) not null,
  phone_number varchar(50) not null,
  display_name varchar(50),
  primary key(id)
);

alter table user_create_request add constraint uk_user_create_request_uid unique (uid);
