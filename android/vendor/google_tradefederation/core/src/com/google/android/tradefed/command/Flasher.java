// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.tradefed.command;

import com.android.tradefed.command.CommandScheduler;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Main entrypoint to access TF's flashing functionality
 */
public class Flasher {
    private static final String FLASHER_CONFIG = "google/util/flash";
    private static final String SERIAL_ARG_NAME = "--flash-serial";

    protected final static String LINE_SEPARATOR = System.getProperty("line.separator");

    protected ICommandScheduler mScheduler;

    protected Flasher() {
        this(new CommandScheduler());
    }

    /**
     * Create a {@link Flasher} with given scheduler.
     * <p/>
     * Exposed for unit testing
     */
    Flasher(ICommandScheduler scheduler) {
        mScheduler = scheduler;
    }

    private void printUsage() {
        System.out.format("Specify device serials to flash with the %s commandline argument%s",
                SERIAL_ARG_NAME, LINE_SEPARATOR);
    }

    /**
     * Pulls out "--flash-serial" option plus its argument.
     *
     * @param args The argument List.  Note that the {@code args} is modified in place
     *             ("--flash-serial" and its arg are removed if present).
     * @return A List of the specified serials to flash
     */
    private List<String> parseArguments(List<String> args) {
        ListIterator<String> argIter = args.listIterator();
        List<String> serials = new ArrayList<String>(args.size());

        // forcibly move serials from the argument list to the serials list
        while (argIter.hasNext()) {
            String arg = argIter.next();
            if (SERIAL_ARG_NAME.equals(arg)) {
                argIter.remove();
                if (argIter.hasNext()) {
                    serials.add(argIter.next());
                    argIter.remove();
                } else {
                    throw new IllegalArgumentException(String.format(
                            "%s requires an argument, but none was provided.", SERIAL_ARG_NAME));
                }
            }
        }

        return serials;
    }

    /**
     * The main method to launch the flasher
     *
     * @param args
     */
    public void run(List<String> args) {
        List<String> serials = parseArguments(args);

        try {
            mScheduler.start();

            System.out.println("Welcome to TF flasher");
            if (serials.isEmpty()) {
                printUsage();
            }

            for (String serial : serials) {
                System.out.println("Got serial " + serial);

                List<String> command = new ArrayList<String>(args.size() + 3);
                command.add(FLASHER_CONFIG);
                command.addAll(args);
                command.add("--serial");
                command.add(serial);
                mScheduler.addCommand(command.toArray(new String[]{}));
            }

            // Hack: wait until the devices show up
            RunUtil.getDefault().sleep(
                    5000 /* DeviceStateMonitor.CHECK_POLL_TIME */ + 2000 /* slop */);

            mScheduler.shutdown();
            mScheduler.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(final String[] args) throws ConfigurationException {
        List<String> nonGlobalArgs = GlobalConfiguration.createGlobalConfiguration(args);

        Flasher flasher = new Flasher();
        flasher.run(nonGlobalArgs);
    }
}
