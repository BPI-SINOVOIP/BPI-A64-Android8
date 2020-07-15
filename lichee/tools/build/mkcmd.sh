# scripts/mkcmd.sh
#
# (c) Copyright 2013
# Allwinner Technology Co., Ltd. <www.allwinnertech.com>
# James Deng <csjamesdeng@allwinnertech.com>
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.

# Notice:
#   1. This script muse source at the top directory of lichee.

BUILD_CONFIG=.buildconfig
cpu_cores=`cat /proc/cpuinfo | grep "processor" | wc -l`
if [ ${cpu_cores} -le 8 ] ; then
	LICHEE_JLEVEL=${cpu_cores}
else
	LICHEE_JLEVEL=`expr ${cpu_cores} / 2`
fi

export LICHEE_JLEVEL

function mk_error()
{
	echo -e "\033[47;31mERROR: $*\033[0m"
}

function mk_warn()
{
	echo -e "\033[47;34mWARN: $*\033[0m"
}

function mk_info()
{
	echo -e "\033[47;30mINFO: $*\033[0m"
}

# define importance variable
LICHEE_TOP_DIR=`pwd`
LICHEE_KERN_DIR=${LICHEE_TOP_DIR}/${LICHEE_KERN_VER}
LICHEE_ARCH_DIR=${LICHEE_KERN_DIR}/${LICHEE_ARCH}
LICHEE_TOOLS_DIR=${LICHEE_TOP_DIR}/tools
LICHEE_SATA_DIR=${LICHEE_TOP_DIR}/SATA
LICHEE_OUT_DIR=${LICHEE_TOP_DIR}/out
MKRULE_FILE=${LICHEE_TOOLS_DIR}/build/mkrule

# add support for buildroot-201611 support
if [ -n "`echo $LICHEE_KERN_VER | grep "linux-4.4"`" ]; then
	LICHEE_BR_DIR=${LICHEE_TOP_DIR}/buildroot-201611
else
	LICHEE_BR_DIR=${LICHEE_TOP_DIR}/buildroot
fi


# make surce at the top directory of lichee
if [ ! -d ${LICHEE_KERN_DIR} -o \
	! -d ${LICHEE_TOOLS_DIR} ] ; then
	mk_error "You are not at the top directory of lichee."
	mk_error "Please changes to that directory."
	exit 1
fi

# export importance variable
export LICHEE_TOP_DIR
export LICHEE_BR_DIR
export LICHEE_KERN_DIR
export LICHEE_ARCH_DIR
export LICHEE_TOOLS_DIR
export LICHEE_OUT_DIR

platforms=(
"android"
"dragonboard"
"linux"
"camdroid"
)

function save_config()
{
	local cfgkey=$1
	local cfgval=$2
	local cfgfile=$3

	if [ -f $cfgfile ] && [ -n "$(grep "^[	 ]*export[	 ]\+$cfgkey[	 ]*=" $cfgfile)" ]; then
		sed -i "s/\(^[	 ]*export[	 ]\+$cfgkey[	 ]*=\).*/\1$cfgval/g" $cfgfile
	else
		echo "export $cfgkey=$cfgval" >> $cfgfile
	fi
}

function load_config()
{
	local cfgkey=$1
	local cfgfile=$2

	if [ -f $cfgfile ]; then
		echo $(grep "^[	 ]*export[	 ]\+$cfgkey[	 ]*=" $cfgfile | tail -n 1 | grep -oP "(?<==).*" | awk '{sub("^[	 ]*","");print}')
	fi
}

