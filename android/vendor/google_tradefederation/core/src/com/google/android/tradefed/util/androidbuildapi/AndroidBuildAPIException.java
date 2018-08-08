// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util.androidbuildapi;

/**
 * An exception class for Android Build API related error.
 */
public class AndroidBuildAPIException extends Exception {

    private static final long serialVersionUID = 1L;

    public AndroidBuildAPIException(String msg) {
        super(msg);
    }

    public AndroidBuildAPIException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
