/*
 * Copyright (c) 2013, Al Stone <al.stone@linaro.org>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 * notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the 
 * documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * 
 * NB: This License is also known as the "BSD 2-Clause License".
 * 
 *
 * [APIC] Multiple APIC Description Table (MADT)
 * Format: [ByteLength]  FieldName : HexFieldValue
 *
 */

[0004]                          Signature : "APIC"
[0004]                       Table Length : 00000000
[0001]                           Revision : 03
[0001]                           Checksum : 00
[0006]                             Oem ID : "LINARO"
[0008]                       Oem Table ID : "RTSMVEV8"
[0004]                       Oem Revision : 00000001
[0004]                    Asl Compiler ID : "INTL"
[0004]              Asl Compiler Revision : 20110623

[0004]                 Local Apic Address : 2C000000
[0004]              Flags (decoded below) : 00000000
                      PC-AT Compatibility : 0

[0001]                      Subtable Type : 0B [Generic Interrupt Controller]
[0001]                             Length : 50
[0002]                           Reserved : 0000
[0004]               CPU Interface Number : 00000000
[0004]                      Processor UID : 00000000
[0004]              Flags (decoded below) : 00000001
                        Processor Enabled : 1
       Performance Interrupt Trigger Mode : 0
       Virtual GIC Interrupt Trigger Mode : 0
[0004]           Parking Protocol Version : 00000000
[0004]              Performance Interrupt : 00000000
[0008]                     Parked Address : 0000000000000000
[0008]                       Base Address : 000000002C000000  /* armv8 FVP Base GIC address */
[0008]           Virtual GIC Base Address : 0
[0008]        Hypervisor GIC Base Address : 0
[0004]              Virtual GIC Interrupt : 0
[0008]         Redistributor Base Address : 0
[0008]                          ARM MPIDR : 0
[0001]                   Efficiency Class : 00
[0003]                           Reserved : 000000

[0001]                      Subtable Type : 0B [Generic Interrupt Controller]
[0001]                             Length : 50
[0002]                           Reserved : 0000
[0004]               CPU Interface Number : 00000001
[0004]                      Processor UID : 00000001
[0004]              Flags (decoded below) : 00000001
                        Processor Enabled : 1
       Performance Interrupt Trigger Mode : 0
       Virtual GIC Interrupt Trigger Mode : 0
[0004]           Parking Protocol Version : 00000000
[0004]              Performance Interrupt : 00000000
[0008]                     Parked Address : 0000000000000000
[0008]                       Base Address : 000000002C000000
[0008]           Virtual GIC Base Address : 0
[0008]        Hypervisor GIC Base Address : 0
[0004]              Virtual GIC Interrupt : 0
[0008]         Redistributor Base Address : 0
[0008]                          ARM MPIDR : 0000000000000001
[0001]                   Efficiency Class : 00
[0003]                           Reserved : 000000

[0001]                      Subtable Type : 0B [Generic Interrupt Controller]
[0001]                             Length : 50
[0002]                           Reserved : 0000
[0004]               CPU Interface Number : 00000002
[0004]                      Processor UID : 00000002
[0004]              Flags (decoded below) : 00000001
                        Processor Enabled : 1
       Performance Interrupt Trigger Mode : 0
       Virtual GIC Interrupt Trigger Mode : 0
[0004]           Parking Protocol Version : 00000000
[0004]              Performance Interrupt : 00000000
[0008]                     Parked Address : 0000000000000000
[0008]                       Base Address : 000000002C000000
[0008]           Virtual GIC Base Address : 0
[0008]        Hypervisor GIC Base Address : 0
[0004]              Virtual GIC Interrupt : 0
[0008]         Redistributor Base Address : 0
[0008]                          ARM MPIDR : 0000000000000002
[0001]                   Efficiency Class : 00
[0003]                           Reserved : 000000

[0001]                      Subtable Type : 0B [Generic Interrupt Controller]
[0001]                             Length : 50
[0002]                           Reserved : 0000
[0004]               CPU Interface Number : 00000003
[0004]                      Processor UID : 00000003
[0004]              Flags (decoded below) : 00000001
                        Processor Enabled : 1
       Performance Interrupt Trigger Mode : 0
       Virtual GIC Interrupt Trigger Mode : 0
[0004]           Parking Protocol Version : 00000000
[0004]              Performance Interrupt : 00000000
[0008]                     Parked Address : 0000000000000000
[0008]                       Base Address : 000000002C000000
[0008]           Virtual GIC Base Address : 0
[0008]        Hypervisor GIC Base Address : 0
[0004]              Virtual GIC Interrupt : 0
[0008]         Redistributor Base Address : 0
[0008]                          ARM MPIDR : 0000000000000003
[0001]                   Efficiency Class : 00
[0003]                           Reserved : 000000