#
# This function can get the realpath between $SRC and $DST
#
function get_realpath()
{
	local src=$(cd $1; pwd);
	local dst=$(cd $2; pwd);
	local res="./";
	local tmp="$dst"

	while [ "${src##*$tmp}" == "${src}" ]; do
		tmp=${tmp%/*};
		res=$res"../"
	done
	res="$res${src#*$tmp/}"

	printf "%s" $res
}

function check_env()
{
	if [ -z "${LICHEE_CHIP}" -o \
		-z "${LICHEE_PLATFORM}" -o \
		-z "${LICHEE_KERN_VER}" -o \
		-z "${LICHEE_ARCH}" -o \
		-z "${LICHEE_BOARD}" ] ; then
		mk_error "run './build.sh config' setup env"
		exit 1
	fi

	cd ${LICHEE_TOOLS_DIR}
	ln -sfT $(get_realpath pack/chips/ ./)/${LICHEE_CHIP} product
	cd - > /dev/null
}

function init_defconf()
{
	local pattern
	local defconf
	local out_dir="common"

	check_env

	pattern="${LICHEE_CHIP}_${LICHEE_PLATFORM}_${LICHEE_BOARD}"
	defconf=`awk '$1=="'$pattern'" {print $2,$3}' ${MKRULE_FILE}`

	if [ -n "${defconf}" ] ; then
		export LICHEE_BR_DEFCONF=`echo ${defconf} | awk '{print $1}'`
		export LICHEE_KERN_DEFCONF=`echo ${defconf} | awk '{print $2}'`
		# almost null, using common as defconfig, depends on mkrule file
		out_dir="${LICHEE_BOARD}"
	else
		pattern="${LICHEE_CHIP}_${LICHEE_PLATFORM}_${LICHEE_ARCH}"
		defconf=`awk '$1=="'$pattern'" {print $2,$3}' ${MKRULE_FILE}`
		if [ -n "${defconf}" ] ; then
			export LICHEE_BR_DEFCONF=`echo ${defconf} | awk '{print $1}'`
			export LICHEE_KERN_DEFCONF=`echo ${defconf} | awk '{print $2}'`
			out_dir="common"
		else
			pattern="${LICHEE_CHIP}_${LICHEE_PLATFORM}"
			defconf=`awk '$1=="'$pattern'" {print $2,$3}' ${MKRULE_FILE}`
			if [ -n "${defconf}" ] ; then
				export LICHEE_BR_DEFCONF=`echo ${defconf} | awk '{print $1}'`
				export LICHEE_KERN_DEFCONF=`echo ${defconf} | awk '{print $2}'`
				out_dir="common"
			fi
		fi
	fi
	# mark as diff for output dir & buildroot output dir
	export LICHEE_PLAT_OUT="${LICHEE_OUT_DIR}/${LICHEE_CHIP}/${LICHEE_PLATFORM}/${out_dir}"

	export LICHEE_BR_OUT="${LICHEE_PLAT_OUT}/buildroot"
	mkdir -p ${LICHEE_BR_OUT}
}

function list_subdir()
{
	echo "$(eval "$(echo "$(ls -d $1/*/)" | sed  "s/^/basename /g")")"
}

function init_key()
{
	local val_list=$1
	local cfg_key=$2
	local cfg_val=$3

	if [ -n "$(echo $val_list | grep -w $cfg_val)" ]; then
		export $cfg_key=$cfg_val
		return 0
	else
		return 1
	fi
}

function init_chips()
{
	local cfg_val=$1 # chip
	local cfg_key="LICHEE_CHIP"
	local val_list=$(list_subdir $LICHEE_TOOLS_DIR/pack/chips)
	init_key "$val_list" "$cfg_key" "$cfg_val"
	return $?
}

function init_platforms()
{
	local cfg_val=$1 # platform
	local cfg_key="LICHEE_PLATFORM"
	local val_list=${platforms[@]}
	init_key "$val_list" "$cfg_key" "$cfg_val"
	return $?
}

function init_kern_ver()
{
	local cfg_val=$1 # kern_ver
	local cfg_key="LICHEE_KERN_VER"
	local val_list=$(list_subdir $LICHEE_TOP_DIR | grep "linux-")
	init_key "$val_list" "$cfg_key" "$cfg_val"
	return $?
}

