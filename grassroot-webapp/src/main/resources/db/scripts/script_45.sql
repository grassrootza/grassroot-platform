CREATE TABLE user_log
(
  id bigserial NOT NULL,
  created_date_time timestamp without time zone,
  user_id bigint,
  user_log_type integer,
  description character varying(255),
  CONSTRAINT user_log_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_user_log_user_id
ON user_log
USING btree
(user_id);

CREATE INDEX idx_user_log_userlogtype
ON user_log
USING btree
(user_log_type);

