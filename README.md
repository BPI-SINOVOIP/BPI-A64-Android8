# BPI-A64-Android 8.1

----------

**Prepare**

Please download oversize files from this [link](https://pan.baidu.com/s/1PCvnVxngNy_D73SI4nZObQ) and merge them to the source code. 


----------

**Build**

Build U-boot

    $ cd brandy 
    $ ./build.sh -p sun50iw1p1

Build Lichee 

    $ cd lichee
    $ ./build.sh config

     Welcome to mkscript setup progress
     All available platform:
        0. android
        1. dragonboard
        2. linux
        3. camdroid
     Choice [dragonboard]: 0
     All available chip:
        0. sun3iw1p1
        1. sun50iw1p1
        2. sun50iw2p1
        3. sun50iw3p1
     ...
     Choice [sun50iw1p1]: 1
     All available kern_ver:
        0. linux-4.9
     Choice [linux-4.9]: 0
     All available board:
        0. dp18
        1. fpga
        2. m64
        3. m64_hdmi
        4. m64_old
     ...
     Choice [m64_hdmi]: 2
     
     $ ./build.sh

Build Android

    $ cd ../android
    $ source build/envsetup.sh
    $ lunch
    $ extract-bsp
    $ make -j8
    $ pack

----------
**Flash**

The target image is packed at lichee/tools/pack/, flash it to your device by PhoenixCard or LiveSuit.
