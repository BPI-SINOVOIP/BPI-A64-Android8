// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ICompressionStrategy;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link ICompressionStrategy} for creating Easy Archives.
 *
 * @see <a href="http://go/ear">go/ear</a>
 */
public class EarCompressionStrategy implements ICompressionStrategy {

    private static final String EAR_TOOL = "/home/build/static/projects/file/ear/tools/ear";
    private static final long EAR_TIMEOUT = 2 * 60 * 1000; // 1 minute

    /**
     * {@inheritDoc}
     */
    @Override
    public File compress(File source) throws IOException {
        // Initialize the output file
        File archive = FileUtil.createTempFile("archive",".ear");

        try {
            // Build the command
            List<String> earCommand = new ArrayList<>();
            earCommand.add(EAR_TOOL);
            earCommand.add("create");
            earCommand.add("-f");
            earCommand.add("-base");
            earCommand.add(source.getAbsolutePath());
            earCommand.add(archive.getAbsolutePath());

            // Add all of the files from the source directory
            Files.walk(source.toPath())
                    .filter(p -> p.toFile().isFile())
                    .forEach(p -> earCommand.add(p.toString()));

            // Run the ear command and return the compressed archive
            CommandResult result = runTimedCmd(EAR_TIMEOUT, earCommand.toArray(new String[0]));
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                throw new IOException(
                        String.format("Failed to create easy archive: %s", result.getStderr()));
            }
        } catch (IOException | RuntimeException e) {
            // Ensure we delete the tmp file in all exception cases.
            FileUtil.deleteFile(archive);
            throw e;
        }
        return archive;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogDataType getLogDataType() {
        return LogDataType.EAR;
    }

    /** Calls {@link RunUtil#runTimedCmd(long, String[])}. Exposed for unit testing. */
    @VisibleForTesting
    protected CommandResult runTimedCmd(long timeout, String[] command) {
        return RunUtil.getDefault().runTimedCmd(timeout, command);
    }
}