[0001]                      Subtable Type : 0B [Generic Interrupt Controller]
[0001]                             Length : 50
[0002]                           Reserved : 0000
[0004]               CPU Interface Number : 00000004
[0004]                      Processor UID : 00000004
[0004]              Flags (decoded below) : 00000001
                        Processor Enabled : 1
       Performance Interrupt Trigger Mode : 0
       Virtual GIC Interrupt Trigger Mode : 0
[0004]           Parking Protocol Version : 00000000
[0004]              Performance Interrupt : 00000000
[0008]                     Parked Address : 0000000000000000
[0008]                       Base Address : 000000002C000000
[0008]           Virtual GIC Base Address : 0
[0008]        Hypervisor GIC Base Address : 0
[0004]              Virtual GIC Interrupt : 0
[0008]         Redistributor Base Address : 0
[0008]                          ARM MPIDR : 0000000000000100
[0001]                   Efficiency Class : 00
[0003]                           Reserved : 000000

[0001]                      Subtable Type : 0B [Generic Interrupt Controller]
[0001]                             Length : 50
[0002]                           Reserved : 0000
[0004]               CPU Interface Number : 00000005
[0004]                      Processor UID : 00000005
[0004]              Flags (decoded below) : 00000001
                        Processor Enabled : 1
       Performance Interrupt Trigger Mode : 0
       Virtual GIC Interrupt Trigger Mode : 0
[0004]           Parking Protocol Version : 00000000
[0004]              Performance Interrupt : 00000000
[0008]                     Parked Address : 0000000000000000
[0008]                       Base Address : 000000002C000000
[0008]           Virtual GIC Base Address : 0
[0008]        Hypervisor GIC Base Address : 0
[0004]              Virtual GIC Interrupt : 0
[0008]         Redistributor Base Address : 0
[0008]                          ARM MPIDR : 0000000000000101
[0001]                   Efficiency Class : 00
[0003]                           Reserved : 000000

[0001]                      Subtable Type : 0B [Generic Interrupt Controller]
[0001]                             Length : 50
[0002]                           Reserved : 0000
[0004]               CPU Interface Number : 00000006
[0004]                      Processor UID : 00000006
[0004]              Flags (decoded below) : 00000001
                        Processor Enabled : 1
       Performance Interrupt Trigger Mode : 0
       Virtual GIC Interrupt Trigger Mode : 0
[0004]           Parking Protocol Version : 00000000
[0004]              Performance Interrupt : 00000000
[0008]                     Parked Address : 0000000000000000
[0008]                       Base Address : 000000002C000000
[0008]           Virtual GIC Base Address : 0
[0008]        Hypervisor GIC Base Address : 0
[0004]              Virtual GIC Interrupt : 0
[0008]         Redistributor Base Address : 0
[0008]                          ARM MPIDR : 0000000000000102
[0001]                   Efficiency Class : 00
[0003]                           Reserved : 000000

[0001]                      Subtable Type : 0B [Generic Interrupt Controller]
[0001]                             Length : 50
[0002]                           Reserved : 0000
[0004]               CPU Interface Number : 00000007
[0004]                      Processor UID : 00000007
[0004]              Flags (decoded below) : 00000001
                        Processor Enabled : 1
       Performance Interrupt Trigger Mode : 0
       Virtual GIC Interrupt Trigger Mode : 0
[0004]           Parking Protocol Version : 00000000
[0004]              Performance Interrupt : 00000000
[0008]                     Parked Address : 0000000000000000
[0008]                       Base Address : 000000002C000000
[0008]           Virtual GIC Base Address : 0
[0008]        Hypervisor GIC Base Address : 0
[0004]              Virtual GIC Interrupt : 0
[0008]         Redistributor Base Address : 0
[0008]                          ARM MPIDR : 0000000000000103
[0001]                   Efficiency Class : 00
[0003]                           Reserved : 000000

[0001]                      Subtable Type : 0C [Generic Interrupt Distributor]
[0001]                             Length : 18
[0002]                           Reserved : 0000
[0004]              Local GIC Hardware ID : 00000000
[0008]                       Base Address : 000000002F000000 /* armv8 FVP Base GIC distributor base addr */
[0004]                     Interrupt Base : 00000000
[0001]                            Version : 02
[0003]                           Reserved : 000000
