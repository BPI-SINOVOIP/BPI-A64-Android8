#/bin/bash
set -e

SCRIPT_NAME=$(basename $0)
SYSTEM_IMG=$1
OUTPUT_SYSTEM_IMG=$2
NEW_VERSION=$3

if (("$#" < 1)) || (("$#" == 2)); then
  echo "Usage: $SCRIPT_NAME <system.img> (<output_system.img> <new_version_id>)"
  exit
fi

if [ "$ANDROID_BUILD_TOP" == "" ]; then
  echo "Need 'lunch'"
  exit
fi

UNSPARSED_SYSTEM_IMG="${SYSTEM_IMG}.raw"
MOUNT_POINT="temp_mnt"
PROPERTY_NAME="ro.build.version.security_patch"

echo "Unsparsing ${SYSTEM_IMG}..."
simg2img "$SYSTEM_IMG" "$UNSPARSED_SYSTEM_IMG"

echo "Mounting..."
mkdir -p "$MOUNT_POINT"
sudo mount -t ext4 -o loop "$UNSPARSED_SYSTEM_IMG" "${MOUNT_POINT}/"

# check the property file placement
PROPERTY_FILE_PLACES=(
  "/system/build.prop"  # layout of A/B support
  "/build.prop"         # layout of non-A/B support
)
PROPERTY_FILE=""

echo "Finding build.prop..."
for place in ${PROPERTY_FILE_PLACES[@]}; do
  if [ -f "${MOUNT_POINT}${place}" ]; then
    PROPERTY_FILE="${MOUNT_POINT}${place}"
    echo "  ${place}"
    break
  fi
done

if [ "$PROPERTY_FILE" != "" ]; then
  if [ "$OUTPUT_SYSTEM_IMG" != "" ]; then
    echo "Replacing..."
  fi
  CURRENT_VERSION=`sudo sed -n -r "s/^${PROPERTY_NAME}=(.*)$/\1/p" ${PROPERTY_FILE}`
  echo "  Current version: ${CURRENT_VERSION}"
  if [ "$OUTPUT_SYSTEM_IMG" != "" ]; then
    echo "  New version: ${NEW_VERSION}"
    sudo sed -i -r "s/^${PROPERTY_NAME}=(.*)$/${PROPERTY_NAME}=${NEW_VERSION}/" ${PROPERTY_FILE}
  fi
else
  echo "ERROR: Cannot find build.prop."
fi

echo "Unmounting..."
sudo umount "${MOUNT_POINT}/"

if [ "$OUTPUT_SYSTEM_IMG" != "" ]; then
  echo "Writing ${OUTPUT_SYSTEM_IMG}..."
  img2simg "$UNSPARSED_SYSTEM_IMG" "$OUTPUT_SYSTEM_IMG"
fi

echo "Done."
