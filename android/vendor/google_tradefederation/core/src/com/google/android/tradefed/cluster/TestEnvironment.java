// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A class to model a TestEnvironment message returned by TFC API. */
public class TestEnvironment {

    final Map<String, String> mEnvVars = new HashMap<>();
    final List<String> mSetupScripts = new ArrayList<>();
    final List<String> mOutputFilePatterns = new ArrayList<>();
    String mOutputFileUploadUrl = null;

    /**
     * Adds an environment variable.
     *
     * @param name a variable name.
     * @param value a variable value.
     */
    public void addEnvVar(final String name, final String value) {
        mEnvVars.put(name, value);
    }

    /**
     * Returns a {@link Map} object containing all env vars.
     *
     * @return unmodifiable map of all env vars.
     */
    public Map<String, String> getEnvVars() {
        return Collections.unmodifiableMap(mEnvVars);
    }

    /**
     * Adds a setup script command.
     *
     * @param s a setup script command.
     */
    public void addSetupScripts(final String s) {
        mSetupScripts.add(s);
    }

    /**
     * Returns a list of setup script commands.
     *
     * @return unmodifiable list of commands
     */
    public List<String> getSetupScripts() {
        return Collections.unmodifiableList(mSetupScripts);
    }

    /**
     * Adds an output file pattern.
     *
     * @param s a file pattern.
     */
    public void addOutputFilePattern(final String s) {
        mOutputFilePatterns.add(s);
    }

    /**
     * Returns a list of output file patterns.
     *
     * @return unmodifiable list of file patterns.
     */
    public List<String> getOutputFilePatterns() {
        return Collections.unmodifiableList(mOutputFilePatterns);
    }

    /**
     * Sets an output file upload URL.
     *
     * @param s a URL.
     */
    public void setOutputFileUploadUrl(final String s) {
        mOutputFileUploadUrl = s;
    }

    /**
     * Returns an output file upload URL.
     *
     * @return a URL.
     */
    public String getOutputFileUploadUrl() {
        return mOutputFileUploadUrl;
    }
}
