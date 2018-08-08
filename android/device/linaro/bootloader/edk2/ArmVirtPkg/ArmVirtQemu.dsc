#
#  Copyright (c) 2011-2015, ARM Limited. All rights reserved.
#  Copyright (c) 2014, Linaro Limited. All rights reserved.
#  Copyright (c) 2015, Intel Corporation. All rights reserved.
#
#  This program and the accompanying materials
#  are licensed and made available under the terms and conditions of the BSD License
#  which accompanies this distribution.  The full text of the license may be found at
#  http://opensource.org/licenses/bsd-license.php
#
#  THE PROGRAM IS DISTRIBUTED UNDER THE BSD LICENSE ON AN "AS IS" BASIS,
#  WITHOUT WARRANTIES OR REPRESENTATIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED.
#
#

################################################################################
#
# Defines Section - statements that will be processed to create a Makefile.
#
################################################################################
[Defines]
  PLATFORM_NAME                  = ArmVirtQemu
  PLATFORM_GUID                  = 37d7e986-f7e9-45c2-8067-e371421a626c
  PLATFORM_VERSION               = 0.1
  DSC_SPECIFICATION              = 0x00010005
  OUTPUT_DIRECTORY               = Build/ArmVirtQemu-$(ARCH)
  SUPPORTED_ARCHITECTURES        = AARCH64|ARM
  BUILD_TARGETS                  = DEBUG|RELEASE
  SKUID_IDENTIFIER               = DEFAULT
  FLASH_DEFINITION               = ArmVirtPkg/ArmVirtQemu.fdf

  #
  # Defines for default states.  These can be changed on the command line.
  # -D FLAG=VALUE
  #
  DEFINE SECURE_BOOT_ENABLE      = FALSE

!include ArmVirtPkg/ArmVirt.dsc.inc

[LibraryClasses.AARCH64]
  ArmLib|ArmPkg/Library/ArmLib/AArch64/AArch64Lib.inf
  ArmCpuLib|ArmPkg/Drivers/ArmCpuLib/ArmCortexAEMv8Lib/ArmCortexAEMv8Lib.inf

[LibraryClasses.ARM]
  ArmLib|ArmPkg/Library/ArmLib/ArmV7/ArmV7Lib.inf
  ArmCpuLib|ArmPkg/Drivers/ArmCpuLib/ArmCortexA15Lib/ArmCortexA15Lib.inf

[LibraryClasses.common]
  # Virtio Support
  VirtioLib|OvmfPkg/Library/VirtioLib/VirtioLib.inf
  VirtioMmioDeviceLib|OvmfPkg/Library/VirtioMmioDeviceLib/VirtioMmioDeviceLib.inf
  QemuFwCfgLib|ArmVirtPkg/Library/QemuFwCfgLib/QemuFwCfgLib.inf

  ArmPlatformLib|ArmVirtPkg/Library/ArmVirtPlatformLib/ArmVirtPlatformLib.inf
  ArmPlatformSysConfigLib|ArmPlatformPkg/Library/ArmPlatformSysConfigLibNull/ArmPlatformSysConfigLibNull.inf

  TimerLib|ArmPkg/Library/ArmArchTimerLib/ArmArchTimerLib.inf
  NorFlashPlatformLib|ArmVirtPkg/Library/NorFlashQemuLib/NorFlashQemuLib.inf

  CapsuleLib|MdeModulePkg/Library/DxeCapsuleLibNull/DxeCapsuleLibNull.inf
  GenericBdsLib|IntelFrameworkModulePkg/Library/GenericBdsLib/GenericBdsLib.inf
  PlatformBdsLib|ArmVirtPkg/Library/PlatformIntelBdsLib/PlatformIntelBdsLib.inf
  CustomizedDisplayLib|MdeModulePkg/Library/CustomizedDisplayLib/CustomizedDisplayLib.inf
  QemuBootOrderLib|OvmfPkg/Library/QemuBootOrderLib/QemuBootOrderLib.inf

[LibraryClasses.common.UEFI_DRIVER]
  UefiScsiLib|MdePkg/Library/UefiScsiLib/UefiScsiLib.inf

