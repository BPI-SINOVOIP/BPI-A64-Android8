// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/** A class to download test resource files from file system/GCS/HTTP. */
public class TestResourceDownloader {

    private final File mRootDir;

    public TestResourceDownloader(final File rootDir) {
        mRootDir = rootDir;
    }

    public File download(TestResource resource) throws IOException {
        final URL url = new URL(resource.getUrl());
        final String protocol = url.getProtocol();
        final File dest = new File(mRootDir, resource.getName());
        if ("file".equals(protocol)) {
            final File src = new File(resource.getUrl().substring(6));
            FileUtil.hardlinkFile(src, dest);
        } else {
            throw new UnsupportedOperationException("protocol " + protocol + " is not supported");
        }
        return dest;
    }
}
