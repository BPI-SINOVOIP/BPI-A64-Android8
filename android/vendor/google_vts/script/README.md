### change_security_patch_ver.sh ###

The script can help you to generate a new GSI with different security patch
level. The security patch level in GSI is placed in `system/build.prop`, ex:

    ro.build.version.security_patch=2017-01-01

The script requires `img2simg` and `simg2img`, you could prepare them by
following commands:

    $ source build/envsetup.sh
    $ lunch aosp_arm64-eng   # or any other workable project
    $ make -j32 img2simg
    $ make -j32 simg2img

You could use following command to check the security patch version in the image:

    $ ./change_security_patch_ver.sh <gsi_system.img>

And use following command to change the security patch version in the image:

    $ ./change_security_patch_ver.sh <gsi_system.img> <output_system.img> <new_version>

You need to input your password for mount image, example:

    $./change_security_patch_ver.sh system.img out.img 2017-01-01
    Unsparsing system.img...
    Mounting...
    [sudo] password for ********:
    Replacing...
      Current version: 2017-09-05
      New version: 2017-01-01
    Unmounting...
    Writing out.img...
    Done.

And check the result:

    $ ./change_security_patch_ver.sh out.img
    Unsparsing out.img...
    Mounting...
      Current version: 2017-01-01
    Unmounting...
    Done.