[LibraryClasses.AARCH64.SEC]
  ArmLib|ArmPkg/Library/ArmLib/AArch64/AArch64LibSec.inf

[LibraryClasses.ARM.SEC]
  ArmLib|ArmPkg/Library/ArmLib/ArmV7/ArmV7LibSec.inf

[BuildOptions]
  RVCT:*_*_ARM_PLATFORM_FLAGS == --cpu Cortex-A15 -I$(WORKSPACE)/ArmVirtPkg/Include
  GCC:*_*_ARM_PLATFORM_FLAGS == -mcpu=cortex-a15 -I$(WORKSPACE)/ArmVirtPkg/Include
  *_*_AARCH64_PLATFORM_FLAGS == -I$(WORKSPACE)/ArmVirtPkg/Include


################################################################################
#
# Pcd Section - list of all EDK II PCD Entries defined by this Platform
#
################################################################################

[PcdsFeatureFlag.common]
  gUefiOvmfPkgTokenSpaceGuid.PcdQemuBootOrderPciTranslation|TRUE
  gUefiOvmfPkgTokenSpaceGuid.PcdQemuBootOrderMmioTranslation|TRUE

  ## If TRUE, Graphics Output Protocol will be installed on virtual handle created by ConsplitterDxe.
  #  It could be set FALSE to save size.
  gEfiMdeModulePkgTokenSpaceGuid.PcdConOutGopSupport|TRUE
  gEfiMdeModulePkgTokenSpaceGuid.PcdConOutUgaSupport|FALSE

  # Activate KVM workaround for now.
  gArmVirtTokenSpaceGuid.PcdKludgeMapPciMmioAsCached|TRUE

[PcdsFixedAtBuild.common]
  gArmPlatformTokenSpaceGuid.PcdCoreCount|1
!if $(ARCH) == AARCH64
  gArmTokenSpaceGuid.PcdVFPEnabled|1
!endif

  gArmPlatformTokenSpaceGuid.PcdCPUCoresStackBase|0x4007c000
  gArmPlatformTokenSpaceGuid.PcdCPUCorePrimaryStackSize|0x4000

  # Size of the region used by UEFI in permanent memory (Reserved 64MB)
  gArmPlatformTokenSpaceGuid.PcdSystemMemoryUefiRegionSize|0x04000000

  #
  # ARM Pcds
  #
  gArmTokenSpaceGuid.PcdArmUncachedMemoryMask|0x0000000040000000

  ## Trustzone enable (to make the transition from EL3 to EL2 in ArmPlatformPkg/Sec)
  gArmTokenSpaceGuid.PcdTrustzoneSupport|FALSE

  #
  # ARM PrimeCell
  #

  ## PL011 - Serial Terminal
  gEfiMdePkgTokenSpaceGuid.PcdUartDefaultBaudRate|38400

  ## Default Terminal Type
  ## 0-PCANSI, 1-VT100, 2-VT00+, 3-UTF8, 4-TTYTERM
!if $(TTY_TERMINAL) == TRUE
  gEfiMdePkgTokenSpaceGuid.PcdDefaultTerminalType|4
!else
  gEfiMdePkgTokenSpaceGuid.PcdDefaultTerminalType|1
!endif
  gEfiMdePkgTokenSpaceGuid.PcdPlatformBootTimeOut|3

  #
  # ARM Virtual Architectural Timer -- fetch frequency from QEMU (TCG) or KVM
  #
  gArmTokenSpaceGuid.PcdArmArchTimerFreqInHz|0

  #
  # NV Storage PCDs. Use base of 0x04000000 for NOR1
  #
  gEfiMdeModulePkgTokenSpaceGuid.PcdFlashNvStorageVariableBase|0x04000000
  gEfiMdeModulePkgTokenSpaceGuid.PcdFlashNvStorageVariableSize|0x00040000
  gEfiMdeModulePkgTokenSpaceGuid.PcdFlashNvStorageFtwWorkingBase|0x04040000
  gEfiMdeModulePkgTokenSpaceGuid.PcdFlashNvStorageFtwWorkingSize|0x00040000
  gEfiMdeModulePkgTokenSpaceGuid.PcdFlashNvStorageFtwSpareBase|0x04080000
  gEfiMdeModulePkgTokenSpaceGuid.PcdFlashNvStorageFtwSpareSize|0x00040000

  # System Memory Base -- fixed at 0x4000_0000
  gArmTokenSpaceGuid.PcdSystemMemoryBase|0x40000000

  # initial location of the device tree blob passed by QEMU -- base of DRAM
  gArmVirtTokenSpaceGuid.PcdDeviceTreeInitialBaseAddress|0x40000000

  gEfiMdeModulePkgTokenSpaceGuid.PcdResetOnMemoryTypeInformationChange|FALSE
  gEfiIntelFrameworkModulePkgTokenSpaceGuid.PcdShellFile|{ 0x83, 0xA5, 0x04, 0x7C, 0x3E, 0x9E, 0x1C, 0x4F, 0xAD, 0x65, 0xE0, 0x52, 0x68, 0xD0, 0xB4, 0xD1 }

  #
  # The maximum physical I/O addressability of the processor, set with
  # BuildCpuHob().
  #
  gEmbeddedTokenSpaceGuid.PcdPrePiCpuIoSize|16

