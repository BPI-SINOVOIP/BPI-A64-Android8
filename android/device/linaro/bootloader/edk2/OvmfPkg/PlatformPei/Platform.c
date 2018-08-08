/**@file
  Platform PEI driver

  Copyright (c) 2006 - 2014, Intel Corporation. All rights reserved.<BR>
  Copyright (c) 2011, Andrei Warkentin <andreiw@motorola.com>

  This program and the accompanying materials
  are licensed and made available under the terms and conditions of the BSD License
  which accompanies this distribution.  The full text of the license may be found at
  http://opensource.org/licenses/bsd-license.php

  THE PROGRAM IS DISTRIBUTED UNDER THE BSD LICENSE ON AN "AS IS" BASIS,
  WITHOUT WARRANTIES OR REPRESENTATIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED.

**/

//
// The package level header files this module uses
//
#include <PiPei.h>

//
// The Library classes this module consumes
//
#include <Library/BaseLib.h>
#include <Library/DebugLib.h>
#include <Library/HobLib.h>
#include <Library/IoLib.h>
#include <Library/MemoryAllocationLib.h>
#include <Library/PcdLib.h>
#include <Library/PciLib.h>
#include <Library/PeimEntryPoint.h>
#include <Library/PeiServicesLib.h>
#include <Library/QemuFwCfgLib.h>
#include <Library/ResourcePublicationLib.h>
#include <Guid/MemoryTypeInformation.h>
#include <Ppi/MasterBootMode.h>
#include <IndustryStandard/Pci22.h>
#include <OvmfPlatforms.h>

#include "Platform.h"
#include "Cmos.h"

EFI_MEMORY_TYPE_INFORMATION mDefaultMemoryTypeInformation[] = {
  { EfiACPIMemoryNVS,       0x004 },
  { EfiACPIReclaimMemory,   0x008 },
  { EfiReservedMemoryType,  0x004 },
  { EfiRuntimeServicesData, 0x024 },
  { EfiRuntimeServicesCode, 0x030 },
  { EfiBootServicesCode,    0x180 },
  { EfiBootServicesData,    0xF00 },
  { EfiMaxMemoryType,       0x000 }
};


EFI_PEI_PPI_DESCRIPTOR   mPpiBootMode[] = {
  {
    EFI_PEI_PPI_DESCRIPTOR_PPI | EFI_PEI_PPI_DESCRIPTOR_TERMINATE_LIST,
    &gEfiPeiMasterBootModePpiGuid,
    NULL
  }
};


UINT16 mHostBridgeDevId;

EFI_BOOT_MODE mBootMode = BOOT_WITH_FULL_CONFIGURATION;

BOOLEAN mS3Supported = FALSE;


VOID
AddIoMemoryBaseSizeHob (
  EFI_PHYSICAL_ADDRESS        MemoryBase,
  UINT64                      MemorySize
  )
{
  BuildResourceDescriptorHob (
    EFI_RESOURCE_MEMORY_MAPPED_IO,
      EFI_RESOURCE_ATTRIBUTE_PRESENT     |
      EFI_RESOURCE_ATTRIBUTE_INITIALIZED |
      EFI_RESOURCE_ATTRIBUTE_UNCACHEABLE |
      EFI_RESOURCE_ATTRIBUTE_TESTED,
    MemoryBase,
    MemorySize
    );
}

VOID
AddReservedMemoryBaseSizeHob (
  EFI_PHYSICAL_ADDRESS        MemoryBase,
  UINT64                      MemorySize,
  BOOLEAN                     Cacheable
  )
{
  BuildResourceDescriptorHob (
    EFI_RESOURCE_MEMORY_RESERVED,
      EFI_RESOURCE_ATTRIBUTE_PRESENT     |
      EFI_RESOURCE_ATTRIBUTE_INITIALIZED |
      EFI_RESOURCE_ATTRIBUTE_UNCACHEABLE |
      (Cacheable ?
       EFI_RESOURCE_ATTRIBUTE_WRITE_COMBINEABLE |
       EFI_RESOURCE_ATTRIBUTE_WRITE_THROUGH_CACHEABLE |
       EFI_RESOURCE_ATTRIBUTE_WRITE_BACK_CACHEABLE :
       0
       ) |
      EFI_RESOURCE_ATTRIBUTE_TESTED,
    MemoryBase,
    MemorySize
    );
}

