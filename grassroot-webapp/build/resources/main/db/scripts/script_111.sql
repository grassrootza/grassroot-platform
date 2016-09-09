CREATE SEQUENCE messenger_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



CREATE TABLE messenger_settings
(
  id bigint NULL DEFAULT nextval('messenger_settings_id_seq'::regclass),
  group_id bigint NOT NULL,
  created_date_time timestamp without time zone,
  active boolean default true,
  user_id bigint NOT NULL,
  user_initiated boolean default false,
  send boolean default true,
  receive boolean default true,
  reactivation_time timestamp without time zone NOT NULL,
  primary key (id),
  CONSTRAINT fk_chat_group FOREIGN KEY (group_id)
      REFERENCES group_profile (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT fk_chat_user FOREIGN KEY (user_id)
      REFERENCES user_profile (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION
);
