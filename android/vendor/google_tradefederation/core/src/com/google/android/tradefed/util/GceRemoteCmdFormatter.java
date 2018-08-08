// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Utility class to format commands to reach a remote gce device. */
public class GceRemoteCmdFormatter {

    /**
     * Utility to create a ssh command for a gce device based on some parameters.
     *
     * @param sshKey the ssh key {@link File}.
     * @param extraOptions a List of {@link String} that can be added for extra ssh options. can be
     *     null.
     * @param hostName the hostname where to connect to the gce device.
     * @param command the actual command to run on the gce device.
     * @return a list representing the ssh command for a gce device.
     */
    public static List<String> getSshCommand(
            File sshKey, List<String> extraOptions, String hostName, String... command) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ssh");
        cmd.add("-o");
        cmd.add("UserKnownHostsFile=/dev/null");
        cmd.add("-o");
        cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o");
        cmd.add("ServerAliveInterval=10");
        cmd.add("-i");
        cmd.add(sshKey.getAbsolutePath());
        if (extraOptions != null) {
            for (String op : extraOptions) {
                cmd.add(op);
            }
        }
        cmd.add("root@" + hostName);
        for (String cmdOption : command) {
            cmd.add(cmdOption);
        }
        return cmd;
    }

    /**
     * Utility to create a scp command to fetch a file from a remote gce device.
     *
     * @param sshKey the ssh key {@link File}.
     * @param extraOptions a List of {@link String} that can be added for extra ssh options. can be
     *     null.
     * @param hostName the hostname where to connect to the gce device.
     * @param remoteFile the file to be fetched on the remote gce device.
     * @param localFile the local file where to put the remote file.
     * @return a list representing the scp command for a gce device.
     */
    public static List<String> getScpCommand(
            File sshKey,
            List<String> extraOptions,
            String hostName,
            String remoteFile,
            String localFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add("scp");
        cmd.add("-o");
        cmd.add("UserKnownHostsFile=/dev/null");
        cmd.add("-o");
        cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o");
        cmd.add("ServerAliveInterval=10");
        cmd.add("-i");
        cmd.add(sshKey.getAbsolutePath());
        if (extraOptions != null) {
            for (String op : extraOptions) {
                cmd.add(op);
            }
        }
        cmd.add(String.format("root@%s:%s", hostName, remoteFile));
        cmd.add(localFile);
        return cmd;
    }
}
