alter table event rename column applies_to_group to parent_group_id;
alter table event rename column log_book_id to parent_log_book_id;

alter table event_request rename column applies_to_group to parent_group_id;
alter table event_request rename column log_book_id to parent_log_book_id;

alter table log_book rename column group_id to parent_group_id;
alter table log_book rename column event_id to parent_event_id;

alter table log_book_request rename column group_id to parent_group_id;
alter table log_book_request rename column event_id to parent_event_id;

CREATE OR REPLACE FUNCTION getusergroups(IN userid bigint)
  RETURNS TABLE(id bigint, created_date_time timestamp without time zone, name character varying, active boolean, maximum_time timestamp without time zone) AS
$BODY$
DECLARE
 mem_rec RECORD;
 group_rec RECORD;
 max_time timestamp;
BEGIN
FOR mem_rec in SELECT group_id FROM group_user_membership WHERE group_user_membership.user_id=userId
 LOOP
 SELECT * FROM group_profile WHERE group_profile.id=mem_rec.group_id INTO group_rec;
 SELECT max(start_date_time) FROM event WHERE event.parent_group_id=group_rec.id INTO max_time;
  id:= group_rec.id;
  created_date_time:=group_rec.created_date_time;
  name:=group_rec.name;
  active:=group_rec.active;
  maximum_time:=max_time;
  RETURN NEXT;
 END LOOP;
END;
$BODY$
  LANGUAGE plpgsql VOLATILE

