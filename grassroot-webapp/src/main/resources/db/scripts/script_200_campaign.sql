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
    sequence_number integer,
    primary key (id));


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

  ALTER SEQUENCE campaign_id_seq OWNED BY campaign.id;

  ALTER SEQUENCE campaign_message_id_seq OWNED BY campaign_message.id;