#!/usr/bin/env python3.4
#
#   Copyright 2016 - Google
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
"""
    Test Script for Telephony Pre Check In Sanity
"""

import collections
import time
from acts.test_decorators import test_tracker_info
from acts.controllers.sl4a_types import Sl4aNetworkInfo
from acts.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts.test_utils.tel.tel_data_utils import wifi_tethering_setup_teardown
from acts.test_utils.tel.tel_defines import AOSP_PREFIX
from acts.test_utils.tel.tel_defines import CAPABILITY_VOLTE
from acts.test_utils.tel.tel_defines import CAPABILITY_VT
from acts.test_utils.tel.tel_defines import CAPABILITY_WFC
from acts.test_utils.tel.tel_defines import CAPABILITY_OMADM
from acts.test_utils.tel.tel_defines import DATA_STATE_CONNECTED
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_PROVISIONING
from acts.test_utils.tel.tel_defines import MAX_WAIT_TIME_TETHERING_ENTITLEMENT_CHECK
from acts.test_utils.tel.tel_defines import TETHERING_MODE_WIFI
from acts.test_utils.tel.tel_defines import WAIT_TIME_AFTER_REBOOT
from acts.test_utils.tel.tel_defines import WAIT_TIME_AFTER_CRASH
from acts.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts.test_utils.tel.tel_defines import WAIT_TIME_IN_CALL
from acts.test_utils.tel.tel_defines import WAIT_TIME_IN_CALL_FOR_IMS
from acts.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts.test_utils.tel.tel_defines import WFC_MODE_DISABLED
from acts.test_utils.tel.tel_defines import WFC_MODE_WIFI_ONLY
from acts.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts.test_utils.tel.tel_defines import VT_STATE_BIDIRECTIONAL
from acts.test_utils.tel.tel_subscription_utils import \
    get_incoming_voice_sub_id
from acts.test_utils.tel.tel_subscription_utils import \
    get_outgoing_voice_sub_id
from acts.test_utils.tel.tel_lookup_tables import device_capabilities
from acts.test_utils.tel.tel_lookup_tables import operator_capabilities
from acts.test_utils.tel.tel_test_utils import call_setup_teardown
from acts.test_utils.tel.tel_test_utils import ensure_wifi_connected
from acts.test_utils.tel.tel_test_utils import get_model_name
from acts.test_utils.tel.tel_test_utils import get_operator_name
from acts.test_utils.tel.tel_test_utils import multithread_func
from acts.test_utils.tel.tel_test_utils import sms_send_receive_verify
from acts.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts.test_utils.tel.tel_test_utils import wait_for_cell_data_connection
from acts.test_utils.tel.tel_test_utils import verify_http_connection
from acts.test_utils.tel.tel_test_utils import trigger_modem_crash
from acts.test_utils.tel.tel_test_utils import initiate_call
from acts.test_utils.tel.tel_test_utils import wait_and_answer_call
from acts.test_utils.tel.tel_voice_utils import is_phone_in_call_3g
from acts.test_utils.tel.tel_voice_utils import is_phone_in_call_csfb
from acts.test_utils.tel.tel_voice_utils import is_phone_in_call_iwlan
from acts.test_utils.tel.tel_voice_utils import is_phone_in_call_volte
from acts.test_utils.tel.tel_voice_utils import phone_setup_voice_3g
from acts.test_utils.tel.tel_voice_utils import phone_setup_csfb
from acts.test_utils.tel.tel_voice_utils import phone_setup_iwlan
from acts.test_utils.tel.tel_voice_utils import \
    phone_setup_iwlan_cellular_preferred
from acts.test_utils.tel.tel_voice_utils import phone_setup_voice_general
from acts.test_utils.tel.tel_voice_utils import phone_setup_volte
from acts.test_utils.tel.tel_voice_utils import phone_idle_3g
from acts.test_utils.tel.tel_voice_utils import phone_idle_csfb
from acts.test_utils.tel.tel_voice_utils import phone_idle_iwlan
from acts.test_utils.tel.tel_voice_utils import phone_idle_volte
from acts.test_utils.tel.tel_video_utils import video_call_setup_teardown
from acts.test_utils.tel.tel_video_utils import phone_setup_video
from acts.test_utils.tel.tel_video_utils import \
    is_phone_in_call_video_bidirectional

