//Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import com.android.tradefed.log.LogUtil.CLog;

import junit.framework.TestCase;

import org.junit.Assert;

public class IperfTestRunnerTest extends TestCase {
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testTcpSpeedParser() throws Exception {
        IperfTestRunner.TcpSpeedParser sp = new IperfTestRunner.TcpSpeedParser();
        String[] tcpOutput = {"------------------------------------------------------------",
                "Client connecting to 192.168.1.1, TCP port 5001",
                "TCP window size: 0.50 MByte (default)",
                "------------------------------------------------------------",
                "[  3] local 192.168.10.101 port 32941 connected with 192.168.1.1 port 5001",
                "[ ID] Interval       Transfer     Bandwidth",
                "[  3]  0.0-10.4 sec  8.62 MBytes  6.99 Mbits/sec"};
        String[] errorLines = {"connect failed: Connection refused"};
        /* This case is to test when output includes integer instead of double values */
        String[] tcpOutput2 = {"------------------------------------------------------------",
                "Client connecting to 192.168.1.1, TCP port 5001",
                "TCP window size: 0.50 MByte (default)",
                "------------------------------------------------------------",
                "[  3] local 192.168.1.221 port 59787 connected with 192.168.1.1 port 5001",
                "[ ID] Interval       Transfer     Bandwidth",
                "[  3]  0.0-60.0 sec   326 MBytes  45.6 Mbits/sec"};
        String[] tcpOutput3 = {"------------------------------------------------------------",
            "Client connecting to 192.168.1.1, TCP port 5001",
            "TCP window size: 0.50 MByte (default)",
            "------------------------------------------------------------",
            "[  3] local 192.168.1.221 port 59787 connected with 192.168.1.1 port 5001",
            "[ ID] Interval       Transfer     Bandwidth",
            "[  3]  0.0-130.0 sec   326 MBytes  45.6 Mbits/sec"};
        sp.processNewLines(tcpOutput);
        CLog.d("tcp speed 1 is " + sp.getSpeed());
        Assert.assertEquals(6.99, sp.getSpeed(), 0d);
        sp.processNewLines(errorLines);
        Assert.assertEquals(IperfTestRunner.INVALID_SPEED, sp.getSpeed(), 0d);
        sp.processNewLines(tcpOutput2);
        CLog.d("tcp speed 2 is: "+ sp.getSpeed());
        Assert.assertEquals(45.6, sp.getSpeed(), 0d);
        sp.processNewLines(tcpOutput3);
        CLog.d("tcp speed 3 is: " + sp.getSpeed());
        Assert.assertEquals(IperfTestRunner.INVALID_SPEED, sp.getSpeed(), 0d);

    }
    public void testUdpSpeedParser() throws Exception {
        IperfTestRunner.UdpSpeedParser sp = new IperfTestRunner.UdpSpeedParser();
        // Expected udp output
        String[] udpOutput1 = {
                "------------------------------------------------------------",
                "Sending 1470 byte datagrams",
                "UDP buffer size: 0.16 MByte (default)",
                "------------------------------------------------------------",
                "[  3] local 192.168.1.232 port 43828 connected with 192.168.1.1 port 5001",
                "[ ID] Interval       Transfer     Bandwidth",
                "[  3]  0.0-60.0 sec   397 MBytes  55.6 Mbits/sec",
                "[  3] Sent 283540 datagrams",
                "[  3] Server Report:",
                "[  3]  0.0-60.1 sec   397 MBytes  55.5 Mbits/sec   0.166 ms    0/283539 (0%)",
                "[  3]  0.0-60.1 sec  1 datagrams received out-of-order"
        };
        // udp output which contains a high loss rate and is presented in scientific notation.
        String[] udpOutput2 = {
                "------------------------------------------------------------",
                "Client connecting to 192.168.1.232, UDP port 5001",
                "Sending 1470 byte datagrams",
                "UDP buffer size: 0.22 MByte (default)",
                "------------------------------------------------------------",
                "[  3] local 192.168.1.1 port 42493 connected with 192.168.1.232 port 5001",
                "[ ID] Interval       Transfer     Bandwidth",
                "[  3]  0.0-10.0 sec   120 MBytes   101 Mbits/sec",
                "[  3] Sent 85471 datagrams",
                "[  3] Server Report:",
                "[  3]  0.0-11.0 sec  0.41 MBytes  0.32 Mbits/sec  32.354 ms 85098/85393 (1e+02%)",
                "[  3]  0.0-11.0 sec  48 datagrams received out-of-order"
        };
        // udp output with space in total number of packets sent
        String[] udpOutput3 = {
                "------------------------------------------------------------",
                "Client connecting to 192.168.1.232, UDP port 5001",
                "Sending 1470 byte datagrams",
                "UDP buffer size: 0.22 MByte (default)",
                "------------------------------------------------------------",
                "[  3] local 192.168.1.1 port 59012 connected with 192.168.1.232 port 5001",
                "[ ID] Interval       Transfer     Bandwidth",
                "[  3]  0.0-60.0 sec  2.86 MBytes  0.40 Mbits/sec",
                "[  3] Sent 2042 datagrams",
                "[  3] Server Report:",
                "[  3]  0.0-60.0 sec  2.76 MBytes  0.39 Mbits/sec  20.234 ms   71/ 2042 (3.5%)",
                "[  3]  0.0-60.0 sec  4 datagrams received out-of-order"
        };
        // udp output with total time is less than 10 seconds.
        String[] udpOutput4 = {
                "------------------------------------------------------------",
                "Client connecting to 192.168.1.230, UDP port 5001",
                "Sending 1470 byte datagrams",
                "UDP buffer size: 0.22 MByte (default)",
                " ------------------------------------------------------------",
                "[  3] local 192.168.1.1 port 49084 connected with 192.168.1.230 port 5001",
                "[ ID] Interval       Transfer     Bandwidth",
                "[  3]  0.0-10.0 sec   114 MBytes  95.9 Mbits/sec",
                "[  3] Sent 81550 datagrams",
                "[  3] Server Report:",
                "[  3]  0.0- 9.8 sec   112 MBytes  95.8 Mbits/sec   0.209 ms 1674/81549 (2.1%)",
                "[  3]  0.0- 9.8 sec  1 datagrams received out-of-order"
        };
        // udp connected failed: server is not up or the connection failed.
        String[] udpError = {
                "------------------------------------------------------------",
                "Client connecting to 192.168.1.1, UDP port 5001",
                "Sending 1470 byte datagrams",
                "UDP buffer size: 0.16 MByte (default)",
                "------------------------------------------------------------",
                "[  3] local 192.168.1.232 port 41978 connected with 192.168.1.1 port 5001",
                "[ ID] Interval       Transfer     Bandwidth",
                "[  3]  0.0-60.0 sec  246121 MBytes  34413 Mbits/sec",
                "[  3] Sent 257288 datagrams",
                "read failed: Connection refused",
                "[  3] WARNING: did not receive ack of last datagram after 1 tries."
        };
        sp.processNewLines(udpOutput1);
        Assert.assertEquals(55.5, sp.getSpeed(), 0d);
        Assert.assertEquals(0.0, sp.getLossRate(), 0d);

        sp.processNewLines(udpOutput2);
        double lossRate = 100 * (double)85098/85393;
        Assert.assertEquals(lossRate, sp.getLossRate(), 0d);
        Assert.assertEquals(0.32, sp.getSpeed(), 0d);

        sp.processNewLines(udpOutput3);
        lossRate = 100 * (double)71/2042;
        Assert.assertEquals(lossRate, sp.getLossRate(), 0d);
        Assert.assertEquals(0.39, sp.getSpeed(), 0d);

        sp.processNewLines(udpOutput4);
        lossRate = 100 * (double)1674/81549;
        Assert.assertEquals(95.8, sp.getSpeed(), 0d);
        Assert.assertEquals(lossRate, sp.getLossRate(), 0d);

        sp.processNewLines(udpError);
        Assert.assertEquals(IperfTestRunner.INVALID_SPEED, sp.getSpeed(), 0d);
    }

