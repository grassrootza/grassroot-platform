ALTER TABLE log_book_log ADD COLUMN type varchar(50);
update log_book_log set type = 'REMINDER_SENT' where user_id is null;
update log_book_log set type = 'CHANGED' where user_id is not null;

do $$
declare
  lbrec record;
BEGIN
  FOR lbrec IN
  select * from log_book
  LOOP
    insert into log_book_log (created_date_time, logbook_id, user_id, type)
    values (lbrec.created_date_time, lbrec.id, lbrec.created_by_user_id, 'CREATED');
  END LOOP;
end;
$$;

alter table log_book_log alter column type set not null;
alter table log_book_log rename column logbook_id to log_book_id;