VOID
AddIoMemoryRangeHob (
  EFI_PHYSICAL_ADDRESS        MemoryBase,
  EFI_PHYSICAL_ADDRESS        MemoryLimit
  )
{
  AddIoMemoryBaseSizeHob (MemoryBase, (UINT64)(MemoryLimit - MemoryBase));
}


VOID
AddMemoryBaseSizeHob (
  EFI_PHYSICAL_ADDRESS        MemoryBase,
  UINT64                      MemorySize
  )
{
  BuildResourceDescriptorHob (
    EFI_RESOURCE_SYSTEM_MEMORY,
      EFI_RESOURCE_ATTRIBUTE_PRESENT |
      EFI_RESOURCE_ATTRIBUTE_INITIALIZED |
      EFI_RESOURCE_ATTRIBUTE_UNCACHEABLE |
      EFI_RESOURCE_ATTRIBUTE_WRITE_COMBINEABLE |
      EFI_RESOURCE_ATTRIBUTE_WRITE_THROUGH_CACHEABLE |
      EFI_RESOURCE_ATTRIBUTE_WRITE_BACK_CACHEABLE |
      EFI_RESOURCE_ATTRIBUTE_TESTED,
    MemoryBase,
    MemorySize
    );
}


VOID
AddMemoryRangeHob (
  EFI_PHYSICAL_ADDRESS        MemoryBase,
  EFI_PHYSICAL_ADDRESS        MemoryLimit
  )
{
  AddMemoryBaseSizeHob (MemoryBase, (UINT64)(MemoryLimit - MemoryBase));
}


VOID
AddUntestedMemoryBaseSizeHob (
  EFI_PHYSICAL_ADDRESS        MemoryBase,
  UINT64                      MemorySize
  )
{
  BuildResourceDescriptorHob (
    EFI_RESOURCE_SYSTEM_MEMORY,
      EFI_RESOURCE_ATTRIBUTE_PRESENT |
      EFI_RESOURCE_ATTRIBUTE_INITIALIZED |
      EFI_RESOURCE_ATTRIBUTE_UNCACHEABLE |
      EFI_RESOURCE_ATTRIBUTE_WRITE_COMBINEABLE |
      EFI_RESOURCE_ATTRIBUTE_WRITE_THROUGH_CACHEABLE |
      EFI_RESOURCE_ATTRIBUTE_WRITE_BACK_CACHEABLE,
    MemoryBase,
    MemorySize
    );
}


VOID
AddUntestedMemoryRangeHob (
  EFI_PHYSICAL_ADDRESS        MemoryBase,
  EFI_PHYSICAL_ADDRESS        MemoryLimit
  )
{
  AddUntestedMemoryBaseSizeHob (MemoryBase, (UINT64)(MemoryLimit - MemoryBase));
}