[PcdsFixedAtBuild.AARCH64]
  gEfiMdeModulePkgTokenSpaceGuid.PcdSetNxForStack|TRUE

  # KVM limits it IPA space to 40 bits (1 TB), so there is no need to
  # support anything bigger, even if the host hardware does
  gEmbeddedTokenSpaceGuid.PcdPrePiCpuMemorySize|40

[PcdsDynamicDefault.common]
  ## If TRUE, OvmfPkg/AcpiPlatformDxe will not wait for PCI
  #  enumeration to complete before installing ACPI tables.
  gEfiMdeModulePkgTokenSpaceGuid.PcdPciDisableBusEnumeration|TRUE

  # System Memory Size -- 1 MB initially, actual size will be fetched from DT
  gArmTokenSpaceGuid.PcdSystemMemorySize|0x00100000

  gArmTokenSpaceGuid.PcdArmArchTimerSecIntrNum|0x0
  gArmTokenSpaceGuid.PcdArmArchTimerIntrNum|0x0
  gArmTokenSpaceGuid.PcdArmArchTimerVirtIntrNum|0x0
  gArmTokenSpaceGuid.PcdArmArchTimerHypIntrNum|0x0

  #
  # ARM General Interrupt Controller
  #
  gArmTokenSpaceGuid.PcdGicDistributorBase|0x0
  gArmTokenSpaceGuid.PcdGicRedistributorsBase|0x0
  gArmTokenSpaceGuid.PcdGicInterruptInterfaceBase|0x0
  gArmVirtTokenSpaceGuid.PcdArmGicRevision|0x0

  ## PL031 RealTimeClock
  gArmPlatformTokenSpaceGuid.PcdPL031RtcBase|0x0

  gArmPlatformTokenSpaceGuid.PcdPciBusMin|0x0
  gArmPlatformTokenSpaceGuid.PcdPciBusMax|0x0
  gArmPlatformTokenSpaceGuid.PcdPciIoBase|0x0
  gArmPlatformTokenSpaceGuid.PcdPciIoSize|0x0
  gArmPlatformTokenSpaceGuid.PcdPciIoTranslation|0x0
  gArmPlatformTokenSpaceGuid.PcdPciMmio32Base|0x0
  gArmPlatformTokenSpaceGuid.PcdPciMmio32Size|0x0
  gEfiMdePkgTokenSpaceGuid.PcdPciExpressBaseAddress|0x0

  gArmVirtTokenSpaceGuid.PcdArmPsciMethod|0

  gArmVirtTokenSpaceGuid.PcdFwCfgSelectorAddress|0x0
  gArmVirtTokenSpaceGuid.PcdFwCfgDataAddress|0x0
  gArmVirtTokenSpaceGuid.PcdFwCfgDmaAddress|0x0

  #
  # Set video resolution for boot options and for text setup.
  # PlatformDxe can set the former at runtime.
  #
  gEfiMdeModulePkgTokenSpaceGuid.PcdVideoHorizontalResolution|800
  gEfiMdeModulePkgTokenSpaceGuid.PcdVideoVerticalResolution|600
  gEfiMdeModulePkgTokenSpaceGuid.PcdSetupVideoHorizontalResolution|640
  gEfiMdeModulePkgTokenSpaceGuid.PcdSetupVideoVerticalResolution|480

  #
  # SMBIOS entry point version
  #
  gEfiMdeModulePkgTokenSpaceGuid.PcdSmbiosVersion|0x0300
  gEfiMdeModulePkgTokenSpaceGuid.PcdSmbiosDocRev|0x0
  gUefiOvmfPkgTokenSpaceGuid.PcdQemuSmbiosValidated|FALSE

