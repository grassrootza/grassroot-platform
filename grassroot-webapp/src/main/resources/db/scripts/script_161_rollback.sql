alter table notification alter column message type varchar(255);

drop table group_join_code;

delete from role where role_name = "ROLE_ALPHA_TESTER";