VOID
MemMapInitialization (
  VOID
  )
{
  //
  // Create Memory Type Information HOB
  //
  BuildGuidDataHob (
    &gEfiMemoryTypeInformationGuid,
    mDefaultMemoryTypeInformation,
    sizeof(mDefaultMemoryTypeInformation)
    );

  //
  // Add PCI IO Port space available for PCI resource allocations.
  //
  BuildResourceDescriptorHob (
    EFI_RESOURCE_IO,
    EFI_RESOURCE_ATTRIBUTE_PRESENT     |
    EFI_RESOURCE_ATTRIBUTE_INITIALIZED,
    0xC000,
    0x4000
    );

  //
  // Video memory + Legacy BIOS region
  //
  AddIoMemoryRangeHob (0x0A0000, BASE_1MB);

  if (!mXen) {
    UINT32  TopOfLowRam;
    UINT32  PciBase;

    TopOfLowRam = GetSystemMemorySizeBelow4gb ();
    if (mHostBridgeDevId == INTEL_Q35_MCH_DEVICE_ID) {
      //
      // A 3GB base will always fall into Q35's 32-bit PCI host aperture,
      // regardless of the Q35 MMCONFIG BAR. Correspondingly, QEMU never lets
      // the RAM below 4 GB exceed it.
      //
      PciBase = BASE_2GB + BASE_1GB;
      ASSERT (TopOfLowRam <= PciBase);
    } else {
      PciBase = (TopOfLowRam < BASE_2GB) ? BASE_2GB : TopOfLowRam;
    }

    //
    // address       purpose   size
    // ------------  --------  -------------------------
    // max(top, 2g)  PCI MMIO  0xFC000000 - max(top, 2g)
    // 0xFC000000    gap                           44 MB
    // 0xFEC00000    IO-APIC                        4 KB
    // 0xFEC01000    gap                         1020 KB
    // 0xFED00000    HPET                           1 KB
    // 0xFED00400    gap                          111 KB
    // 0xFED1C000    gap (PIIX4) / RCRB (ICH9)     16 KB
    // 0xFED20000    gap                          896 KB
    // 0xFEE00000    LAPIC                          1 MB
    //
    AddIoMemoryRangeHob (PciBase, 0xFC000000);
    AddIoMemoryBaseSizeHob (0xFEC00000, SIZE_4KB);
    AddIoMemoryBaseSizeHob (0xFED00000, SIZE_1KB);
    if (mHostBridgeDevId == INTEL_Q35_MCH_DEVICE_ID) {
      AddIoMemoryBaseSizeHob (ICH9_ROOT_COMPLEX_BASE, SIZE_16KB);
    }
    AddIoMemoryBaseSizeHob (PcdGet32(PcdCpuLocalApicBaseAddress), SIZE_1MB);
  }
}

EFI_STATUS
GetNamedFwCfgBoolean (
  IN  CHAR8   *FwCfgFileName,
  OUT BOOLEAN *Setting
  )
{
  EFI_STATUS           Status;
  FIRMWARE_CONFIG_ITEM FwCfgItem;
  UINTN                FwCfgSize;
  UINT8                Value[3];

  Status = QemuFwCfgFindFile (FwCfgFileName, &FwCfgItem, &FwCfgSize);
  if (EFI_ERROR (Status)) {
    return Status;
  }
  if (FwCfgSize > sizeof Value) {
    return EFI_BAD_BUFFER_SIZE;
  }
  QemuFwCfgSelectItem (FwCfgItem);
  QemuFwCfgReadBytes (FwCfgSize, Value);

  if ((FwCfgSize == 1) ||
      (FwCfgSize == 2 && Value[1] == '\n') ||
      (FwCfgSize == 3 && Value[1] == '\r' && Value[2] == '\n')) {
    switch (Value[0]) {
      case '0':
      case 'n':
      case 'N':
        *Setting = FALSE;
        return EFI_SUCCESS;

      case '1':
      case 'y':
      case 'Y':
        *Setting = TRUE;
        return EFI_SUCCESS;

      default:
        break;
    }
  }
  return EFI_PROTOCOL_ERROR;
}

