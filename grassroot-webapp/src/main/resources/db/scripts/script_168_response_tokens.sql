alter table verification_token_code add column entity_uid varchar(100);
alter table verification_token_code add column user_uid varchar(100);

create index idx_code_username on verification_token_code(username, code);
create unique index idx_entity_user_uid on verification_token_code(entity_uid, user_uid);

alter table media_file alter column mime_type type varchar(255);