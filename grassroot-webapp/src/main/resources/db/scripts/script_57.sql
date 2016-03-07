ALTER TABLE group_profile ADD COLUMN join_approver_id int8;
alter table group_profile add constraint fk_join_approver foreign key (join_approver_id) references user_profile;