alter table address add column postal_code varchar(20);
alter table address add column town_or_city varchar(255);

update address set location_source = 'CALCULATED' where location_source = '0';
update address set location_source = 'LOGGED_APPROX' where location_source = '1';
update address set location_source = 'LOGGED_PRECISE' where location_source = '2';
update address set location_source = 'LOGGED_MULTIPLE' where location_source = '3';
update address set location_source = 'UNKNOWN' where location_source = '4';

create index address_post_code on address(postal_code);