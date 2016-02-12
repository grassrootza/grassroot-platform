#!/bin/bash

if [[ -f /var/grassroot/.pid ]]; then
        kill -TERM `cat /var/grassroot/.pid`;
	mypid = `cat /var/grassroot/.pid`;
	while [[ `ps -p $mypid > /dev/null;echo $?` -eq '1' ]]; do 
		echo -n '.'; 
		sleep 1; 
	done
        rm -f  /var/grassroot/.pid;
fi

echo STOPPING DONE
