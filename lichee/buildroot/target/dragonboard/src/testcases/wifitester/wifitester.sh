#!/bin/sh
##############################################################################
# \version	 1.0.0
# \date		2012年05月31日
# \author	  James Deng <csjamesdeng@allwinnertech.com>
# \Descriptions:
#			create the inital version

# \version	 1.1.0
# \date		2012年09月26日
# \author	  Martin <zhengjiewen@allwinnertech.com>
# \Descriptions:
#			add some new features:
#			1.wifi hotpoint ssid and single strongth san
#			2.sort the hotpoint by single strongth quickly
##############################################################################
source send_cmd_pipe.sh
source script_parser.sh

for file in `ls /dragonboard/bin/*.bin /dragonboard/bin/*.txt`; do
	filename=`echo $file | awk -F "/" '{print $NF}'`
	if [ ! -f /system/vendor/modules/$filename ]; then
		ln -s /dragonboard/bin/$filename /system/vendor/modules/$filename
	fi
done

WIFI_PIPE=/tmp/wifi_pipe
wlan_try=0

module_path=`script_fetch "wifi" "module_path"`
module_args=`script_fetch "wifi" "module_args"`

if [ -z "$module_path" ]; then
	SEND_CMD_PIPE_FAIL $3
	exit 1
fi
flag="######"

echo "insmod $module_path $module_args"
insmod "$module_path" "$module_args"
if [ $? -ne 0 ]; then
	SEND_CMD_PIPE_FAIL $3
	exit 1
fi

sleep 3

if ifconfig -a | grep wlan0; then
	for i in `seq 3`; do
		ifconfig wlan0 up > /dev/null
		sleep 3
		if [ $? -ne 0 -a $i -eq 3 ]; then
			echo "ifconfig wlan0 up failed, no more try"
			SEND_CMD_PIPE_FAIL $3
			exit 1
		fi
		if [ $? -ne 0 ]; then
			echo "ifconfig wlan0 up failed, try again 1s later"
			sleep 1
		else
			break
		fi
	done
fi

while true ; do
	wifi=$(iw wlan0 scan | awk -F"[:|=]" '(NF&&$1~/^[[:space:]]*SSID/) \
									{printf "%s:",substr($2,2,length($2)-2)} \
									(NF&&/[[:space:]]*signal/){printf "%d\n",$2 }' \
									| sort -n -r -k 2 -t :)
	for item in $wifi ; do
		echo $item >> $WIFI_PIPE
	done

	echo $flag >> $WIFI_PIPE
	sleep 3

	SEND_CMD_PIPE_OK $3
	sleep 1
done

echo "wlan0 not found, no more try"
SEND_CMD_PIPE_FAIL $3
exit 1