from acts.utils import rand_ascii_str


class TelLiveRebootStressTest(TelephonyBaseTest):
    def __init__(self, controllers):
        TelephonyBaseTest.__init__(self, controllers)

        self.stress_test_number = self.get_stress_test_number()
        self.wifi_network_ssid = self.user_params["wifi_network_ssid"]

        try:
            self.wifi_network_pass = self.user_params["wifi_network_pass"]
        except KeyError:
            self.wifi_network_pass = None

        self.dut = self.android_devices[0]
        self.ad_reference = self.android_devices[1] if len(
            self.android_devices) > 1 else None
        self.dut_model = get_model_name(self.dut)
        self.dut_operator = get_operator_name(self.log, self.dut)

    def _check_provisioning(self):
        if (CAPABILITY_OMADM in device_capabilities[self.dut_model] and
                CAPABILITY_OMADM in operator_capabilities[self.dut_operator]):
            self.log.info("Check Provisioning bit")
            if not self.dut.droid.imsIsVolteProvisionedOnDevice():
                self.log.error("{}: VoLTE Not Provisioned on the Platform".
                               format(self.dut.serial))
                return False
        return True

    def _check_provision(self):
        elapsed_time = 0
        while (elapsed_time < MAX_WAIT_TIME_PROVISIONING):
            if self._check_provisioning():
                return True
            else:
                time.sleep(CHECK_INTERVAL)
                elapsed_time += CHECK_INTERVAL
        self.log.error("Provisioning fail.")
        return False

    def _clear_provisioning(self):
        if (CAPABILITY_OMADM in device_capabilities[self.dut_model] and
                CAPABILITY_OMADM in operator_capabilities[self.dut_operator]):
            self.log.info("Clear Provisioning bit")
            self.dut.droid.imsSetVolteProvisioning(False)
        return True

    def _check_call_setup_teardown(self):
        if not call_setup_teardown(self.log, self.dut, self.ad_reference):
            self.log.error("Phone Call Failed.")
            return False
        return True

    def _get_list_average(self, input_list):
        total_sum = float(sum(input_list))
        total_count = float(len(input_list))
        if input_list == []:
            return False
        return float(total_sum / total_count)

    def _check_lte_data(self):
        self.log.info("Check LTE data.")
        if not phone_setup_csfb(self.log, self.dut):
            self.log.error("Failed to setup LTE data.")
            return False
        if not verify_http_connection(self.log, self.dut):
            self.log.error("Data not available on cell.")
            return False
        return True

    def _check_volte(self):
        if (CAPABILITY_VOLTE in operator_capabilities[self.dut_operator]):
            self.log.info("Check VoLTE")
            if not phone_setup_volte(self.log, self.dut):
                self.log.error("Failed to setup VoLTE.")
                return False
            time.sleep(5)
            if not call_setup_teardown(self.log, self.dut, self.ad_reference,
                                       self.dut, is_phone_in_call_volte):
                self.log.error("VoLTE Call Failed.")
                return False
            if not sms_send_receive_verify(self.log, self.dut,
                                           self.ad_reference,
                                           [rand_ascii_str(50)]):
                self.log.error("SMS failed")
                return False
        return True

    def _check_vt(self):
        if (CAPABILITY_VT in operator_capabilities[self.dut_operator]):
            self.log.info("Check VT")
            if not phone_setup_video(self.log, self.dut):
                self.log.error("Failed to setup VT.")
                return False
            time.sleep(5)
            if not video_call_setup_teardown(
                    self.log,
                    self.dut,
                    self.ad_reference,
                    self.dut,
                    video_state=VT_STATE_BIDIRECTIONAL,
                    verify_caller_func=is_phone_in_call_video_bidirectional,
                    verify_callee_func=is_phone_in_call_video_bidirectional):
                self.log.error("VT Call Failed.")
                return False
        return True

    def _check_wfc(self):
        if (CAPABILITY_WFC in operator_capabilities[self.dut_operator]):
            self.log.info("Check WFC")
            if not phone_setup_iwlan(
                    self.log, self.dut, True, WFC_MODE_WIFI_PREFERRED,
                    self.wifi_network_ssid, self.wifi_network_pass):
                self.log.error("Failed to setup WFC.")
                return False
            if not call_setup_teardown(self.log, self.dut, self.ad_reference,
                                       self.dut, is_phone_in_call_iwlan):
                self.log.error("WFC Call Failed.")
                return False
            if not sms_send_receive_verify(self.log, self.dut,
                                           self.ad_reference,
                                           [rand_ascii_str(50)]):
                self.log.error("SMS failed")
                return False
        return True

    def _check_3g(self):
        self.log.info("Check 3G data and CS call")
        if not phone_setup_voice_3g(self.log, self.dut):
            self.log.error("Failed to setup 3G")
            return False
        if not verify_http_connection(self.log, self.dut):
            self.log.error("Data not available on cell.")
            return False
        if not call_setup_teardown(self.log, self.dut, self.ad_reference,
                                   self.dut, is_phone_in_call_3g):
            self.log.error("WFC Call Failed.")
            return False
        if not sms_send_receive_verify(self.log, self.dut, self.ad_reference,
                                       [rand_ascii_str(50)]):
            self.log.error("SMS failed")
            return False
        return True

    def _check_tethering(self):
        self.log.info("Check tethering")
        if not self.dut.droid.carrierConfigIsTetheringModeAllowed(
                TETHERING_MODE_WIFI,
                MAX_WAIT_TIME_TETHERING_ENTITLEMENT_CHECK):
            self.log.error("Tethering Entitlement check failed.")
            return False
        if not wifi_tethering_setup_teardown(
                self.log,
                self.dut, [self.ad_reference],
                check_interval=5,
                check_iteration=1):
            self.log.error("Tethering Failed.")
            return False
        return True

    def _check_data_roaming_status(self):
        if not self.dut.droid.telephonyIsDataEnabled():
            self.log.info("Enabling Cellular Data")
            telephonyToggleDataConnection(True)
        else:
            self.log.info("Cell Data is Enabled")
        self.log.info("Waiting for cellular data to be connected")
        if not wait_for_cell_data_connection(self.log, self.dut, state=True):
            self.log.error("Failed to enable cell data")
            return False
        self.log.info("Cellular data connected, checking NetworkInfos")
        roaming_state = self.dut.droid.telephonyCheckNetworkRoaming()
        for network_info in self.dut.droid.connectivityNetworkGetAllInfo():
            sl4a_network_info = Sl4aNetworkInfo.from_dict(network_info)
            if sl4a_network_info.isRoaming:
                self.log.warning("We don't expect to be roaming")
            if sl4a_network_info.isRoaming != roaming_state:
                self.log.error(
                    "Mismatched Roaming Status Information Telephony: {}, NetworkInfo {}".
                    format(roaming_state, sl4a_network_info.isRoaming))
                self.log.error(network_info)
                return False
        return True

    def _telephony_monitor_test(self):
        """
        Steps -
        1. Reboot the phone
        2. Start Telephony Monitor using adb/developer options
        3. Verify if it is running
        4. Phone Call from A to B
        5. Answer on B
        6. Trigger ModemSSR on B
        7. There will be a call drop with Media Timeout/Server Unreachable
        8. Parse logcat to confirm that

        Expected Results:
            UI Notification is received by User

        Returns:
            True is pass, False if fail.
        """
        # Reboot
        ads = self.android_devices
        ads[0].adb.shell(
            "am start -n com.android.settings/.DevelopmentSettings",
            ignore_status=True)
        ads[0].log.info("reboot!")
        ads[0].reboot()
        ads[0].log.info("wait %d secs for radio up." % WAIT_TIME_AFTER_REBOOT)
        time.sleep(WAIT_TIME_AFTER_REBOOT)

        # Ensure apk is running
        if not ads[0].is_apk_running("com.google.telephonymonitor"):
            ads[0].log.info("TelephonyMonitor is not running, start it now")
            ads[0].adb.shell(
                'am broadcast -a '
                'com.google.gservices.intent.action.GSERVICES_OVERRIDE -e '
                '"ce.telephony_monitor_enable" "true"')

        # Setup Phone Call
        caller_number = ads[0].cfg['subscription'][get_outgoing_voice_sub_id(
            ads[0])]['phone_num']
        callee_number = ads[1].cfg['subscription'][get_incoming_voice_sub_id(
            ads[1])]['phone_num']
        tasks = [(phone_setup_voice_general, (self.log, ads[0])),
                 (phone_setup_voice_general, (self.log, ads[1]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        if not initiate_call(ads[0].log, ads[0], callee_number):
            ads[0].log.error("Phone was unable to initate a call")
            return False
        if not wait_and_answer_call(self.log, ads[1], caller_number):
            ads[0].log.error("wait_and_answer_call failed")
            return False

        # Modem SSR
        time.sleep(5)
        ads[1].log.info("Triggering ModemSSR")
        ads[1].adb.shell(
            "echo restart > /sys/kernel/debug/msm_subsys/modem",
            ignore_status=True)
        time.sleep(60)

        # Parse logcat for UI notification
        if ads[0].search_logcat("Bugreport notification title Call Drop:"):
            ads[0].log.info("User got the Call Drop Notification")
        else:
            ads[0].log.error("User didn't get Call Drop Notification in 1 min")
            return False
        return True

    def _reboot_stress_test(self, **kwargs):
        """Reboot Reliability Test

        Arguments:
            check_provision: whether to check provisioning after reboot.
            check_call_setup_teardown: whether to check setup and teardown a call.
            check_lte_data: whether to check the LTE data.
            check_volte: whether to check Voice over LTE.
            check_wfc: whether to check Wifi Calling.
            check_3g: whether to check 3G.
            check_tethering: whether to check Tethering.
            check_data_roaming: whether to check Data Roaming.
            clear_provision: whether to clear provisioning before reboot.

        Expected Results:
            No crash happens in stress test.

        Returns:
            True is pass, False if fail.
        """
        CHECK_INTERVAL = 10

        toggle_airplane_mode(self.log, self.dut, False)
        phone_setup_voice_general(self.log, self.ad_reference)
        fail_count = collections.defaultdict(int)
        test_result = True
        test_method_mapping = {
            "check_provision": self._check_provision,
            "check_call_setup_teardown": self._check_call_setup_teardown,
            "check_lte_data": self._check_lte_data,
            "check_volte": self._check_volte,
            "check_wfc": self._check_wfc,
            "check_3g": self._check_3g,
            "check_tethering": self._check_tethering,
            "check_data_roaming": self._check_data_roaming_status,
            "clear_provision": self._clear_provisioning
        }
        for kwarg in kwargs:
            if kwarg not in test_method_mapping:
                self.log.error("method %s is not supported" % method)

        required_methods = []
        for method in test_method_mapping.keys():
            if method in kwargs: required_methods.append(method)

        for i in range(1, self.stress_test_number + 1):
            self.log.info("Reboot Stress Test {} Iteration: <{}> / <{}>".
                          format(self.test_name, i, self.stress_test_number))

            self.log.info("{} reboot!".format(self.dut.serial))
            self.dut.reboot()
            self.log.info("{} wait {}s for radio up.".format(
                self.dut.serial, WAIT_TIME_AFTER_REBOOT))
            time.sleep(WAIT_TIME_AFTER_REBOOT)
            iteration_result = "pass"
            for check in required_methods:
                if not test_method_mapping[check]():
                    fail_count[check] += 1
                    iteration_result = "fail"
            self.log.info("Reboot Stress Test {} Iteration: <{}> / <{}> {}".
                          format(self.test_name, i, self.stress_test_number,
                                 iteration_result))

            # TODO: Check if crash happens.

        for failure, count in fail_count.items():
            if count:
                self.log.error("{} {} failures in {} iterations".format(
                    count, failure, self.stress_test_number))
                test_result = False
        return test_result

    def _crash_recovery_test(self, **kwargs):
        """Crash Recovery Test

        Arguments:
            check_lte_data: whether to check the LTE data.
            check_volte: whether to check Voice over LTE.
            check_vt: whether to check VT
            check_wfc: whether to check Wifi Calling.

        Expected Results:
            All Features should work as intended post crash recovery

        Returns:
            True is pass, False if fail.
        """
        CHECK_INTERVAL = 10

        toggle_airplane_mode(self.log, self.dut, False)
        phone_setup_voice_general(self.log, self.ad_reference)
        fail_count = collections.defaultdict(int)
        test_result = True
        test_method_mapping = {
            "check_provision": self._check_provision,
            "check_call_setup_teardown": self._check_call_setup_teardown,
            "check_lte_data": self._check_lte_data,
            "check_volte": self._check_volte,
            "check_vt": self._check_vt,
            "check_wfc": self._check_wfc,
            "check_3g": self._check_3g,
            "check_tethering": self._check_tethering,
            "check_data_roaming": self._check_data_roaming_status,
            "clear_provision": self._clear_provisioning
        }
        for kwarg in kwargs:
            if kwarg not in test_method_mapping:
                self.log.error("method %s is not supported" % method)

        required_methods = []
        for method in test_method_mapping.keys():
            if method in kwargs: required_methods.append(method)

        process_list = ("rild", "netmgrd", "com.android.phone", "imsqmidaemon",
                        "imsdatadaemon", "ims_rtp_daemon", "netd",
                        "com.android.ims.rcsservice", "system_server", "cnd",
                        "modem")
        for service in process_list:
            iteration_result = "pass"
            self.log.info("Crash Recover Test for Process <%s>" % service)
            self.log.info("%s kill Process %s" % (self.dut.serial, service))
            if service == "modem":
                trigger_modem_crash(self.log, self.dut)
                time.sleep(WAIT_TIME_AFTER_CRASH * 2)
            else:
                process_pid = self.dut.adb.shell("pidof %s" % service)
                self.log.info("%s is the pidof %s" % (process_pid, service))
                if not process_pid:
                    self.dut.log.error("Process %s not running" % service)
                    iteration_result = "fail"
                if service == "netd" or service == "system_server":
                    self.dut.stop_services()
                self.dut.adb.shell(
                    "kill -9 %s" % process_pid, ignore_status=True)
                self.log.info("%s wait %d sec for radio up." %
                              (self.dut.serial, WAIT_TIME_AFTER_CRASH))
                time.sleep(WAIT_TIME_AFTER_CRASH)
                if service == "netd" or service == "system_server":
                    self.dut.start_services()
                process_pid_new = self.dut.adb.shell("pidof %s" % service)
                if process_pid == process_pid_new:
                    self.log.error("kill failed old:%s new:%s" %
                                   (process_pid, process_pid_new))
            for check in required_methods:
                if not test_method_mapping[check]():
                    fail_count[check] += 1
                    iteration_result = "fail"
            self.log.info("Crash Recover Test for Process <%s> %s" %
                          (service, iteration_result))
        for failure, count in fail_count.items():
            if count:
                self.log.error("%d %s failures" % (count, failure))
                test_result = False
        return test_result

    def _telephony_bootup_time_test(self, **kwargs):
        """Telephony Bootup Perf Test

        Arguments:
            check_lte_data: whether to check the LTE data.
            check_volte: whether to check Voice over LTE.
            check_wfc: whether to check Wifi Calling.

        Expected Results:
            Time

        Returns:
            True is pass, False if fail.
        """
        ad = self.dut
        toggle_airplane_mode(self.log, ad, False)
        if not phone_setup_volte(self.log, ad):
            ad.log.error("Failed to setup VoLTE.")
            return False
        fail_count = collections.defaultdict(int)
        test_result = True
        keyword_time_dict = {}

        for i in range(1, self.stress_test_number + 1):
            ad.log.info("Telephony Bootup Time Test %s Iteration: %d / %d",
                        self.test_name, i, self.stress_test_number)
            ad.log.info("reboot!")
            ad.reboot()
            iteration_result = "pass"

            time.sleep(30)
            text_search_mapping = {
                'boot_complete': "processing action (sys.boot_completed=1)",
                'Voice_Reg':
                "< VOICE_REGISTRATION_STATE {.regState = REG_HOME",
                'Data_Reg': "< DATA_REGISTRATION_STATE {.regState = REG_HOME",
                'Data_Call_Up': "onSetupConnectionCompleted result=SUCCESS",
                'VoLTE_Enabled': "isVolteEnabled=true",
            }

            text_obj_mapping = {
                "boot_complete": None,
                "Voice_Reg": None,
                "Data_Reg": None,
                "Data_Call_Up": None,
                "VoLTE_Enabled": None,
            }
            blocked_for_calculate = ["boot_complete"]

            for tel_state in text_search_mapping:
                dict_match = ad.search_logcat(text_search_mapping[tel_state])
                if len(dict_match) != 0:
                    text_obj_mapping[tel_state] = dict_match[0]['datetime_obj']
                else:
                    ad.log.error("Cannot Find Text %s in logcat",
                                 text_search_mapping[tel_state])
                    blocked_for_calculate.append(tel_state)

            for tel_state in text_search_mapping:
                if tel_state not in blocked_for_calculate:
                    time_diff = text_obj_mapping[tel_state] - \
                                text_obj_mapping['boot_complete']
                    if time_diff.seconds > 100:
                        continue
                    if tel_state in keyword_time_dict:
                        keyword_time_dict[tel_state].append(time_diff.seconds)
                    else:
                        keyword_time_dict[tel_state] = [
                            time_diff.seconds,
                        ]

            ad.log.info("Telephony Bootup Time Test %s Iteration: %d / %d %s",
                        self.test_name, i, self.stress_test_number,
                        iteration_result)

        for tel_state in text_search_mapping:
            if tel_state not in blocked_for_calculate:
                avg_time = self._get_list_average(keyword_time_dict[tel_state])
                if avg_time < 12.0:
                    ad.log.info("Average %s for %d iterations = %.2f seconds",
                                tel_state, self.stress_test_number, avg_time)
                else:
                    ad.log.error("Average %s for %d iterations = %.2f seconds",
                                 tel_state, self.stress_test_number, avg_time)
                    fail_count[tel_state] += 1

        ad.log.info("Bootup Time Dict {}".format(keyword_time_dict))
        for failure, count in fail_count.items():
            if count:
                ad.log.error("%d %d failures in %d iterations", count, failure,
                             self.stress_test_number)
                test_result = False
        return test_result

    """ Tests Begin """

    @test_tracker_info(uuid="4d9b425b-f804-45f4-8f47-0ba3f01a426b")
    @TelephonyBaseTest.tel_test_wrap
    def test_reboot_stress(self):
        """Reboot Reliability Test

        Steps:
            1. Reboot DUT.
            2. Check Provisioning bit (if support provisioning)
            3. Wait for DUT to camp on LTE, Verify Data.
            4. Enable VoLTE, check IMS registration. Wait for DUT report VoLTE
                enabled, make VoLTE call. And verify VoLTE SMS.
                (if support VoLTE)
            5. Connect WiFi, enable WiFi Calling, wait for DUT report WiFi
                Calling enabled and make a WFC call and verify SMS.
                Disconnect WiFi. (if support WFC)
            6. Wait for DUT to camp on 3G, Verify Data.
            7. Make CS call and verify SMS.
            8. Verify Tethering Entitlement Check and Verify WiFi Tethering.
            9. Check crashes.
            10. Repeat Step 1~9 for N times. (before reboot, clear Provisioning
                bit if provisioning is supported)

        Expected Results:
            No crash happens in stress test.

        Returns:
            True is pass, False if fail.
        """
        return self._reboot_stress_test(
            check_provision=True,
            check_call_setup_teardown=True,
            check_lte_data=True,
            check_volte=True,
            check_wfc=True,
            check_3g=True,
            check_tethering=True,
            check_data_roaming=False,
            clear_provision=True)

    @test_tracker_info(uuid="39a822e5-0360-44ce-97c7-f75468eba8d7")
    @TelephonyBaseTest.tel_test_wrap
    def test_reboot_stress_without_clear_provisioning(self):
        """Reboot Reliability Test without Clear Provisioning

        Steps:
            1. Reboot DUT.
            2. Check Provisioning bit (if support provisioning)
            3. Wait for DUT to camp on LTE, Verify Data.
            4. Enable VoLTE, check IMS registration. Wait for DUT report VoLTE
                enabled, make VoLTE call. And verify VoLTE SMS.
                (if support VoLTE)
            5. Connect WiFi, enable WiFi Calling, wait for DUT report WiFi
                Calling enabled and make a WFC call and verify SMS.
                Disconnect WiFi. (if support WFC)
            6. Wait for DUT to camp on 3G, Verify Data.
            7. Make CS call and verify SMS.
            8. Verify Tethering Entitlement Check and Verify WiFi Tethering.
            9. Check crashes.
            10. Repeat Step 1~9 for N times.

        Expected Results:
            No crash happens in stress test.

        Returns:
            True is pass, False if fail.
        """
        return self._reboot_stress_test(
            check_provision=True,
            check_call_setup_teardown=True,
            check_lte_data=True,
            check_volte=True,
            check_wfc=True,
            check_3g=True,
            check_tethering=True,
            check_data_roaming=False,
            clear_provision=False)

    @test_tracker_info(uuid="8b0e2c06-02bf-40fd-a374-08860e482757")
    @TelephonyBaseTest.tel_test_wrap
    def test_reboot_stress_check_phone_call_only(self):
        """Reboot Reliability Test

        Steps:
            1. Reboot DUT.
            2. Check phone call .
            3. Check crashes.
            4. Repeat Step 1~9 for N times. (before reboot, clear Provisioning
                bit if provisioning is supported)

        Expected Results:
            No crash happens in stress test.

        Returns:
            True is pass, False if fail.
        """
        return self._stress_test(
            check_provision=True, check_call_setup_teardown=True)

    @test_tracker_info(uuid="6c243b53-379a-4cda-9848-84fcec4019bd")
    @TelephonyBaseTest.tel_test_wrap
    def test_reboot_stress_data_roaming(self):
        """Reboot Reliability Test

        Steps:
            1. Reboot DUT.
            8. Check the data connection
            9. Check crashes.
            10. Repeat Step 1~9 for N times. (before reboot, clear Provisioning
                bit if provisioning is supported)

        Expected Results:
            No crash happens in stress test.

        Returns:
            True is pass, False if fail.
        """
        return self._reboot_stress_test(check_data_roaming=True)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="109d59ff-a488-4a68-87fd-2d8d0c035326")
    def test_bootup_optimized_stress(self):
        """Bootup Optimized Reliability Test

        Steps:
            1. Reboot DUT.
            2. Check Provisioning bit (if support provisioning)
            3. Wait for DUT to camp on LTE, Verify Data.
            4. Enable VoLTE, check IMS registration. Wait for DUT report VoLTE
                enabled, make VoLTE call. And verify VoLTE SMS.
                (if support VoLTE)
            5. Connect WiFi, enable WiFi Calling, wait for DUT report WiFi
                Calling enabled and make a WFC call and verify SMS.
                Disconnect WiFi. (if support WFC)
            6. Wait for DUT to camp on 3G, Verify Data.
            7. Make CS call and verify SMS.
            8. Verify Tethering Entitlement Check and Verify WiFi Tethering.
            9. Check crashes.
            10. Repeat Step 1~9 for N times. (before reboot, clear Provisioning
                bit if provisioning is supported)

        Expected Results:
            No crash happens in stress test.

        Returns:
            True is pass, False if fail.
        """
        return self._telephony_bootup_time_test()

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="08752fac-dbdb-4d5b-91f6-4ffc3a3ac6d6")
    def test_crash_recovery_functional(self):
        """Crash Recovery Test

        Steps:
            1. Crash multiple daemons/processes
            2. Post crash recovery, verify Voice, Data, SMS, VoLTE, VT

        Expected Results:
            No crash happens in functional test, features work fine.

        Returns:
            True is pass, False if fail.
        """
        return self._crash_recovery_test(
            check_lte_data=True, check_volte=True, check_vt=True)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="b6d2fccd-5dfd-4637-aa3b-257837bfba54")
    def test_telephonymonitor_functional(self):
        """Telephony Monitor Functional Test

        Steps:
            1. Verify Telephony Monitor functionality is working or not
            2. Force Trigger a call drop : media timeout and ensure it is
               notified by Telephony Monitor

        Expected Results:
            feature work fine, and does report to User about Call Drop

        Returns:
            True is pass, False if fail.
        """
        return self._telephony_monitor_test()


""" Tests End """