################################################################################
#
# Components Section - list of all EDK II Modules needed by this Platform
#
################################################################################
[Components.common]
  #
  # PEI Phase modules
  #
  ArmPlatformPkg/PrePeiCore/PrePeiCoreUniCore.inf
  MdeModulePkg/Core/Pei/PeiMain.inf
  MdeModulePkg/Universal/PCD/Pei/Pcd.inf
  ArmPlatformPkg/PlatformPei/PlatformPeim.inf
  ArmPlatformPkg/MemoryInitPei/MemoryInitPeim.inf
  ArmPkg/Drivers/CpuPei/CpuPei.inf

  MdeModulePkg/Universal/Variable/Pei/VariablePei.inf

  MdeModulePkg/Core/DxeIplPeim/DxeIpl.inf {
    <LibraryClasses>
      NULL|MdeModulePkg/Library/LzmaCustomDecompressLib/LzmaCustomDecompressLib.inf
  }

  #
  # DXE
  #
  MdeModulePkg/Core/Dxe/DxeMain.inf {
    <LibraryClasses>
      NULL|MdeModulePkg/Library/DxeCrc32GuidedSectionExtractLib/DxeCrc32GuidedSectionExtractLib.inf
  }
  MdeModulePkg/Universal/PCD/Dxe/Pcd.inf

  #
  # Architectural Protocols
  #
  ArmPkg/Drivers/CpuDxe/CpuDxe.inf
  MdeModulePkg/Core/RuntimeDxe/RuntimeDxe.inf
  MdeModulePkg/Universal/Variable/RuntimeDxe/VariableRuntimeDxe.inf {
    <LibraryClasses>
      NULL|MdeModulePkg/Library/VarCheckUefiLib/VarCheckUefiLib.inf
  }
!if $(SECURE_BOOT_ENABLE) == TRUE
  MdeModulePkg/Universal/SecurityStubDxe/SecurityStubDxe.inf {
    <LibraryClasses>
      NULL|SecurityPkg/Library/DxeImageVerificationLib/DxeImageVerificationLib.inf
  }
  SecurityPkg/VariableAuthenticated/SecureBootConfigDxe/SecureBootConfigDxe.inf
!else
  MdeModulePkg/Universal/SecurityStubDxe/SecurityStubDxe.inf
!endif
  MdeModulePkg/Universal/CapsuleRuntimeDxe/CapsuleRuntimeDxe.inf
  MdeModulePkg/Universal/FaultTolerantWriteDxe/FaultTolerantWriteDxe.inf
  MdeModulePkg/Universal/MonotonicCounterRuntimeDxe/MonotonicCounterRuntimeDxe.inf
  EmbeddedPkg/ResetRuntimeDxe/ResetRuntimeDxe.inf
  EmbeddedPkg/RealTimeClockRuntimeDxe/RealTimeClockRuntimeDxe.inf
  EmbeddedPkg/MetronomeDxe/MetronomeDxe.inf

  MdeModulePkg/Universal/Console/ConPlatformDxe/ConPlatformDxe.inf
  MdeModulePkg/Universal/Console/ConSplitterDxe/ConSplitterDxe.inf
  MdeModulePkg/Universal/Console/GraphicsConsoleDxe/GraphicsConsoleDxe.inf
  MdeModulePkg/Universal/Console/TerminalDxe/TerminalDxe.inf
  MdeModulePkg/Universal/SerialDxe/SerialDxe.inf

  MdeModulePkg/Universal/HiiDatabaseDxe/HiiDatabaseDxe.inf

  ArmPkg/Drivers/ArmGic/ArmGicDxe.inf
  ArmPkg/Drivers/TimerDxe/TimerDxe.inf
