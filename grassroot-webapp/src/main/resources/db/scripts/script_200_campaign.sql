create table campaign (
  id bigserial not null,
  version integer,
  uid varchar(50) not null,
  created_date_time timestamp not null,
  start_date_time timestamp not null,
  end_date_time timestamp not null,
  name varchar(50),
  description varchar(255),
  code varchar(10),
  created_by_user bigint,
  tags text[] default '{}',
  ancestor_group_id bigint,
  type varchar(50),
  url varchar(60),
  primary key (id));

  create table campaign_message (
    id bigserial not null,
    version integer,
    uid varchar(50) not null,
    created_date_time timestamp not null,
    message varchar(255),
    created_by_user bigint,
    tags text[] default '{}',
    variation varchar(35),
    locale varchar(35),
    channel varchar(35),
    campaign_id bigserial,
    parent_action_id varchar(50),
    primary key (id));

  create table campaign_message_action (
    id bigserial not null,
    version integer,
    uid varchar(50) not null,
    created_date_time timestamp not null,
    parent_message_id varchar(255),
    created_by_user bigint,
    action varchar(35),
    primary key (id));

  CREATE TABLE campaign_log(
    id bigserial NOT NULL,
    uid varchar(50) NOT NULL,
    creation_time timestamp without time zone not null,
    user_id varchar(50),
    campaign_log_type varchar(50) NOT NULL,
    description character varying(255),
    campaign_id bigserial,
    CONSTRAINT uk_campaign_log_uid unique (uid),
    PRIMARY KEY (id));


  CREATE SEQUENCE campaign_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

  CREATE SEQUENCE campaign_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

  CREATE SEQUENCE campaign_message_action_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

  ALTER SEQUENCE campaign_id_seq OWNED BY campaign.id;

  ALTER SEQUENCE campaign_message_id_seq OWNED BY campaign_message.id;

  ALTER SEQUENCE campaign_message_action_id_seq OWNED BY campaign_message_action.id;

  alter table campaign_message ADD CONSTRAINT fk_campaign_message_campaign_id FOREIGN KEY (campaign_id) REFERENCES campaign(id);