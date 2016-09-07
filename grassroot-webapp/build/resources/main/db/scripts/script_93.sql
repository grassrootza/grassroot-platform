create table log_book_completion_confirmation (
id  bigserial not null,
completion_time timestamp,
log_book_id int8 not null,
member_id int8 not null,
primary key (id));

alter table log_book_completion_confirmation add constraint uk_compl_confirmation_log_book_member unique (log_book_id, member_id);
alter table log_book_completion_confirmation add constraint fk_log_book_compl_confirm_log_book foreign key (log_book_id) references log_book;
alter table log_book_completion_confirmation add constraint fk_log_book_compl_confirm_member foreign key (member_id) references user_profile;

alter table log_book drop column completed;
alter table log_book drop column completed_by_user_id;
alter table log_book add column completion_percentage double precision NOT NULL default 0;


