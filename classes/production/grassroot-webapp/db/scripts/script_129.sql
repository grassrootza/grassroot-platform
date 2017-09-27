alter table user_profile rename account_administered to primary_account;

create table account_admins (
  account_id bigint not null,
  user_id bigint not null,
  primary key (account_id, user_id));

insert into account_admins(account_id, user_id)
  select u.primary_account, u.id
  from user_profile as u
  where u.primary_account is not null;