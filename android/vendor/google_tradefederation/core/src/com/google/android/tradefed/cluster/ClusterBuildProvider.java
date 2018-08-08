// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil2;

import org.apache.commons.compress.archivers.zip.ZipFile;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/** A {@link IBuildProvider} to download TFC test resources. */
@OptionClass(alias = "cluster", global_namespace = false)
public class ClusterBuildProvider implements IBuildProvider {

    private static final String DEFAULT_FILE_VERSION = "0";

    @Option(name = "root-dir", description = "A root directory", mandatory = true)
    private File mRootDir;

    @Option(name = "test-resource", description = "Test resources", mandatory = true)
    private Map<String, String> mTestResources = new HashMap<>();

    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        try {
            mRootDir.mkdirs();
            final IBuildInfo buildInfo = new ClusterBuildInfo(mRootDir);
            final TestResourceDownloader downloader = new TestResourceDownloader(mRootDir);
            for (final Entry<String, String> entry : mTestResources.entrySet()) {
                final TestResource resource = new TestResource(entry.getKey(), entry.getValue());
                final File file = downloader.download(resource);
                buildInfo.setFile(resource.getName(), file, DEFAULT_FILE_VERSION);
                if (file.getName().endsWith(".zip")) {
                    extractZip(file, mRootDir);
                }
            }
            return buildInfo;
        } catch (IOException e) {
            throw new BuildRetrievalError("failed to get test resources", e);
        }
    }

    /** Extracts the zip to a root dir. */
    private void extractZip(File zip, File destDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zip)) {
            ZipUtil2.extractZip(zipFile, destDir);
        } catch (IOException e) {
            throw e;
        }
    }

    @Override
    public void buildNotTested(IBuildInfo info) {}

    @Override
    public void cleanUp(IBuildInfo info) {
        if (!(info instanceof ClusterBuildInfo)) {
            throw new IllegalArgumentException("info is not an instance of ClusterBuildInfo");
        }
        final File rootDir = ((ClusterBuildInfo) info).getRootDir();
        // TODO(moonk): add an option to disable clean up for later debugging.
        FileUtil.recursiveDelete(rootDir);
    }
}
