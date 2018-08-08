// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.List;

/** Unit tests for {@link GceRemoteCmdFormatter} */
@RunWith(JUnit4.class)
public class GceRemoteCmdFormatterTest {

    @Test
    public void testFormatSsh() {
        List<String> res =
                GceRemoteCmdFormatter.getSshCommand(
                        new File("/tmp/key"), null, "127.0.0.1", "stop", "adbd");
        assertEquals("ssh", res.get(0));
        assertEquals("-o", res.get(1));
        assertEquals("UserKnownHostsFile=/dev/null", res.get(2));
        assertEquals("-o", res.get(3));
        assertEquals("StrictHostKeyChecking=no", res.get(4));
        assertEquals("-o", res.get(5));
        assertEquals("ServerAliveInterval=10", res.get(6));
        assertEquals("-i", res.get(7));
        assertEquals("/tmp/key", res.get(8));
        assertEquals("root@127.0.0.1", res.get(9));
        assertEquals("stop", res.get(10));
        assertEquals("adbd", res.get(11));
    }

    @Test
    public void testFormatScp() {
        List<String> res =
                GceRemoteCmdFormatter.getScpCommand(
                        new File("/tmp/key"), null, "127.0.0.1", "/sdcard/test", "/tmp/here");
        assertEquals("scp", res.get(0));
        assertEquals("-o", res.get(1));
        assertEquals("UserKnownHostsFile=/dev/null", res.get(2));
        assertEquals("-o", res.get(3));
        assertEquals("StrictHostKeyChecking=no", res.get(4));
        assertEquals("-o", res.get(5));
        assertEquals("ServerAliveInterval=10", res.get(6));
        assertEquals("-i", res.get(7));
        assertEquals("/tmp/key", res.get(8));
        assertEquals("root@127.0.0.1:/sdcard/test", res.get(9));
        assertEquals("/tmp/here", res.get(10));
    }
}
