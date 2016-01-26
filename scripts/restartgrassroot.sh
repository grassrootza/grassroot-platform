#!/bin/bash
#service apache2 restart > /var/log/restartapache.out 2>&1
if [[ -f /var/grassroot/.pid ]]; then
	kill -s SIGINT `cat /var/grassroot/.pid`;
	rm -f  /var/grassroot/.pid;
fi
. $PWD/startgrassroot.sh