    public void testServerUdpTrafficParser() throws Exception {
        String[] udpServerOutput1 = {
                "[  3] local 192.168.43.1 port 5001 connected with 192.168.43.228 port 47048",
                "[ ID] Interval       Transfer     Bandwidth        Jitter   Lost/Total Datagrams",
                "[  3]  0.0-62.0 sec  34.7 MBytes  4.69 Mbits/sec   9.522 ms  374/25121 (1.5%)",
                "[  3]  0.0-62.0 sec  1 datagrams received out-of-order",
                "read failed: Connection refused"
        };
        String[] udpServerOutput2 = {
                "[  4] local 192.168.43.1 port 5001 connected with 192.168.43.228 port 47304",
                "[  4]  0.0-10.3 sec  12.3 MBytes  10.1 Mbits/sec   5.287 ms  901/ 9670 (9.3%)",
                "[  4]  0.0-10.3 sec  1 datagrams received out-of-order"
        };
        String[] udpServerOutputError = {
                "[  3] local 192.168.43.1 port 5001 connected with 192.168.43.228 port 47048",
                "[ ID] Interval       Transfer     Bandwidth        Jitter   Lost/Total Datagrams"
        };

        IperfTestRunner.ServerUdpTrafficParser sp = new IperfTestRunner.ServerUdpTrafficParser();
        sp.processNewLines(udpServerOutput1);
        CLog.d("udp speed 1 is %f", sp.getSpeed());
        Assert.assertTrue(sp.getSpeed() > IperfTestRunner.INVALID_SPEED);

        sp.processNewLines(udpServerOutput2);
        CLog.d("udp speed 2 is: %f", sp.getSpeed());
        Assert.assertTrue(sp.getSpeed() > IperfTestRunner.INVALID_SPEED);

        sp.processNewLines(udpServerOutputError);
        CLog.d("udp speed 3 is: %f", sp.getSpeed());
        Assert.assertFalse(sp.getSpeed() > IperfTestRunner.INVALID_SPEED);
    }
}
