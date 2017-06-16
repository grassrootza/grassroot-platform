#!/bin/bash

if [[ -f $GRASSROOT_HOME/.pid ]]; then
        #kill -TERM `cat $GRASSROOT_HOME/.pid`;
        kill `cat $GRASSROOT_HOME/.pid`;
	mypid=`cat $GRASSROOT_HOME/.pid`;
	while [[ `ps -p $mypid > /dev/null;echo $?` -eq '0' ]]; do 
		echo -n '.'; 
		sleep 1; 
	done
        rm -f  $GRASSROOT_HOME/.pid;
fi

echo STOPPING DONE
