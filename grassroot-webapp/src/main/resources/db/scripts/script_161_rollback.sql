alter table notification alter column message type varchar(255);
alter table notification drop column read_receipt_fetches;

drop table group_join_code;

delete from user_roles where role_id in (select id from role where role_name = 'ROLE_ALPHA_TESTER');
delete from role where role_name = 'ROLE_ALPHA_TESTER';
