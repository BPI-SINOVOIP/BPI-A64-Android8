#!/bin/sh

#source script_parser.sh
source send_cmd_pipe.sh


if ifconfig -a | grep eth0; then
	ifconfig eth0 down
	sleep 1
	
	ifconfig eth0 up
	if [ $? -ne 0 ];then

		SEND_CMD_PIPE_FALL $3
		exit 1
    fi	

	/etc/init.d/auto_config_network	
	#sleep 10
	
	ping_address=$(ping -c 4 www.baidu.com);

	ping_cmd=$(echo $ping_address | grep -c "bytes from");
	
	if [ $ping_cmd -eq 1 ]; then
		SEND_CMD_PIPE_OK $3
	else	
		SEND_CMD_PIPE_FAIL $3
		exit 1		
	fi

#	ifconfig eth0 down
#	if [ $? -ne 0 ]; then
#		SEND_CMD_PIPE_FAIL $3
#		exit 1
#	fi
	
else

	SEND_CMD_PIPE_FAIL $3
	exit 1
fi
