--paid_account
DROP TABLE paid_account;
DROP SEQUENCE IF EXISTS account_id_seq;
DROP SEQUENCE IF EXISTS paid_account_seq;
CREATE SEQUENCE paid_account_id_seq
START WITH 1
INCREMENT BY 1
NO MINVALUE
NO MAXVALUE
CACHE 1;
CREATE TABLE paid_account
(
  id                BIGINT                 NOT NULL,
  created_date_time TIMESTAMP WITHOUT TIME ZONE,
  account_name      CHARACTER VARYING(255) NOT NULL,
  primary_email     CHARACTER VARYING(100),
  enabled           BOOLEAN DEFAULT FALSE,
  free_form         BOOLEAN DEFAULT FALSE,
  relayable         BOOLEAN DEFAULT FALSE
);

ALTER SEQUENCE paid_account_id_seq OWNED BY paid_account.id;
ALTER TABLE ONLY paid_account ALTER COLUMN id SET DEFAULT nextval('paid_account_id_seq' :: REGCLASS);
ALTER TABLE ONLY paid_account ADD CONSTRAINT paid_account_pkey PRIMARY KEY (id);
--paid_group
DROP TABLE paid_group;
DROP SEQUENCE IF EXISTS paid_group_id_seq;

CREATE SEQUENCE paid_group_id_seq
INCREMENT 1
MINVALUE 1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

CREATE TABLE paid_group
(
  id                BIGINT NOT NULL,
  created_date_time TIMESTAMP WITHOUT TIME ZONE,
  group_id          BIGINT NOT NULL,
  account_id        BIGINT NOT NULL,
  active_date_time  TIMESTAMP WITHOUT TIME ZONE,
  expire_date_time  TIMESTAMP WITHOUT TIME ZONE,
  user_added_id     BIGINT NOT NULL,
  user_removed_id   BIGINT
);

ALTER SEQUENCE paid_group_id_seq OWNED BY paid_group.id;
ALTER TABLE ONLY paid_group ALTER COLUMN id SET DEFAULT nextval('paid_group_id_seq' :: REGCLASS);
ALTER TABLE ONLY paid_group ADD CONSTRAINT paid_group_pkey PRIMARY KEY (id);
