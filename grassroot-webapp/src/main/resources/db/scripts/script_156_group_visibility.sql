ALTER TABLE group_user_membership ADD COLUMN view_priority VARCHAR(50);

update group_user_membership set view_priority = 'NORMAL';
alter table group_user_membership alter column view_priority set not null;
create index membership_view_priority on group_user_membership (view_priority);