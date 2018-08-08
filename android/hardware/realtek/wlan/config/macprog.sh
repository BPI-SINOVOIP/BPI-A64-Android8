#!/vendor/bin/sh

macaddr=$(getprop ro.boot.mac)
macaddr=$(echo $macaddr | tr [a-z] [A-Z])

function verify()
{
	local mac=$1
	local ret=0
	if [ -z "$mac" ]; then
		ret=1
	elif [ "$(echo $mac | wc -c)" -ne 18 ]; then
		ret=1
	elif [ "$mac" = "00:00:00:00:00:00" ]; then
		ret=1
	elif [ "$mac" = "FF:FF:FF:FF:FF:FF" ]; then
		ret=1
	elif [ "${mac:4:13}" = "7:13:36:E4:4B" ]; then
		ret=1
	elif [ "${mac:4:13}" = "9:CB:C9:A4:EC" ]; then
		ret=1
	fi
	return $ret
}

verify $macaddr

if [ $? -ne "0" ]; then
	macaddr="FC"
	macaddr="$macaddr:$(printf "%02X" $((RANDOM/128)))"
	macaddr="$macaddr:$(printf "%02X" $((RANDOM/128)))"
	macaddr="$macaddr:$(printf "%02X" $((RANDOM/128)))"
	macaddr="$macaddr:$(printf "%02X" $((RANDOM/128)))"
	macaddr="$macaddr:$(printf "%02X" $((RANDOM/128)))"
fi

if [ -n "$macaddr" ]; then
	base=0x${macaddr:0:2}
	let "val=base&0xFC"
	val=$(printf "%02X" $val)
	macaddr=${val}${macaddr:2:15}

	base=0x${macaddr:3:2}

	wifi_file=/data/misc/wifi/wifimac.txt
	bt_file=$(getprop ro.bt.bdaddr_path)

	if [ -z "$bt_file" ]; then
		bt_file=/data/misc/bluetooth/bdaddr
	fi

	if [ ! -f "$wifi_file" ]; then
		let "val=base&0x0F|0x00"

		val=$(printf "%02X" $val)

		wlan_mac=${macaddr:0:3}${val}${macaddr:5:12}

		echo $wlan_mac > $wifi_file
		chmod 644 $wifi_file
	fi

	if [ ! -f "$bt_file" ]; then
		let "val=base&0x0F|0x20"
		val=$(printf "%02X" $val)

		bt_mac=${macaddr:0:3}${val}${macaddr:5:12}

		echo $bt_mac > $bt_file
		chmod 644 $bt_file
	fi
fi
