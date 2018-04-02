alter table verification_token_code drop column entity_uid;
alter table verification_token_code drop column user_uid;

drop index if exists idx_code_username;
drop index if exists idx_entity_user_uid;

alter table media_file alter column mime_type type varchar(50);