
--
-- Name: event; Type: TABLE; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE TABLE event (
    id bigint NOT NULL,
    canceled boolean,
    created_date_time timestamp,
    date_time_string character varying(255),
    location character varying(50),
    start_date_time timestamp,
    event_type integer,
    name character varying(255),
    applies_to_group bigint,
    created_by_user bigint
);



--
-- Name: event_id_seq; Type: SEQUENCE; Schema: public; Owner: grassroot
--

CREATE SEQUENCE event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;






--
-- Name: event_log; Type: TABLE; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE TABLE event_log (
    id bigint NOT NULL,
    created_date_time timestamp ,
    event_log_type integer,
    message character varying(255),
    event_id bigint,
    user_id bigint
);



--
-- Name: event_log_id_seq; Type: SEQUENCE; Schema: public; Owner: grassroot
--

CREATE SEQUENCE event_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;






--
-- Name: group_profile; Type: TABLE; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE TABLE group_profile (
    id bigint NOT NULL,
    created_date_time timestamp,
    name character varying(50) NOT NULL,
    group_token_code character varying(255),
    token_code_expiry timestamp,
    created_by_user bigint,
    parent bigint
);


--
-- Name: group_profile_id_seq; Type: SEQUENCE; Schema: public; Owner: grassroot
--

CREATE SEQUENCE group_profile_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;






--
-- Name: group_user_membership; Type: TABLE; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE TABLE group_user_membership (
    group_id bigint NOT NULL,
    user_id bigint NOT NULL
);



--
-- Name: permission; Type: TABLE; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE TABLE permission (
    id bigint NOT NULL,
    permission_name character varying(50)
);



--
-- Name: permission_id_seq; Type: SEQUENCE; Schema: public; Owner: grassroot
--

CREATE SEQUENCE permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;






--
-- Name: role; Type: TABLE; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE TABLE role (
    id bigint NOT NULL,
    role_name character varying(50)
);



--
-- Name: role_id_seq; Type: SEQUENCE; Schema: public; Owner: grassroot
--

CREATE SEQUENCE role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;





