# grassroot-platform
Application to make it faster, cheaper and easier to persistently organize and mobilize people in low-income communities. 

The platform is built with the Spring framework and launches through Spring Boot. To run it on a local environment, use 
the profile "localpg". It requires some local set up and configuration, namely:

1 -- A local PostgreSQL DB, named "grassroot", owned by a user named "grassroot" with the password "verylongpassword".
All of those values can be adjusted if corresponding changes are made in the StandaloneLocalPGConfig in grassroot-core.

2 -- A configuration file, ~/grassroot/grassroot-integration.properties. This should set the following properties:

* The SMS gateway for SMS notifications (grassroot.sms.gateway for the host, and grassroot.sms.gateway.username and
grassroot.sms.gateway.password)

* A GCM sender ID and key for push notifications (gcm.sender.id and gcm.sender.key), unless GCM is turned off by setting
property gcm.connection.enabled=false in application-localpg.properties in grassroot-webapp

* Usual Spring Mail properties, plus grassroot.mail.from.address, grassroot.mail.from.name, and grassroot.system.mail
(destination for daily summary mail of activity on the platform), unless email is disabled via grassroot.email.enabled

* The name of AWS S3 buckets in which to store task images, if desired

3 -- A configuration file, ~/grassroot/grassroot-payments.properties, with details of the payments provider, only
necessary if billing and payments are switched on via grassroot.accounts.active, grassroot.billing.enabled and 
grassroot.payments.enabled, in application-localpg.properties.