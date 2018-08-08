#!/bin/bash

targetdir=$(cd $(dirname $0) && pwd)/../..

if [ -d $targetdir/common/adb ]; then
	make -C $targetdir/common/adb clean
fi

if [ -d $targetdir/dragonboard ]; then
	make -C $targetdir/dragonboard/src clean
	rm -rf $targetdir/dragonboard/output/bin/hawkview
	rm -rf $targetdir/dragonboard/src/lib/libscript.a
	rm -rf $targetdir/dragonboard/*root*
fi

if [ -d $targetdir/dragonmat ]; then
	make -C $targetdir/dragonmat/src clean
	rm -rf $targetdir/dragonmat/output/bin/*
	rm -rf $targetdir/dragonmat/*root*
	rm -rf $targetdir/dragonmat/src/lib/libscript.a
fi
