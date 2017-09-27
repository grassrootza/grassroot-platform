create table gcm_registration
(
  id BIGSERIAL not null,
  uid character varying(50) NOT NULL,
  user_id bigint not null,
  event_id bigint not null,
  event_log_id bigint not null,
  registration_id character varying(50) not null,
  creation_time timestamp without time zone not null,

   CONSTRAINT fk_user_gcm407 FOREIGN KEY (user_id)
      REFERENCES user_profile (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,

    CONSTRAINT gcm_registration_pkey PRIMARY KEY (id),
  CONSTRAINT uk_gcm_registration_uid UNIQUE (uid)
);

