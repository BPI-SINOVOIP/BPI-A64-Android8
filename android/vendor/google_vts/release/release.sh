# VTS release script
# Args:
#   $1 = arm_64 or x64_64
#   $2 = android build top
#   $3 = build ID

mkdir $1 -p
pushd $1
unzip ../android-vts.zip
mkdir android-vts/bin
cp ../adb android-vts/bin
chmod 755 android-vts/bin/adb

cp $2/test/vts/script/setup.sh android-vts/bin
cp $2/vendor/google_vts/script/process_report.py android-vts/bin
chmod 755 android-vts/bin/setup.sh
chmod 755 android-vts/bin/process_report.py

rm android-vts/tools/google-tradefed-vts-prebuilt.jar

zip -r android-vts-$3-$1.zip android-vts
mv android-vts-$3-$1.zip ../
popd
