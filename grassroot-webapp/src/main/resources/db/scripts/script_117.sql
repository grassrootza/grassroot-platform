DO $$ 
  declare
    temp record;
BEGIN
  FOR temp IN
         select * from role  where role_name in ('ROLE_COMMITTEE_MEMBER','ROLE_GROUP_ORGANIZER')
    LOOP
         IF NOT EXISTS (SELECT 1 FROM role_permissions WHERE role_id = temp.id AND permission='GROUP_PERMISSION_MUTE_MEMBER') THEN
          INSERT INTO role_permissions (role_id,permission) VALUES (temp.id,'GROUP_PERMISSION_MUTE_MEMBER');
         END IF;
    END LOOP; 
END $$;