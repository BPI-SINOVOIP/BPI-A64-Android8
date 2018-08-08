// Copyright 2013 Google Inc. All Rights Reserved

package com.google.android.gcore;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.EmailResultReporter;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.util.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility that sends a email containing the html table to add to go/gms-core/builds when a new
 * gcore build is available.
 * <p/>
 * Uses template in res/gcore/DogfoodHtmlTable.txt
 */
@OptionClass(alias="dogfood-table")
public class EmailDogfoodTable extends EmailResultReporter {

    private static final Pattern GCORE_BRANCH_PATTERN = Pattern.compile(
            ".*ub-gcore-(\\w+)-release");

    private static final Pattern GCORE_VERSION_PATTERN = Pattern.compile(
            ".*google-play-services-(\\d+).*\\.aar");

    @Override
    protected String generateEmailSubject() {
        return String.format("Dogfood table for %s %s", getBuildInfo().getBuildBranch(),
                getBuildInfo().getBuildId());
    }

    // only send message on invocation success
    @Override
    protected boolean shouldSendMessage() {
        return getInvocationStatus().equals(InvocationStatus.SUCCESS);
    }

    @Override
    protected String generateEmailBody() {
        InputStream templateStream = getClass().getResourceAsStream("/gcore/DogfoodHtmlTable.txt");
        if (templateStream == null) {
            CLog.e("Could not find /gcore/DogfoodHtmlTable.txt");
        }
        try {
            String shortBranch = getShortBranchName(getBuildInfo().getBuildBranch());
            String text = StreamUtil.getStringFromStream(templateStream);
            text = text.replaceAll("SHORT_BRANCH", shortBranch);
            text = text.replaceAll("BRANCH", getBuildInfo().getBuildBranch());
            text = text.replaceAll("BUILD_NUMBER", getBuildInfo().getBuildId());
            text = insertApkVersions(text);
            return text;
        } catch (IOException e) {
            CLog.e(e);
        }
        return "Error generating email";
    }

    /**
     * Insert apk versionName and versionCode into text by parsing apk from build.
     * @param text
     * @return
     */
    private String insertApkVersions(String text) {
        String versionFromAAR = findAarVersion(getBuildInfo());

        // parse out only the major minor build numbers from version code
        int[] version = parseVersionCode(versionFromAAR);
        String versionName = String.format("%d.%d.%02d", version[0], version[1], version[2]);
        text = text.replaceAll("VERSION_NAME", versionName);
        // version code is in format Mmbbtad. Here we want a == 0 non-native arch, so just use
        // 0s for the tad bits
        String versionCode = String.format("%d%d%02d000", version[0], version[1], version[2]);
        text = text.replaceAll("VERSION_CODE", versionCode);
        return text;
    }

    private String findAarVersion(IBuildInfo buildInfo) {
        for (VersionedFile f: buildInfo.getFiles()) {
            Matcher m = GCORE_VERSION_PATTERN.matcher(f.getFile().getName());
            if (m.matches()) {
                return m.group(1);
            }
        }
        return "0000000";
    }
    /**
     * Parse the major, minor and build number from gmscore version code
     *
     * @param versionCode String version code in Mmbbtad format
     * @return a 3-element array containing major, minor, build
     */
    int[] parseVersionCode(String versionCode) {
        Pattern versionPattern = Pattern.compile("(\\d)(\\d)(\\d\\d).*");
        Matcher m = versionPattern.matcher(versionCode);
        if (m.matches()) {
            return new int[] {Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3))
            };
        }
        throw new IllegalArgumentException(String.format("Invalid version code %s", versionCode));
    }

    private String getShortBranchName(String buildBranch) {
        Matcher m = GCORE_BRANCH_PATTERN.matcher(buildBranch);
        if (m.matches()) {
            String shortBranch = m.group(1);
            // capitalize first letter
            String firstLetter = shortBranch.substring(0, 1).toUpperCase();
            return firstLetter + shortBranch.substring(1, shortBranch.length());
        }
        return buildBranch;
    }
}
