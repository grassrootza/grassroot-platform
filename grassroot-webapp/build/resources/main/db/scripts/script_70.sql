DROP TABLE user_log;

CREATE TABLE user_log
(
  id bigserial NOT NULL,
  uid varchar(50) NOT NULL,
  creation_time timestamp without time zone not null,
  user_uid varchar(50) NOT NULL,
  user_log_type integer NOT NULL,
  description character varying(255),
  CONSTRAINT user_log_pkey PRIMARY KEY (id)
);

ALTER TABLE user_log ADD CONSTRAINT
  uk_user_log_request_uid unique (uid);

CREATE INDEX idx_user_log_user_uid
ON user_log
USING btree
(user_uid);

CREATE INDEX idx_user_log_userlogtype
ON user_log
USING btree
(user_log_type);

INSERT INTO user_log (creation_time, user_uid, user_log_type, description, uid)
  select created_date_time, uid, 0, 'Set from existing user', ('auto_log_' || nextval('user_log_id_seq'))
  from user_profile where created_date_time IS NOT NULL;

INSERT INTO user_log (creation_time, user_uid, user_log_type, description, uid)
  select current_timestamp, uid, 1, 'Initiated user session earlier than now', ('auto_log_' || nextval('user_log_id_seq'))
  from user_profile where initiated_session = TRUE;

INSERT INTO user_log (creation_time, user_uid, user_log_type, description, uid)
  select current_timestamp, uid, 2, 'Created web profile earlier than now', ('auto_log_' || nextval('user_log_id_seq'))
  from user_profile where web = TRUE;
