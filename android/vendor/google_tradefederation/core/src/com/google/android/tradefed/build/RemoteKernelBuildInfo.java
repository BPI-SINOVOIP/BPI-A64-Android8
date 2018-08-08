// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Container for kernel build service info response.
 */
public class RemoteKernelBuildInfo {
    private final static String SHA1_KEY = "sha1";
    private final static String SHORT_SHA1_KEY = "short-sha1";
    private final static String PATH_KEY = "path";
    private final static String FILES_KEY = "files";
    private final static String COMMIT_TIME_KEY = "commit-time";

    private final static String[] REQUIRED_KEYS = {SHORT_SHA1_KEY, PATH_KEY, FILES_KEY,
            COMMIT_TIME_KEY};
    private final static String KERNEL_FILE = "kernel";

    /**
     * Thrown if server response is not recognized.
     */
    @SuppressWarnings("serial")
    public static class InvalidResponseException extends Exception {
        InvalidResponseException(String message) {
            super(message);
        }

        InvalidResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private String mSha1;
    private String mShortSha1;
    private String mKernelFilePath;
    private long mCommitTime = 0;

    private RemoteKernelBuildInfo() {
        // Make constructor private
    }

    /**
     * Parse a remote build info from the server response.
     * <p>
     * Expected serverResponse is a JSON formatted map. If the response doesn't contain the key
     * {@code sha1}, then there is not build to test, otherwise it expects the keys
     * {@code short-sha1}, {@code path} (the path , {@code files}, {@code commit-time}
     * </p>
     *
     * @param serverResponse the server response to be parsed
     * @return a {@link RemoteKernelBuildInfo} or {@code null} if no build is available to test.
     * @throws InvalidResponseException
     */
    public static RemoteKernelBuildInfo parseRemoteBuildInfo(String serverResponse)
            throws InvalidResponseException {
        JSONTokener parser = new JSONTokener(serverResponse);
        RemoteKernelBuildInfo remoteBuild = new RemoteKernelBuildInfo();
        try {
            JSONObject obj = (JSONObject) parser.nextValue();

            if (!obj.has(SHA1_KEY)) {
                return null;
            }

            String sha1 = obj.getString(SHA1_KEY);
            if (sha1.length() != 40) {
                throw new InvalidResponseException(String.format(
                        "Invalid response: sha1 \"%s\" is not 40 characters", sha1));
            }

            for (String key : REQUIRED_KEYS) {
                if (!obj.has(key)) {
                    throw new InvalidResponseException(String.format(
                            "Invalid response: %s does not contain key %s", serverResponse, key));
                }
            }

            String path = obj.getString(PATH_KEY);
            JSONArray files = obj.getJSONArray(FILES_KEY);
            boolean kernelFound = false;
            for (int i = 0; !kernelFound && i < files.length(); i++) {
                if (KERNEL_FILE.equals(files.getString(i))) {
                    kernelFound = true;
                }
            }
            if (!kernelFound) {
                throw new InvalidResponseException(String.format(
                        "Invalid response: file list \"%s\" does not contain kernel file", files));
            }

            remoteBuild.mSha1 = sha1;
            remoteBuild.mShortSha1 = obj.getString(SHORT_SHA1_KEY);
            remoteBuild.mKernelFilePath = String.format("%s/%s", path, KERNEL_FILE);
            remoteBuild.mCommitTime = obj.getLong(COMMIT_TIME_KEY);
            return remoteBuild;
        } catch (ClassCastException e) {
            throw new InvalidResponseException(String.format(
                    "Invalid response: %s", serverResponse), e);
        } catch (JSONException e) {
            throw new InvalidResponseException(String.format(
                    "Invalid response: %s", serverResponse), e);
        }
    }

    /**
     * Get the git sha1 of the kernel.
     */
    public String getSha1() {
        return mSha1;
    }

    /**
     * Get the git short sha1 of the kernel.
     */
    public String getShortSha1() {
        return mShortSha1;
    }

    /**
     * Get the kernel file path.
     *
     * @return the path of the kernel file without the namespace info.
     */
    public String getKernelFilePath() {
        return mKernelFilePath;
    }

    /**
     * Get the git commit time of the kernel.
     */
    public long getCommitTime() {
        return mCommitTime;
    }
}