function init_arch()
{
	local cfg_val=$1 # arch
	local cfg_key="LICHEE_ARCH"
	local val_list=$(list_subdir $LICHEE_KERN_DIR/arch | grep "arm")
	init_key "$val_list" "$cfg_key" "$cfg_val"
	return $?
}

function init_boards()
{
	local chip=$1
	local cfg_val=$2 # board
	local cfg_key="LICHEE_BOARD"
	local val_list=$(list_subdir $LICHEE_TOOLS_DIR/pack/chips/$chip/configs | grep -v default)
	init_key "$val_list" "$cfg_key" "$cfg_val"
	return $?
}

function mk_select()
{
	local val_list=$1
	local cfg_key=$2
	local cnt=0
	local cfg_val=$(load_config $cfg_key $BUILD_CONFIG)
	local cfg_idx=0
	local banner=$(echo ${cfg_key:7} | tr '[:upper:]' '[:lower:]')

	printf "All available $banner:\n"
	for val in $val_list; do
		array[$cnt]=$val
		if [ "X_$cfg_val" == "X_${array[$cnt]}" ]; then
			cfg_idx=$cnt
		fi
		printf "%4d. %s\n" $cnt $val
		let "cnt++"
	done
	while true; do
		read -p "Choice [${array[$cfg_idx]}]: " choice
		if [ -z "${choice}" ]; then
			choice=$cfg_idx
		fi

		if [ -z "${choice//[0-9]/}" ] ; then
			if [ $choice -ge 0 -a $choice -lt $cnt ] ; then
				cfg_val="${array[$choice]}"
				break;
			fi
		fi
		 printf "Invalid input ...\n"
	done
	export $cfg_key=$cfg_val
	save_config "$cfg_key" "$cfg_val" $BUILD_CONFIG
}

function list_subdir()
{
	echo "$(eval "$(echo "$(ls -d $1/*/)" | sed  "s/^/basename /g")")"
}

function mk_config()
{
	select_platform
	select_chip
	select_kern_ver
	select_arch
	select_board
}

function select_chip()
{
	local val_list=$(list_subdir $LICHEE_TOOLS_DIR/pack/chips)
	local cfg_key="LICHEE_CHIP"
	mk_select "$val_list" "$cfg_key"
}

function select_platform()
{
	local val_list="${platforms[@]}"
	local cfg_key="LICHEE_PLATFORM"
	mk_select "$val_list" "$cfg_key"
}

function select_kern_ver()
{
	local val_list=$(list_subdir $LICHEE_TOP_DIR | grep "linux-")
	local cfg_key="LICHEE_KERN_VER"
	mk_select "$val_list" "$cfg_key"
}

function select_arch()
{
	# for all self config, we normal not use
	if [ x${CONFIG_ALL} == x${FLAGS_TRUE} ]; then
		local val_list=$(list_subdir $LICHEE_KERN_DIR/arch | grep "arm")
		local cfg_key="LICHEE_ARCH"
		mk_select "$val_list" "$cfg_key"
	else
		if [ -n "`echo ${LICHEE_CHIP} | grep "sun5[0-9]i"`" ]; then
			export LICHEE_ARCH="arm64"
		else
			export LICHEE_ARCH="arm"
		fi
		save_config "LICHEE_ARCH" "$LICHEE_ARCH" $BUILD_CONFIG
	fi
}

function select_board()
{
	local val_list=$(list_subdir $LICHEE_TOOLS_DIR/pack/chips/$LICHEE_CHIP/configs | grep -v default)
	local cfg_key="LICHEE_BOARD"
	mk_select "$val_list" "$cfg_key"
}

function mkbr()
{
	mk_info "build buildroot ..."

	local build_script="scripts/build.sh"

	prepare_toolchain

	(cd ${LICHEE_BR_DIR} && [ -x ${build_script} ] && ./${build_script})
	[ $? -ne 0 ] && mk_error "build buildroot Failed" && return 1

	mk_info "build buildroot OK."
}

