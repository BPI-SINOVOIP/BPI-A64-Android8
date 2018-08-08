#!/vendor/bin/sh

if [ -f $1 ]; then
	/vendor/bin/usb_modeswitch -W -I -c $1
else
	echo "$1 does not exist."
fi

