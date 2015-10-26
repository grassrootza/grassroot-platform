INSERT INTO user_profile (id,phone_number, user_name ,password, enabled,web,version) VALUES
(0,'27744448888','27744448888','$2a$10$CoEVqSyDD53QF66sOb3.o.kF6jEDjX4es028b/vC8.i8LypvV7LPW',true,true,1);


ALTER TABLE user_roles DROP CONSTRAINT  uk_5q4rc4fh1on6567qk69uesvyf;

insert INTO  user_roles (user_id, role_id) VALUES
(0,1);