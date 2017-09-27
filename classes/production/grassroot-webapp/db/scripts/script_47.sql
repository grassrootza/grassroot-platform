CREATE OR REPLACE FUNCTION getusergroupswithmembercount(IN userid bigint)
  RETURNS TABLE(id bigint, created_date_time timestamp without time zone, name character varying, active boolean, group_size integer) AS
$BODY$
DECLARE
 mem_rec RECORD;
 group_rec RECORD;
 num_members int;
BEGIN
FOR mem_rec in SELECT user_groups.id FROM getusergroups(userid) as user_groups where user_groups.active=true order by user_groups.maximum_time desc NULLS LAST,user_groups.created_date_time desc
 LOOP
 SELECT * FROM group_profile WHERE group_profile.id=mem_rec.id INTO group_rec;
 Select count(*) from group_user_membership where group_user_membership.group_id=group_rec.id INTO num_members;
  id:= group_rec.id; 
  created_date_time:=group_rec.created_date_time;
  name:=group_rec.name;
  active:=group_rec.active;
  group_size:=num_members;
  RETURN NEXT;
 END LOOP;
END;
$BODY$
  LANGUAGE plpgsql VOLATILE
