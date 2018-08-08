import os
import tempfile

import common
import sparse_img

OPTIONS = common.OPTIONS

def AssertBootVersion(info):
  info.script.AppendExtra(
      'assert_boot_version(%s);'%(info.info_dict.get("boot_version", "0")))

def GetFex(name, tmpdir):
  path = os.path.join(tmpdir, "IMAGES", name)
  if os.path.exists(path):
    return common.File.FromLocalFile(name, path)
  else:
    print " %s is not exist " %(path)
    return None

def WriteRawFex(info, mount_point, fn):
  info.script.AppendExtra(
      'package_extract_file("%s","%s");'%(fn, mount_point))

def UpdateBootloader(info):
  boot_resource_fex = GetFex("boot-resource.fex", OPTIONS.target_tmp)
  if boot_resource_fex:
    info.script.Print("Updating bootloader...")
    common.ZipWriteStr(info.output_zip, "boot-resource.fex", boot_resource_fex.data)
    WriteRawFex(info, "/dev/block/by-name/bootloader", "boot-resource.fex")

def UpdateEnv(info):
  env_fex = GetFex("env.fex", OPTIONS.target_tmp)
  if env_fex:
    info.script.Print("Updating env...")
    common.ZipWriteStr(info.output_zip, "env.fex", env_fex.data)
    WriteRawFex(info, "/dev/block/by-name/env", "env.fex")

def UpdateBoot(info):
  boot0_nand = GetFex("boot0_nand.fex", OPTIONS.target_tmp)
  if boot0_nand:
    common.ZipWriteStr(info.output_zip, "boot0_nand.fex", boot0_nand.data)
  boot0_sdcard = GetFex("boot0_sdcard.fex", OPTIONS.target_tmp)
  if boot0_sdcard:
    common.ZipWriteStr(info.output_zip, "boot0_sdcard.fex", boot0_sdcard.data)
  uboot = GetFex("u-boot.fex", OPTIONS.target_tmp)
  if uboot:
    common.ZipWriteStr(info.output_zip, "u-boot.fex", uboot.data)
  toc0 = GetFex("toc0.fex", OPTIONS.target_tmp)
  if toc0:
    common.ZipWriteStr(info.output_zip, "toc0.fex", toc0.data)
  toc1 = GetFex("toc1.fex", OPTIONS.target_tmp)
  if toc1:
    common.ZipWriteStr(info.output_zip, "toc1.fex", toc1.data)
  info.script.Print("Updating boot...")
  info.script.AppendExtra('burnboot();')

def updateVerityBlock(info):
  verity_block = GetFex("verity_block.fex", OPTIONS.target_tmp)
  if verity_block:
    info.script.Print("Updating verity_block...")
    common.ZipWriteStr(info.output_zip, "verity_block.fex", verity_block.data)
    WriteRawFex(info, "/dev/block/by-name/verity_block", "verity_block.fex")

def ResizeUdisk(info):
  info.script.AppendExtra('resize2fs("%s");'%("/dev/block/by-name/UDISK"))

def FullOTA_Assertions(info):
  AssertBootVersion(info)
  ResizeUdisk(info)
  UpdateBoot(info)

def FullOTA_InstallEnd(info):
  print("pack custom to OTA package...")
  UpdateBootloader(info)
  UpdateEnv(info)
  updateVerityBlock(info)

def IncrementalOTA_Assertions(info):
  AssertBootVersion(info)
  ResizeUdisk(info)
  UpdateBoot(info)

def IncrementalOTA_InstallEnd(info):
  print("pack custom to OTA package...")
  UpdateBootloader(info)
  UpdateEnv(info)
  updateVerityBlock(info)
