#!/bin/bash
# For all devices listed under adb, disable wifi setting on those devices
device_output=$(adb devices | grep -e 'device$' | awk '{print $1}')
echo $device_output

arr=$(echo $device_output | tr " " "\n");
for x in $arr
do
    echo "disable wifi on device $x";
    adb -s $x root
    sleep 2;
    adb -s $x wait-for-device shell svc wifi disable
done
