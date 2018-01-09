alter table account_log alter column user_uid drop not null;
alter table group_join_code add column short_url varchar(30);