function clbr()
{
	mk_info "clean buildroot ..."

	local build_script="scripts/build.sh"
	(cd ${LICHEE_BR_DIR} && [ -x ${build_script} ] && ./${build_script} "clean")

	mk_info "clean buildroot OK."
}

function prepare_toolchain()
{
	local ARCH="";
	local GCC="";
	local GCC_PREFIX="";
	local toolchain_archive="";
	local tooldir="";

	mk_info "Prepare toolchain ..."

	if [ "x${LICHEE_ARCH}" = "xarm64" ]; then
		ARCH="aarch64"
		if [ -n "`echo $LICHEE_KERN_VER | grep "linux-4.4"`" ]; then
			toolchain_archive="${LICHEE_TOOLS_DIR}/build/toolchain/gcc-linaro-5.3.1-2016.05-x86_64_aarch64-linux-gnu.tar.xz";
		else
			toolchain_archive="${LICHEE_TOOLS_DIR}/build/toolchain/gcc-linaro-aarch64.tar.xz";
		fi
	elif [ "x${LICHEE_ARCH}" = "xarm" ]; then
		ARCH="arm"
		if [ -n "`echo $LICHEE_KERN_VER | grep "linux-4.4"`" ] || [ -n "`echo $LICHEE_KERN_VER | grep "linux-4.9"`" ]; then
			toolchain_archive="${LICHEE_TOOLS_DIR}/build/toolchain/gcc-linaro-5.3.1-2016.05-x86_64_arm-linux-gnueabi.tar.xz";
		else
			toolchain_archive="${LICHEE_TOOLS_DIR}/build/toolchain/gcc-linaro-arm.tar.xz";
		fi
	else
		exit 1
	fi

	if [ -n "`echo $LICHEE_KERN_VER | grep "linux-4.4"`" ] || [ -n "`echo $LICHEE_KERN_VER | grep "linux-4.9"`" ]; then
		tooldir=${LICHEE_OUT_DIR}/gcc-linaro-5.3.1-2016.05/gcc-${ARCH}
	else
		tooldir=${LICHEE_OUT_DIR}/external-toolchain/gcc-${ARCH}
	fi

	if [ ! -d "${tooldir}" ]; then
		mkdir -p ${tooldir} || exit 1
		tar --strip-components=1 -xf ${toolchain_archive} -C ${tooldir} || exit 1
	fi

	GCC=$(find ${tooldir} -perm /a+x -a -regex '.*-gcc');
	if [ -z "${GCC}" ]; then
		tar --strip-components=1 -xf ${toolchain_archive} -C ${tooldir} || exit 1
		GCC=$(find ${tooldir} -perm /a+x -a -regex '.*-gcc');
	fi
	GCC_PREFIX=${GCC##*/};

	if [ "${tooldir}" == "${LICHEE_TOOLCHAIN_PATH}" \
		-a "${LICHEE_CROSS_COMPILER}-gcc" == "${GCC_PREFIX}" \
		-a -x "${GCC}" ]; then
		return
	fi

	if ! echo $PATH | grep -q "${tooldir}" ; then
		export PATH=${tooldir}/bin:$PATH
	fi

	LICHEE_CROSS_COMPILER="${GCC_PREFIX%-*}";

	if [ -n ${LICHEE_CROSS_COMPILER} ]; then
		if [ -f ${BUILD_CONFIG} ]; then
			sed -i '/LICHEE_CROSS_COMPILER.*/d' ${BUILD_CONFIG}
			sed -i '/LICHEE_TOOLCHAIN_PATH.*/d' ${BUILD_CONFIG}
		fi
		export LICHEE_CROSS_COMPILER=${LICHEE_CROSS_COMPILER}
		export LICHEE_TOOLCHAIN_PATH=${tooldir}
		save_config "LICHEE_CROSS_COMPILER" "$LICHEE_CROSS_COMPILER" $BUILD_CONFIG
		save_config "LICHEE_TOOLCHAIN_PATH" "$tooldir" $BUILD_CONFIG
	fi
}

function prepare_dragonboard_toolchain()
{
	local ARCH="arm";
	local GCC="";
	local GCC_PREFIX="";
	local toolchain_archive="${LICHEE_TOOLS_DIR}/build/toolchain/gcc-linaro-5.3.1-2016.05-x86_64_arm-linux-gnueabi.tar.xz";
	local tooldir="";

	mk_info "Prepare dragonboard toolchain ..."
	tooldir=${LICHEE_OUT_DIR}/gcc-linaro-5.3.1-2016.05/dragonboard/gcc-arm

	if [ ! -d "${tooldir}" ]; then
		mkdir -p ${tooldir} || exit 1
		tar --strip-components=1 -xf ${toolchain_archive} -C ${tooldir} || exit 1
	fi


	GCC=$(find ${tooldir} -perm /a+x -a -regex '.*-gcc');
	if [ -z "${GCC}" ]; then
		tar --strip-components=1 -xf ${toolchain_archive} -C ${tooldir} || exit 1
		GCC=$(find ${tooldir} -perm /a+x -a -regex '.*-gcc');
	fi
	GCC_PREFIX=${GCC##*/};

	if [ "${tooldir}" == "${LICHEE_TOOLCHAIN_PATH}" \
		-a "${LICHEE_CROSS_COMPILER}-gcc" == "${GCC_PREFIX}" \
		-a -x "${GCC}" ]; then
		return
	fi

	if ! echo $PATH | grep -q "${tooldir}" ; then
		export PATH=${tooldir}/bin:$PATH
	fi


	LICHEE_CROSS_COMPILER="${GCC_PREFIX%-*}";

	if [ -n ${LICHEE_CROSS_COMPILER} ]; then
		export LICHEE_CROSS_COMPILER=${LICHEE_CROSS_COMPILER}
		export LICHEE_TOOLCHAIN_PATH=${tooldir}
	fi
}

function mkkernel()
{
	mk_info "build kernel ..."

	local build_script="scripts/build.sh"

	prepare_toolchain

	# mark kernel .config belong to which platform
	local config_mark="${LICHEE_KERN_DIR}/.config.mark"
	if [ -f ${config_mark} ] ; then
		if ! grep -q "${LICHEE_CHIP}_${LICHEE_BOARD}_${LICHEE_PLATFORM}" ${config_mark} ; then
			mk_info "clean last time build for different platform"
			(cd ${LICHEE_KERN_DIR} && [ -x ${build_script} ] && ./${build_script} "clean")
			rm -rf ${LICHEE_KERN_DIR}/.config
			echo "${LICHEE_CHIP}_${LICHEE_BOARD}_${LICHEE_PLATFORM}" > ${config_mark}
		fi
	else
		echo "${LICHEE_CHIP}_${LICHEE_BOARD}_${LICHEE_PLATFORM}" > ${config_mark}
	fi

	(cd ${LICHEE_KERN_DIR} && [ -x ${build_script} ] && ./${build_script})
	[ $? -ne 0 ] && mk_error "build kernel Failed" && return 1

	mk_info "build kernel OK."
}

function clkernel()
{
	local clarg="clean"

	if [ "x$1" == "xdistclean" ]; then
		clarg="distclean"
	fi

	mk_info "clean kernel ..."

	local build_script="scripts/build.sh"

	prepare_toolchain

	(cd ${LICHEE_KERN_DIR} && [ -x ${build_script} ] && ./${build_script} "$clarg")

	mk_info "clean kernel OK."
}

function cldragonboard()
{
	local tooldir=${LICHEE_OUT_DIR}/gcc-linaro-5.3.1-2016.05/dragonboard/gcc-arm
	[ ! -d ${tooldir} ] && return
	mk_info "clean dragonboard ..."

	prepare_dragonboard_toolchain

	local script_dir="${LICHEE_TOP_DIR}/buildroot/target/common/scripts/"

	local clean_script="clean.sh"
	(cd ${script_dir} && [ -x ${clean_script} ] && ./${clean_script})

	mk_info "clean dragonboard OK."
}

function mkboot()
{
	mk_info "build boot ..."
	mk_info "build boot OK."
}

function mksata()
{
	if [ "x$PACK_BSPTEST" = "xtrue" ];then
		clsata
		mk_info "build sata ..."

		local build_script="linux/bsptest/script/bsptest.sh"
		local sata_config="${LICHEE_SATA_DIR}/linux/bsptest/script/Config"
		. ${sata_config}
		if [ "x$BTEST_MODULE" = "x" ];then
			BTEST_MODULE=all
		fi

		(cd ${LICHEE_SATA_DIR} && [ -x ${build_script} ] && ./${build_script} -b $BTEST_MODULE)

		[ $? -ne 0 ] && mk_error "build kernel Failed" && return 1
		mk_info "build sata OK."

		(cd ${LICHEE_SATA_DIR} && [ -x ${build_script} ] && ./${build_script} -s $BTEST_MODULE)
	fi
}

function clsata()
{
	mk_info "clear sata ..."

	local build_script="linux/bsptest/script/bsptest.sh"
	(cd ${LICHEE_SATA_DIR} && [ -x ${build_script} ] && ./${build_script} -b clean)

	mk_info "clean sata OK."
}

function mk_tinyandroid()
{
	local ROOTFS=${LICHEE_PLAT_OUT}/rootfs_tinyandroid

	mk_info "Build tinyandroid rootfs ..."
	if [ "$1" = "f" ]; then
		rm -fr ${ROOTFS}
	fi

	if [ ! -f ${ROOTFS} ]; then
		mkdir -p ${ROOTFS}
		tar -jxf ${LICHEE_TOOLS_DIR}/build/rootfs_tar/tinyandroid_${LICHEE_ARCH}.tar.bz2 -C ${ROOTFS}
	fi

	mkdir -p ${ROOTFS}/lib/modules
	cp -rf ${LICHEE_KERN_DIR}/output/lib/modules/* \
		${ROOTFS}/lib/modules/

	if [ "x$PACK_BSPTEST" = "xtrue" ];then
		if [ -d ${ROOTFS}/target ];then
 			rm -rf ${ROOTFS}/target/*
		fi
		if [ -d ${LICHEE_SATA_DIR}/linux/target ]; then
			mk_info "copy SATA rootfs_def"
			cp -a ${LICHEE_SATA_DIR}/linux/target  ${ROOTFS}/
		fi
	fi

	NR_SIZE=`du -sm ${ROOTFS} | awk '{print $1}'`
	NEW_NR_SIZE=$(((($NR_SIZE+32)/16)*16))

	echo "blocks: $NR_SIZE"M" -> $NEW_NR_SIZE"M""
	${LICHEE_TOOLS_DIR}/build/bin/make_ext4fs -l \
		$NEW_NR_SIZE"M" ${LICHEE_PLAT_OUT}/rootfs.ext4 ${ROOTFS}
	fsck.ext4 -y ${LICHEE_PLAT_OUT}/rootfs.ext4 > /dev/null
}

function mk_defroot()
{
	local ROOTFS=${LICHEE_PLAT_OUT}/rootfs_def
	local INODES=""
	local BLOCKS=""

	mk_info "Build default rootfs ..."
	if [ "$1" = "f" ]; then
		rm -fr ${ROOTFS}
	fi

	if [ ! -f ${ROOTFS} ]; then
		mkdir -p ${ROOTFS}
		if [ -n "`echo $LICHEE_KERN_VER | grep "linux-4.[49]"`" ]; then
			fakeroot tar -jxf ${LICHEE_TOOLS_DIR}/build/rootfs_tar/target-${LICHEE_ARCH}-linaro-5.3.tar.bz2 -C ${ROOTFS}
		else
			tar -jxf ${LICHEE_TOOLS_DIR}/build/rootfs_tar/target_${LICHEE_ARCH}.tar.bz2 -C ${ROOTFS}
		fi
	fi

	mkdir -p ${ROOTFS}/lib/modules
	cp -rf ${LICHEE_KERN_DIR}/output/lib/modules/* \
		${ROOTFS}/lib/modules/

	if [ "x$PACK_BSPTEST" = "xtrue" ];then
		if [ -d ${ROOTFS}/target ];then
 			rm -rf ${ROOTFS}/target/*
		fi
		if [ -d ${LICHEE_SATA_DIR}/linux/target ]; then
			mk_info "copy SATA rootfs_def"
			cp -a ${LICHEE_SATA_DIR}/linux/target  ${ROOTFS}/
		fi
	fi

	if [ "x$PACK_STABILITY" = "xtrue" -a -d ${LICHEE_KERN_DIR}/tools/sunxi ];then
		cp -v ${LICHEE_KERN_DIR}/tools/sunxi/* ${ROOTFS}/bin
	fi

	(cd ${ROOTFS}; ln -fs bin/busybox init)

	export PATH=$PATH:${LICHEE_TOOLS_DIR}/build/bin
	fakeroot chown	 -h -R 0:0	${ROOTFS}
	fakeroot mke2img -d ${ROOTFS} -G 4 -R 1 -B 0 -I 0 -o ${LICHEE_PLAT_OUT}/rootfs.ext4
cat  > ${LICHEE_PLAT_OUT}/.rootfs << EOF
chown -h -R 0:0 ${ROOTFS}
${LICHEE_TOOLS_DIR}/build/bin/makedevs -d \
${LICHEE_TOOLS_DIR}/build/rootfs_tar/_device_table.txt ${ROOTFS}
${LICHEE_TOOLS_DIR}/build/bin/mksquashfs \
${ROOTFS} ${LICHEE_PLAT_OUT}/rootfs.squashfs -root-owned -no-progress -comp xz -noappend
EOF
	chmod a+x ${LICHEE_PLAT_OUT}/.rootfs
	fakeroot -- ${LICHEE_PLAT_OUT}/.rootfs
}

function mkrootfs()
{
	mk_info "build rootfs ..."

	if [ ${LICHEE_PLATFORM} = "linux" ] ; then

		if [ "x$PACK_TINY_ANDROID" = "xtrue" ]; then
			mk_tinyandroid $1
		elif [ ${SKIP_BR} -ne 0 ]; then
			mk_defroot $1
		else
			if [ "x$PACK_BSPTEST" = "xtrue" ];then
				if [ -d ${LICHEE_BR_OUT}/target ];then
 					rm -rf ${ROOTFS}/target/*
				fi
				if [ -d ${LICHEE_SATA_DIR}/linux/target ];then
					mk_info "copy SATA rootfs"
					cp -a ${LICHEE_SATA_DIR}/linux/target ${LICHEE_BR_OUT}/target/
				fi
			fi

			# buildroot-201611 just not using this
			if [ -z `echo $LICHEE_BR_DIR | grep "201611"` ]; then
				make O=${LICHEE_BR_OUT} -C ${LICHEE_BR_DIR} \
					BR2_TOOLCHAIN_EXTERNAL_PATH=${LICHEE_TOOLCHAIN_PATH} \
					BR2_TOOLCHAIN_EXTERNAL_PREFIX=${LICHEE_CROSS_COMPILER} \
					BR2_JLEVEL=${LICHEE_JLEVEL} target-post-image
			fi

			[ $? -ne 0 ] && mk_error "build rootfs Failed" && return 1

			cp ${LICHEE_BR_OUT}/images/rootfs.ext4 ${LICHEE_PLAT_OUT}

			if [ -f "${LICHEE_BR_OUT}/images/rootfs.squashfs" ]; then
				cp ${LICHEE_BR_OUT}/images/rootfs.squashfs ${LICHEE_PLAT_OUT}
			fi
		fi
	elif [ ${LICHEE_PLATFORM} = "dragonboard" ] ; then
		# buildroot-201611 not supporting dragonboard, i am just say sorry,
		# pls add it, but i just done nothing else
		if [ -d ${LICHEE_TOP_DIR}/buildroot/target ]; then
			echo "Regenerating dragonboard Rootfs..."
			(
			cd ${LICHEE_TOP_DIR}/buildroot/target/${LICHEE_PLATFORM}; \
				if [ ! -d "./rootfs" ]; then \
					echo "extract dragonboard rootfs.tar.gz"; \
					tar zxf ../common/rootfs/rootfs.tar.gz; \
				fi
			)
			prepare_dragonboard_toolchain
			mkdir -p ${LICHEE_TOP_DIR}/buildroot/target/${LICHEE_PLATFORM}/rootfs/lib/modules
			rm -rf ${LICHEE_TOP_DIR}/buildroot/target/${LICHEE_PLATFORM}/rootfs/lib/modules/*
			cp -rf ${LICHEE_KERN_DIR}/output/lib/modules/* \
				${LICHEE_TOP_DIR}/buildroot/target/${LICHEE_PLATFORM}/rootfs/lib/modules/
			(cd ${LICHEE_TOP_DIR}/buildroot/target/common/scripts; ./build.sh)
					[  $? -ne 0 ] && mk_error "build rootfs Failed" && return 1
			cp ${LICHEE_TOP_DIR}/buildroot/target/${LICHEE_PLATFORM}/rootfs.ext4 ${LICHEE_PLAT_OUT}
		fi
	else
		mk_info "skip make rootfs for ${LICHEE_PLATFORM}"
	fi

	mk_info "build rootfs OK."
}

function mklichee()
{

	mk_info "----------------------------------------"
	mk_info "build lichee ..."
	mk_info "chip: $LICHEE_CHIP"
	mk_info "platform: $LICHEE_PLATFORM"
	mk_info "kernel: $LICHEE_KERN_VER"
	mk_info "board: $LICHEE_BOARD"
	mk_info "output: out/${LICHEE_CHIP}/${LICHEE_PLATFORM}/${LICHEE_BOARD}"
	mk_info "----------------------------------------"

	check_env

	if [ ${SKIP_BR} -eq 0 ]; then
		mkbr
	fi

	mkkernel && mksata && mkrootfs $1

	[ $? -ne 0 ] && return 1

	mk_info "----------------------------------------"
	mk_info "build lichee OK."
	mk_info "----------------------------------------"
}

function mkclean()
{
	clkernel
	cldragonboard

	mk_info "clean product output in ${LICHEE_PLAT_OUT} ..."
	cd ${LICHEE_PLAT_OUT}
	ls | grep -v "buildroot" | xargs rm -rf
	cd - > /dev/null

}

function mkdistclean()
{
	clkernel "distclean"
	if [ ${SKIP_BR} -eq 0 ]; then
		clbr
	fi
	cldragonboard
	mk_info "clean entires output dir ..."
	rm -rf ${LICHEE_OUT_DIR}
}

function mkpack()
{
	mk_info "packing firmware ..."

	check_env

        (cd ${LICHEE_TOOLS_DIR}/pack && \
		./pack -c ${LICHEE_CHIP} -p ${LICHEE_PLATFORM} -b ${LICHEE_BOARD} -k ${LICHEE_KERN_VER} $@)
}

function mkhelp()
{
	printf "
	mkscript - lichee build script

	<version>: 1.0.0
	<author >: james

	<command>:
	mkboot      build boot
	mkbr        build buildroot
	mkkernel    build kernel
	mkrootfs    build rootfs for linux, dragonboard
	mklichee    build total lichee

	mkclean     clean current board output
	mkdistclean clean entires output

	mkpack      pack firmware for lichee

	mkhelp      show this message

	"
}

