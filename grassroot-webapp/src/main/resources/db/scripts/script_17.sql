CREATE TABLE paid_account (
    id bigint NOT NULL,
    created_date_time timestamp without time zone,
    account_name character varying(255) NOT NULL,
    primary_email character varying(100),
    enabled boolean,
    free_form boolean,
    relayable boolean
);

CREATE SEQUENCE account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE paid_group (
    id bigint NOT NULL,
    created_date_time timestamp without time zone,
    group_id bigint NOT NULL,
    account_id bigint NOT NULL,
    active_date_time timestamp,
    expire_date_time timestamp
)

CREATE SEQUENCE paid_group_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;