drop index if exists address_post_code;

alter table address drop column postal_code;
alter table address drop column town_or_city;

update address set location_source = '0' where location_source = 'CALCULATED';
update address set location_source = '1' where location_source = 'LOGGED_APPROX';
update address set location_source = '2' where location_source = 'LOGGED_PRECISE';
update address set location_source = '3' where location_source = 'LOGGED_MULTIPLE';
update address set location_source = '4' where location_source = 'UNKNOWN';

