#!/bin/bash
##############################################################################
# \version     1.0.0
# \date        2012年05月31日
# \author      James Deng <csjamesdeng@allwinnertech.com>
# \Descriptions:
#			create the inital version

# \version     1.1.0
# \date        2012年09月26日
# \author      Martin <zhengjiewen@allwinnertech.com>
# \Descriptions:
#			add some new features:
#			1.wifi hotpoint ssid and single strongth san
#			2.sort the hotpoint by single strongth quickly
##############################################################################
source send_cmd_pipe.sh
source script_parser.sh
module_path=`script_fetch "bluetooth" "module_path"`
loop_time=`script_fetch "bluetooth" "test_time"`
destination_bt=`script_fetch "bluetooth" "dst_bt"`
device_node=`script_fetch "bluetooth" "device_node"`
baud_rate=`script_fetch "bluetooth" "baud_rate"`
bt_vnd=`script_fetch "bluetooth" "bt_vnd"`

echo "module_path   : "$module_path
echo "loop_time     : "$loop_time
echo "destination_bt: "$destination_bt
echo "device_node   : "$device_node
echo "baud_rate     : "$baud_rate
echo "bt_vnd        : "$bt_vnd

if [ -z "$bt_vnd" ]; then
	bt_vnd="broadcom"
	echo "Warning: bt_vnd in test_config.fex is not set, use defaut: broadcom"
fi

if [ -z "$baud_rate" ]; then
	baud_rate="115200"
	echo "Warning: baud_rate in test_config.fex is not set, use defaut: 115200"
fi

if [ "X_$bt_vnd" == "X_realtek" ]; then
	cfgpgm=hciattach
	cfgpara="-n -s 115200 $device_node rtk_h5"

	chipname=`script_fetch "bluetooth" "rtk_bt_chip"`
	echo "chipname      : "$chipname

	cp /dragonboard/bin/"$chipname"_config /system/vendor/modules/rtlbt_config
	cp /dragonboard/bin/"$chipname"_fw /system/vendor/modules/rtlbt_fw
	cp /dragonboard/bin/btmac.txt /system/vendor/modules/btmac.txt
	tsleep=10
elif [ "X_$bt_vnd" == "X_broadcom" ]; then
	cfgpgm=brcm_patchram_plus
	cfgpara="--tosleep=50000 --no2bytes --enable_hci --scopcm=0,2,0,0,0,0,0,0,0,0 --baudrate $baud_rate --patchram $module_path $device_node"

	for file in `ls /dragonboard/bin/*.hcd /dragonboard/bin/*.bin /dragonboard/bin/*.txt`; do
		filename=`echo $file | awk -F "/" '{print $NF}'`
		if [ ! -f /system/vendor/modules/$filename ]; then
			ln -s /dragonboard/bin/$filename /system/vendor/modules/$filename
		fi
	done
	tsleep=20
else
	echo "Unsuport bt_vnd, exit."
	exit 1
fi

tryloop=1
while true; do
	let "tryloop++"

	pid=`ps | awk '{print $1" "$3}' | grep $cfgpgm`

	for kp in `echo "$pid" | awk '{print $1}'`; do
		kill -9 $kp
	done

	rfkillpath="/sys/class/rfkill"

	echo 0 > $rfkillpath/rfkill0/state
	echo 1 > $rfkillpath/rfkill0/state

	$cfgpgm $cfgpara > /tmp/.btcfglog 2>&1 &
	wt=0
	while true; do
		if [ "`grep "Done setting line discpline" /tmp/.btcfglog`" != "" ] \
		|| [ "`grep "Realtek Bluetooth post process" /tmp/.btcfglog`" != "" ] \
		|| [ $wt -ge $tsleep ]; then
			break
		else
			sleep 1
			let "wt=wt+1"
		fi
	done

	if [ $wt -ge $tsleep ]; then
		echo "Firmware load timeout, time: ${wt}s"
	else
		echo "Firmware load finish, time: ${wt}s"
	fi

	for node in `ls $rfkillpath`; do
		devtype=`cat "$rfkillpath/$node/type" | grep bluetooth`
		devname=`cat "$rfkillpath/$node/name" | grep hci0`
		if [ "X_$devtype" == "X_bluetooth" ] && [ "X_$devname" == "X_hci0" ]; then
			statepath="$rfkillpath/$node/state"
			echo 1 > $statepath
			break
		fi
	done

	if [ -z $statepath ]; then
		if [ $tryloop -gt 3 ]; then
			SEND_CMD_PIPE_FAIL_EX $3 "Bluetooth can't find hci port."
			echo "Bluetooth can't find hci port."
			exit 1
		fi
	else
		break
	fi
done

hciconfig hci0 up

if [ $? -ne 0 ]; then
	SEND_CMD_PIPE_FAIL_EX $3 "Bluetooth can't bring up hci port."
	echo "Bluetooth can't bring up hci port."
	exit 1
fi

for((i=1;i<=${loop_time} ;i++));  do
	devlist=`hcitool scan | grep "${destination_bt}"`
	if [ ! -z "$devlist" ]; then
		echo -e "Bluetooth found devices list:\n\n$devlist"
		devlist=`echo "$devlist" | awk '{print $2}'`
		devlist=`echo $devlist`
		SEND_CMD_PIPE_OK_EX $3 "List: $devlist"
		hciconfig hci0 down
		exit 1
	fi
done

SEND_CMD_PIPE_FAIL_EX $3 "Bluetooth OK, but no device found."
echo "Bluetooth OK, but no device found."
