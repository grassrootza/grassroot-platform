-- adding spending limits to accounts
alter table broadcast alter column msg_template1 type varchar(255);
alter table broadcast alter column msg_template2 type varchar(255);
alter table broadcast alter column msg_template3 type varchar(255);
