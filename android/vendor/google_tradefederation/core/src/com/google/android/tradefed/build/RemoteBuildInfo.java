// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for launch control build info response.
 */
public class RemoteBuildInfo {

    public static final String UNKNOWN_BUILD_ID = "0";
    static final String DEFAULT_BUILD_ATTEMPT_ID = "0";

    public enum BuildType {
        /**
         * presubmit build.
         */
        PENDING("pending"),

        /**
         * postsubmit build.
         */
        SUBMITTED("submitted");

        private final String mBuildType;

        private BuildType(String buildType) {
            mBuildType = buildType;
        }

        @Override
        public String toString() {
            return mBuildType;
        }
    }

    public enum BuildAttributeKey {
        /**
         * The unique identifier of the build.
         */
        BUILD_ID("bid"),

        /**
         * The branch of the build
         */
        BRANCH("branch"),

        /**
         * An alias for the build. Typically used for release-branch builds. ie IMM76
         */
        BUILD_ALIAS("rc"),

        /**
         * The build name aka target. Format is currently
         * <branch>-<platform>-<build-flavor> e.g. git_donut-linux-opal-userdebug.
         */
        BUILD_TARGET_NAME("target"),

        /**
         * The build type
         */
        BUILD_TYPE("build_type"),

        /**
         * The build attempt ID
         */
        BUILD_ATTEMPT_ID("build_attempt_id"),

        /**
         * The device image zip
         */
        DEVICE_IMAGE("updater"),

        /**
         * User data image
         */
        USER_DATA("userdata"),

        /**
         * Emulator binary zip file path
         */
        EMULATOR("emulator"),

        /**
         * Additional files.
         */
        FILES("files"),

        /**
         * Sdk zip file path
         */
        SDK("sdk"),

        /**
         * Tests zip file path
         */
        TESTS_ZIP("tests-zip"),

        /**
         * Target files path
         */
        TARGET_FILES("target_files"),

        /**
         * application apk and test apk file paths.
         */
        APP_APKS("apks"),

        /**
         * The device OTA package (a zip)
         */
        OTA_PACKAGE("ota"),

        /**
         * The CTS zip file path
         */
        CTS("cts"),

        /**
         * The VTS zip file path
         */
        VTS("vts"),

        /**
         * The mkbootimg path
         */
        MKBOOTIMG("mkbootimg"),

        /**
         * The ramdisk path
         */
        RAMDISK("ramdisk"),

        /**
         * The bootloader path
         */
        BOOTLOADER("bootloader"),

        /**
         *  The baseband path
         */
        BASEBAND("baseband"),

        /**
         * The google-tradefed.zip file path
         */
        TF("tf");

        private String mRemoteValue;

        /**
         * Create a {@link BuildAttributeKey}.
         *
         * @param remoteValue the {@link String} value that represents the actual value passed to
         * launch control.
         */
        BuildAttributeKey(String remoteValue) {
            mRemoteValue = remoteValue;
        }

        /**
         * Gets the internal value for the {@link BuildAttributeKey}.
         */
        public String getRemoteValue() {
            return mRemoteValue;
        }
    }

    /**
     * Thrown if server response is not recognized.
     */
    @SuppressWarnings("serial")
    static class InvalidResponseException extends Exception {
        InvalidResponseException(String msg) {
            super(msg);
        }
    }


    private Map<String, String> mAttributeMap;

    /**
     * Package private for use in tests without calling parse
     */
    RemoteBuildInfo() {
        mAttributeMap = new HashMap<String, String>();
    }

    /**
     * Parse a remote build info from the server response.
     * <p/>
     * Expected return result is one or more lines of
     * <code>build_attribute_name:value</code> pairs
     *
     * @param serverResponse the {@link String} launch control response to be parsed
     * @return a {@link RemoteBuildInfo} or <code>null</code> if no build is available.
     * @throws InvalidResponseException if server response is invalid
     */
    public static RemoteBuildInfo parseRemoteBuildInfo(String serverResponse)
            throws InvalidResponseException {
        // an empty response means no build available
        // TODO: change protocol so lc returns explicit code for 'no build available'
        if (serverResponse.trim().length() == 0) {
            return null;
        }
        final String[] responseLines = serverResponse.split("\n");
        RemoteBuildInfo buildInfo = new RemoteBuildInfo();
        for (String responsePair : responseLines) {
            final String[] pair = responsePair.split(":", 2);
            if (pair.length >= 2) {
                buildInfo.addAttribute(pair[0], pair[1]);
            }
        }
        if (buildInfo.getBuildId().equals(UNKNOWN_BUILD_ID)) {
            throw new InvalidResponseException(String.format(
                    "Server response %s is missing build id", serverResponse));
        }
        // Set default build attempt id and build type.
        if (buildInfo.getAttribute(BuildAttributeKey.BUILD_ATTEMPT_ID) == null) {
            buildInfo.addAttribute(BuildAttributeKey.BUILD_ATTEMPT_ID, DEFAULT_BUILD_ATTEMPT_ID);
        }
        if (buildInfo.getAttribute(BuildAttributeKey.BUILD_TYPE) == null) {
            buildInfo.addAttribute(BuildAttributeKey.BUILD_TYPE,
                    parseBuildType(buildInfo.getBuildId()).toString());
        }
        return buildInfo;
    }

    /**
     * Parse build type from build id. Pending build start with "P".
     *
     * @param buildId
     * @return build type
     */
    private static BuildType parseBuildType(String buildId) {
        if (buildId.startsWith("P")) {
            return BuildType.PENDING;
        }
        return BuildType.SUBMITTED;
    }

    /**
     * Add a build attribute.
     *
     * @param key the unique {@link String} name of the attribute.
     * @param value the {@link String} value of the attribute.
     */
    private void addAttribute(String key, String value) {
        mAttributeMap.put(key, value);
    }

    /**
     * Add a build attribute.
     *
     * @param key the unique {@link BuildAttributeKey} name of the attribute.
     * @param value the {@link String} value of the attribute.
     */
    void addAttribute(BuildAttributeKey key, String value) {
        addAttribute(key.getRemoteValue(), value);
    }

    /**
     * Retrieve a build attribute value from a pre-defined key.
     *
     * @param key the {@link BuildAttributeKey}.
     * @return the {@link String} build attribute value, or <code>null</code> if it cannot be found.
     */
    public String getAttribute(BuildAttributeKey key) {
        return getAttribute(key.getRemoteValue());
    }

    /**
     * Retrieve a build attribute value from its launch control defined name.
     * <p/>
     * Its preferable to use {@link #getAttribute(BuildAttributeKey)} instead.
     *
     * @param key the {@link String} unique name of the attribute.
     * @return the {@link String} build attribute value, or <code>null</code> if it cannot be found.
     */
    public String getAttribute(String key) {
        return mAttributeMap.get(key);
    }

    /**
     * Helper method to retrieve the build id.
     *
     * @return the build id or {@link #UNKNOWN_BUILD_ID} if build id was missing
     * from server response.
     */
    public String getBuildId() {
        String idString = getAttribute(BuildAttributeKey.BUILD_ID);
        if (idString == null) {
            return UNKNOWN_BUILD_ID;
        }
        return idString;
    }

    @Override
    public String toString() {
        return mAttributeMap.toString();
    }
}
