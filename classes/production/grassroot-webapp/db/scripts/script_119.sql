delete from messenger_settings
where id in
      (select id from
        (select id, row_number() over (PARTITION BY user_id, group_id order by id DESC) as rnum
         from messenger_settings) t
      where t.rnum > 1);

alter table messenger_settings add constraint uk_chat_settings_user_group unique (user_id, group_id);