--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE TABLE role_permissions (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


--
-- Name: user_profile; Type: TABLE; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE TABLE user_profile (
    id bigint NOT NULL,
    created_date_time timestamp ,
    display_name character varying(70),
    enabled boolean,
    first_name character varying(255),
    language_code character varying(10),
    last_name character varying(255),
    password character varying(255),
    phone_number character varying(20) NOT NULL,
    user_name character varying(50),
    version integer,
    web boolean
);


--
-- Name: user_profile_id_seq; Type: SEQUENCE; Schema: public; Owner: grassroot
--

CREATE SEQUENCE user_profile_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE TABLE user_roles (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL
);



--
-- Name: verification_token_code; Type: TABLE; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE TABLE verification_token_code (
    id bigint NOT NULL,
    code character varying(255),
    creation_date timestamp ,
    expiry_date timestamp ,
    token_access_attempts integer,
    username character varying(255)
);



--
-- Name: verification_token_code_id_seq; Type: SEQUENCE; Schema: public; Owner: grassroot
--

CREATE SEQUENCE verification_token_code_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;






--
-- Name: id; Type: DEFAULT; Schema: public; Owner: grassroot
--

ALTER table event ALTER COLUMN id SET DEFAULT nextval('event_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: grassroot
--

ALTER table event_log ALTER COLUMN id SET DEFAULT nextval('event_log_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: grassroot
--

ALTER table group_profile ALTER COLUMN id SET DEFAULT nextval('group_profile_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: grassroot
--

ALTER table permission ALTER COLUMN id SET DEFAULT nextval('permission_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: grassroot
--

ALTER table role ALTER COLUMN id SET DEFAULT nextval('role_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: grassroot
--

ALTER table user_profile ALTER COLUMN id SET DEFAULT nextval('user_profile_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: grassroot
--

ALTER table verification_token_code ALTER COLUMN id SET DEFAULT nextval('verification_token_code_id_seq'::regclass);


--
-- Name: event_log_pkey; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table event_log
    ADD CONSTRAINT event_log_pkey PRIMARY KEY (id);


--
-- Name: event_pkey; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table event
    ADD CONSTRAINT event_pkey PRIMARY KEY (id);


--
-- Name: group_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table group_profile
    ADD CONSTRAINT group_profile_pkey PRIMARY KEY (id);


--
-- Name: permission_pkey; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);


--
-- Name: role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (permission_id, role_id);


--
-- Name: role_pkey; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);


--
-- Name: uk_5q4rc4fh1on6567qk69uesvyf; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table user_roles
    ADD CONSTRAINT uk_5q4rc4fh1on6567qk69uesvyf UNIQUE (role_id);


--
-- Name: uk_d4atqq8ege1sij0316vh2mxfu; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table role_permissions
    ADD CONSTRAINT uk_d4atqq8ege1sij0316vh2mxfu UNIQUE (role_id);


--
-- Name: uk_dd0g7xm8e4gtak3ka2h89clyh; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table user_profile
    ADD CONSTRAINT uk_dd0g7xm8e4gtak3ka2h89clyh UNIQUE (phone_number);


--
-- Name: uk_dr9d9bx3sxymtaak6nj7cim69; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table group_profile
    ADD CONSTRAINT uk_dr9d9bx3sxymtaak6nj7cim69 UNIQUE (group_token_code);


--
-- Name: uk_l6wgfhqrwuy4m1o7bs81ivg6x; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table user_profile
    ADD CONSTRAINT uk_l6wgfhqrwuy4m1o7bs81ivg6x UNIQUE (user_name);


--
-- Name: uk_qfkbccnh2c5o4tc7akq5x11wv; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table role_permissions
    ADD CONSTRAINT uk_qfkbccnh2c5o4tc7akq5x11wv UNIQUE (permission_id);


--
-- Name: user_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table user_profile
    ADD CONSTRAINT user_profile_pkey PRIMARY KEY (id);


--
-- Name: user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: verification_token_code_pkey; Type: CONSTRAINT; Schema: public; Owner: grassroot; Tablespace: 
--

ALTER table verification_token_code
    ADD CONSTRAINT verification_token_code_pkey PRIMARY KEY (id);


--
-- Name: idx_event_applies_to_group; Type: INDEX; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE INDEX idx_event_applies_to_group ON event USING btree (applies_to_group);


--
-- Name: idx_event_createdby_user; Type: INDEX; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE INDEX idx_event_createdby_user ON event USING btree (created_by_user);


--
-- Name: idx_eventlog_event_id; Type: INDEX; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE INDEX idx_eventlog_event_id ON event_log USING btree (event_id);


--
-- Name: idx_eventlog_eventlog_type; Type: INDEX; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE INDEX idx_eventlog_eventlog_type ON event_log USING btree (event_log_type);


--
-- Name: idx_group_profile_createdby_user; Type: INDEX; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE INDEX idx_group_profile_createdby_user ON group_profile USING btree (created_by_user);


--
-- Name: idx_group_profile_parent; Type: INDEX; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE INDEX idx_group_profile_parent ON group_profile USING btree (parent);


--
-- Name: idx_user_profile_phonenumber; Type: INDEX; Schema: public; Owner: grassroot; Tablespace: 
--

CREATE INDEX idx_user_profile_phonenumber ON user_profile USING btree (phone_number);


--
-- Name: fk_3ermupgdpina3lqmomccj6ibr; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table event_log
    ADD CONSTRAINT fk_3ermupgdpina3lqmomccj6ibr FOREIGN KEY (user_id) REFERENCES user_profile(id);


--
-- Name: fk_5q4rc4fh1on6567qk69uesvyf; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table user_roles
    ADD CONSTRAINT fk_5q4rc4fh1on6567qk69uesvyf FOREIGN KEY (role_id) REFERENCES role(id);


--
-- Name: fk_6fbe0u4h1voavkvbo9wc9gbn7; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table group_user_membership
    ADD CONSTRAINT fk_6fbe0u4h1voavkvbo9wc9gbn7 FOREIGN KEY (group_id) REFERENCES group_profile(id);


--
-- Name: fk_6we006nfcmfnvg7bnd97p829l; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table group_profile
    ADD CONSTRAINT fk_6we006nfcmfnvg7bnd97p829l FOREIGN KEY (parent) REFERENCES group_profile(id);


--
-- Name: fk_6x2nm63wuqyipy15xw5ly8frs; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table group_profile
    ADD CONSTRAINT fk_6x2nm63wuqyipy15xw5ly8frs FOREIGN KEY (created_by_user) REFERENCES user_profile(id);


--
-- Name: fk_7wcogaimb6iva5uyoddo54dm9; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table group_user_membership
    ADD CONSTRAINT fk_7wcogaimb6iva5uyoddo54dm9 FOREIGN KEY (user_id) REFERENCES user_profile(id);


--
-- Name: fk_d4atqq8ege1sij0316vh2mxfu; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table role_permissions
    ADD CONSTRAINT fk_d4atqq8ege1sij0316vh2mxfu FOREIGN KEY (role_id) REFERENCES role(id);


--
-- Name: fk_g1uebn6mqk9qiaw45vnacmyo2; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table user_roles
    ADD CONSTRAINT fk_g1uebn6mqk9qiaw45vnacmyo2 FOREIGN KEY (user_id) REFERENCES user_profile(id);


--
-- Name: fk_mg2hi0r8tfcbcopkqqrudb9g2; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table event
    ADD CONSTRAINT fk_mg2hi0r8tfcbcopkqqrudb9g2 FOREIGN KEY (created_by_user) REFERENCES user_profile(id);


--
-- Name: fk_mwl80j90k3p6444g7hal1ugg2; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table event
    ADD CONSTRAINT fk_mwl80j90k3p6444g7hal1ugg2 FOREIGN KEY (applies_to_group) REFERENCES group_profile(id);


--
-- Name: fk_qfkbccnh2c5o4tc7akq5x11wv; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table role_permissions
    ADD CONSTRAINT fk_qfkbccnh2c5o4tc7akq5x11wv FOREIGN KEY (permission_id) REFERENCES permission(id);


--
-- Name: fk_sjoer2novly8reiok6rbbifhd; Type: FK CONSTRAINT; Schema: public; Owner: grassroot
--

ALTER table event_log
    ADD CONSTRAINT fk_sjoer2novly8reiok6rbbifhd FOREIGN KEY (event_id) REFERENCES event(id);


-- PostgreSQL database dump complete
--