!if $(SECURE_BOOT_ENABLE) == TRUE
  ArmPlatformPkg/Drivers/NorFlashDxe/NorFlashAuthenticatedDxe.inf
!else
  ArmPlatformPkg/Drivers/NorFlashDxe/NorFlashDxe.inf
!endif
  MdeModulePkg/Universal/WatchdogTimerDxe/WatchdogTimer.inf

  #
  # Platform Driver
  #
  ArmVirtPkg/VirtFdtDxe/VirtFdtDxe.inf
  ArmVirtPkg/HighMemDxe/HighMemDxe.inf
  OvmfPkg/VirtioBlkDxe/VirtioBlk.inf
  OvmfPkg/VirtioScsiDxe/VirtioScsi.inf
  OvmfPkg/VirtioNetDxe/VirtioNet.inf

  #
  # FAT filesystem + GPT/MBR partitioning
  #
  MdeModulePkg/Universal/Disk/DiskIoDxe/DiskIoDxe.inf
  MdeModulePkg/Universal/Disk/PartitionDxe/PartitionDxe.inf
  MdeModulePkg/Universal/Disk/UnicodeCollation/EnglishDxe/EnglishDxe.inf

  #
  # Bds
  #
  MdeModulePkg/Universal/DevicePathDxe/DevicePathDxe.inf
  MdeModulePkg/Universal/DisplayEngineDxe/DisplayEngineDxe.inf
  MdeModulePkg/Universal/SetupBrowserDxe/SetupBrowserDxe.inf
  IntelFrameworkModulePkg/Universal/BdsDxe/BdsDxe.inf

  #
  # SCSI Bus and Disk Driver
  #
  MdeModulePkg/Bus/Scsi/ScsiBusDxe/ScsiBusDxe.inf
  MdeModulePkg/Bus/Scsi/ScsiDiskDxe/ScsiDiskDxe.inf

  #
  # SMBIOS Support
  #
  MdeModulePkg/Universal/SmbiosDxe/SmbiosDxe.inf {
    <LibraryClasses>
      NULL|OvmfPkg/Library/SmbiosVersionLib/DetectSmbiosVersionLib.inf
  }
  OvmfPkg/SmbiosPlatformDxe/SmbiosPlatformDxe.inf

  #
  # ACPI Support
  #
  MdeModulePkg/Universal/Acpi/AcpiTableDxe/AcpiTableDxe.inf
  OvmfPkg/AcpiPlatformDxe/QemuFwCfgAcpiPlatformDxe.inf

  #
  # PCI support
  #
  ArmVirtPkg/PciHostBridgeDxe/PciHostBridgeDxe.inf
  MdeModulePkg/Bus/Pci/PciBusDxe/PciBusDxe.inf
  OvmfPkg/VirtioPciDeviceDxe/VirtioPciDeviceDxe.inf

  #
  # Video support
  #
  OvmfPkg/QemuVideoDxe/QemuVideoDxe.inf {
    <LibraryClasses>
      BltLib|OptionRomPkg/Library/FrameBufferBltLib/FrameBufferBltLib.inf
  }
  OvmfPkg/PlatformDxe/Platform.inf

  #
  # USB Support
  #
  MdeModulePkg/Bus/Pci/UhciDxe/UhciDxe.inf
  MdeModulePkg/Bus/Pci/EhciDxe/EhciDxe.inf
  MdeModulePkg/Bus/Pci/XhciDxe/XhciDxe.inf
  MdeModulePkg/Bus/Usb/UsbBusDxe/UsbBusDxe.inf
  MdeModulePkg/Bus/Usb/UsbKbDxe/UsbKbDxe.inf

[Components.ARM]
  #
  # The ARM/Linux kernel has no built in EFI boot stub (yet), so we still need
  # an intermediate OS loader. Add the LinuxLoader UEFI application so we can
  # invoke it from the shell.
  #
  MdeModulePkg/Universal/FvSimpleFileSystemDxe/FvSimpleFileSystemDxe.inf
  ArmPkg/Application/LinuxLoader/LinuxLoader.inf {
    <LibraryClasses>
      BdsLib|ArmPkg/Library/BdsLib/BdsLib.inf
  }
