#!/bin/sh

source send_cmd_pipe.sh

if  [ -n "/dev/nanohub" ]; then

    sensorhubtester  config  accel  true 100 100000
    sleep 1
    sensorhubtester  config  gyro  true 100 100000
    sleep 1
    sensorhubtester  config  mag   true 100 100000 -d &

    echo "sensorhubtester id $3"
    SEND_CMD_PIPE_OK $3

    exit 0
else
    SEND_CMD_PIPE_FAIL $3
fi
