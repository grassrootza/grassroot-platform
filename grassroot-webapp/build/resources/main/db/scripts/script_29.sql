CREATE OR REPLACE FUNCTION getchildren(IN groupid bigint)
  RETURNS TABLE(id bigint, name text, parent bigint) AS
$BODY$

 WITH RECURSIVE
    tree(id, created_date_time, name, group_token_code, token_code_expiry, created_by_user, parent, version, reminderminutes)
  AS ( SELECT pg.*
       FROM
         group_profile pg
       WHERE
         pg.id = $1
       UNION ALL SELECT sg.*
                 FROM
                   group_profile sg,
                   tree AS nodes
                 WHERE
                   sg.parent
                   =
                   nodes.id )
select tree.id,tree.name,tree.parent from tree;
$BODY$
  LANGUAGE sql VOLATILE
  COST 100
  ROWS 1000;

CREATE OR REPLACE FUNCTION getroot(groupid bigint)
  RETURNS bigint AS
$BODY$
WITH RECURSIVE
    tree(id, created_date_time, name, group_token_code, token_code_expiry, created_by_user, parent, version, reminderminutes)
  AS ( SELECT pg.*
       FROM
         group_profile pg
       WHERE
         pg.id = $1
       UNION ALL SELECT sg.*
                 FROM
                   group_profile sg,
                   tree AS nodes
                 WHERE
                   sg.id
                   =
                   nodes.parent )
select tree.id from tree where tree.parent ISNULL;
$BODY$
LANGUAGE sql VOLATILE
COST 100;

