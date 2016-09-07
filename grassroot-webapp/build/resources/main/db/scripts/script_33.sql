CREATE TABLE log_book_log
(
  id bigserial NOT NULL,
  created_date_time timestamp without time zone,
  group_id bigint,
  logbook_id bigint,
  message character varying(255),
  message_to character varying(255),
  user_id bigint,
  CONSTRAINT log_book_log_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_log_book_log_logbook_id
ON log_book_log
USING btree
(logbook_id);