#define UPDATE_BOOLEAN_PCD_FROM_FW_CFG(TokenName)                   \
          do {                                                      \
            BOOLEAN Setting;                                        \
                                                                    \
            if (!EFI_ERROR (GetNamedFwCfgBoolean (                  \
                              "opt/ovmf/" #TokenName, &Setting))) { \
              PcdSetBool (TokenName, Setting);                      \
            }                                                       \
          } while (0)

VOID
NoexecDxeInitialization (
  VOID
  )
{
  UPDATE_BOOLEAN_PCD_FROM_FW_CFG (PcdPropertiesTableEnable);
  UPDATE_BOOLEAN_PCD_FROM_FW_CFG (PcdSetNxForStack);
}

VOID
MiscInitialization (
  VOID
  )
{
  UINTN  PmCmd;
  UINTN  Pmba;
  UINTN  AcpiCtlReg;
  UINT8  AcpiEnBit;

  //
  // Disable A20 Mask
  //
  IoOr8 (0x92, BIT1);

  //
  // Build the CPU HOB with guest RAM size dependent address width and 16-bits
  // of IO space. (Side note: unlike other HOBs, the CPU HOB is needed during
  // S3 resume as well, so we build it unconditionally.)
  //
  BuildCpuHob (mPhysMemAddressWidth, 16);

  //
  // Determine platform type and save Host Bridge DID to PCD
  //
  switch (mHostBridgeDevId) {
    case INTEL_82441_DEVICE_ID:
      PmCmd      = POWER_MGMT_REGISTER_PIIX4 (PCI_COMMAND_OFFSET);
      Pmba       = POWER_MGMT_REGISTER_PIIX4 (PIIX4_PMBA);
      AcpiCtlReg = POWER_MGMT_REGISTER_PIIX4 (PIIX4_PMREGMISC);
      AcpiEnBit  = PIIX4_PMREGMISC_PMIOSE;
      break;
    case INTEL_Q35_MCH_DEVICE_ID:
      PmCmd      = POWER_MGMT_REGISTER_Q35 (PCI_COMMAND_OFFSET);
      Pmba       = POWER_MGMT_REGISTER_Q35 (ICH9_PMBASE);
      AcpiCtlReg = POWER_MGMT_REGISTER_Q35 (ICH9_ACPI_CNTL);
      AcpiEnBit  = ICH9_ACPI_CNTL_ACPI_EN;
      break;
    default:
      DEBUG ((EFI_D_ERROR, "%a: Unknown Host Bridge Device ID: 0x%04x\n",
        __FUNCTION__, mHostBridgeDevId));
      ASSERT (FALSE);
      return;
  }
  PcdSet16 (PcdOvmfHostBridgePciDevId, mHostBridgeDevId);

  //
  // If the appropriate IOspace enable bit is set, assume the ACPI PMBA
  // has been configured (e.g., by Xen) and skip the setup here.
  // This matches the logic in AcpiTimerLibConstructor ().
  //
  if ((PciRead8 (AcpiCtlReg) & AcpiEnBit) == 0) {
    //
    // The PEI phase should be exited with fully accessibe ACPI PM IO space:
    // 1. set PMBA
    //
    PciAndThenOr32 (Pmba, (UINT32) ~0xFFC0, PcdGet16 (PcdAcpiPmBaseAddress));

    //
    // 2. set PCICMD/IOSE
    //
    PciOr8 (PmCmd, EFI_PCI_COMMAND_IO_SPACE);

    //
    // 3. set ACPI PM IO enable bit (PMREGMISC:PMIOSE or ACPI_CNTL:ACPI_EN)
    //
    PciOr8 (AcpiCtlReg, AcpiEnBit);
  }

  if (mHostBridgeDevId == INTEL_Q35_MCH_DEVICE_ID) {
    //
    // Set Root Complex Register Block BAR
    //
    PciWrite32 (
      POWER_MGMT_REGISTER_Q35 (ICH9_RCBA),
      ICH9_ROOT_COMPLEX_BASE | ICH9_RCBA_EN
      );
  }
}


VOID
BootModeInitialization (
  VOID
  )
{
  EFI_STATUS    Status;

  if (CmosRead8 (0xF) == 0xFE) {
    mBootMode = BOOT_ON_S3_RESUME;
  }
  CmosWrite8 (0xF, 0x00);

  Status = PeiServicesSetBootMode (mBootMode);
  ASSERT_EFI_ERROR (Status);

  Status = PeiServicesInstallPpi (mPpiBootMode);
  ASSERT_EFI_ERROR (Status);
}


VOID
ReserveEmuVariableNvStore (
  )
{
  EFI_PHYSICAL_ADDRESS VariableStore;

  //
  // Allocate storage for NV variables early on so it will be
  // at a consistent address.  Since VM memory is preserved
  // across reboots, this allows the NV variable storage to survive
  // a VM reboot.
  //
  VariableStore =
    (EFI_PHYSICAL_ADDRESS)(UINTN)
      AllocateAlignedRuntimePages (
        EFI_SIZE_TO_PAGES (2 * PcdGet32 (PcdFlashNvStorageFtwSpareSize)),
        PcdGet32 (PcdFlashNvStorageFtwSpareSize)
        );
  DEBUG ((EFI_D_INFO,
          "Reserved variable store memory: 0x%lX; size: %dkb\n",
          VariableStore,
          (2 * PcdGet32 (PcdFlashNvStorageFtwSpareSize)) / 1024
        ));
  PcdSet64 (PcdEmuVariableNvStoreReserved, VariableStore);
}


VOID
DebugDumpCmos (
  VOID
  )
{
  UINT32 Loop;

  DEBUG ((EFI_D_INFO, "CMOS:\n"));

  for (Loop = 0; Loop < 0x80; Loop++) {
    if ((Loop % 0x10) == 0) {
      DEBUG ((EFI_D_INFO, "%02x:", Loop));
    }
    DEBUG ((EFI_D_INFO, " %02x", CmosRead8 (Loop)));
    if ((Loop % 0x10) == 0xf) {
      DEBUG ((EFI_D_INFO, "\n"));
    }
  }
}


VOID
S3Verification (
  VOID
  )
{
#if defined (MDE_CPU_X64)
  if (FeaturePcdGet (PcdSmmSmramRequire) && mS3Supported) {
    DEBUG ((EFI_D_ERROR,
      "%a: S3Resume2Pei doesn't support X64 PEI + SMM yet.\n", __FUNCTION__));
    DEBUG ((EFI_D_ERROR,
      "%a: Please disable S3 on the QEMU command line (see the README),\n",
      __FUNCTION__));
    DEBUG ((EFI_D_ERROR,
      "%a: or build OVMF with \"OvmfPkgIa32X64.dsc\".\n", __FUNCTION__));
    ASSERT (FALSE);
    CpuDeadLoop ();
  }
#endif
}


/**
  Perform Platform PEI initialization.

  @param  FileHandle      Handle of the file being invoked.
  @param  PeiServices     Describes the list of possible PEI Services.

  @return EFI_SUCCESS     The PEIM initialized successfully.

**/
EFI_STATUS
EFIAPI
InitializePlatform (
  IN       EFI_PEI_FILE_HANDLE  FileHandle,
  IN CONST EFI_PEI_SERVICES     **PeiServices
  )
{
  DEBUG ((EFI_D_ERROR, "Platform PEIM Loaded\n"));

  DebugDumpCmos ();

  XenDetect ();

  if (QemuFwCfgS3Enabled ()) {
    DEBUG ((EFI_D_INFO, "S3 support was detected on QEMU\n"));
    mS3Supported = TRUE;
  }

  S3Verification ();
  BootModeInitialization ();
  AddressWidthInitialization ();

  PublishPeiMemory ();

  InitializeRamRegions ();

  if (mXen) {
    DEBUG ((EFI_D_INFO, "Xen was detected\n"));
    InitializeXen ();
  }

  //
  // Query Host Bridge DID
  //
  mHostBridgeDevId = PciRead16 (OVMF_HOSTBRIDGE_DID);

  if (mBootMode != BOOT_ON_S3_RESUME) {
    ReserveEmuVariableNvStore ();
    PeiFvInitialization ();
    MemMapInitialization ();
    NoexecDxeInitialization ();
  }

  MiscInitialization ();

  return EFI_SUCCESS;
}
