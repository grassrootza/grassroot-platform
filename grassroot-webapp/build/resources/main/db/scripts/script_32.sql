CREATE TABLE log_book
(
  id bigserial NOT NULL,
  action_by_date timestamp without time zone,
  assigned_to_user_id bigint,
  completed boolean,
  completed_by_user_id bigint,
  completed_date timestamp without time zone,
  created_by_user_id bigint,
  created_date_time timestamp without time zone,
  group_id bigint,
  message character varying(255),
  number_of_reminders_left_to_send integer,
  reminder_minutes integer,
  replicated_group_id bigint,
  CONSTRAINT log_book_pkey PRIMARY KEY (id)
);


CREATE INDEX idx_log_book_assigned_to
ON log_book
USING btree
(assigned_to_user_id);

CREATE INDEX idx_log_book_completed
ON log_book
USING btree
(completed);

CREATE INDEX idx_log_book_group_id
ON log_book
USING btree
(group_id);

CREATE INDEX idx_log_book_replicated_group_id
ON log_book
USING btree
(replicated_group_id);

CREATE INDEX idx_log_book_retries_left
ON log_book
USING btree
(number_of_reminders_left_to_send);

