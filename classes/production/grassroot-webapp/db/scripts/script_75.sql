create table account_log
(
  id BIGSERIAL not null,
  account_log_type integer not null,
  creation_time timestamp not null,
  description varchar(255),
  group_uid varchar(50),
  paid_group_uid varchar(50),
  uid varchar(50) not null,
  user_uid varchar(50) not null,
  account_id bigint,
  primary key (id)
);

alter table account_log add constraint uk_account_log_uid unique (uid);

alter table account_log add constraint FK_elqakf1t2bo72tv3xypd1wo88 foreign key (account_id) references paid_account;
