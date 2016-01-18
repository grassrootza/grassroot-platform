CREATE TABLE group_log
(
  id bigserial NOT NULL,
  created_date_time timestamp without time zone,
  group_id bigint,
  group_log_type integer,
  user_id bigint,
  user_or_sub_group_id bigint,
  CONSTRAINT group_log_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_group_log_group_id
ON group_log
USING btree
(group_id);


CREATE INDEX idx_group_log_grouplogtype
ON group_log
USING btree
(group_log_type);

