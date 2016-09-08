create table notification
(
  id BIGSERIAL not null,
  uid character varying(50) NOT NULL,
  user_id bigint not null,
  event_log_id bigint,
  log_book_log_id bigint,
  gcm_registration_id bigint,
  delivered boolean default false,
  read boolean default false,
  message VARCHAR (255),
  notification_type integer not null,
  user_messaging_preference integer DEFAULT  0 not null,
  creation_time timestamp without time zone not null,

   CONSTRAINT fk_user_not88jj53 FOREIGN KEY (user_id)
      REFERENCES user_profile (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
      CONSTRAINT fk_event_log_not33j07 FOREIGN KEY (event_log_id)
      REFERENCES event_log (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,

       CONSTRAINT fk_log_book__log_not144107 FOREIGN KEY (log_book_log_id)
      REFERENCES log_book_log (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,

      CONSTRAINT fk_gcm_registration_not4078jjot FOREIGN KEY (gcm_registration_id)
      REFERENCES gcm_registration (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,

    CONSTRAINT notification_pkey PRIMARY KEY (id),
  CONSTRAINT uk_notification_uid UNIQUE (uid)
);

