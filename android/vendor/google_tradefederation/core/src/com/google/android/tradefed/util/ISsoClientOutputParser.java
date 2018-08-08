// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import java.io.InputStream;
import java.text.ParseException;

/**
 * A helper interface for parsing output from the sso_client command line utility.
 */
interface ISsoClientOutputParser {
    /**
     * Parse output from sso_client and return an initialized {@link SsoClientHttpResponse} object.
     * @param ssoClientOutput the output from sso_client
     * @throws ParseException
     */
    public SsoClientHttpResponse parse(String ssoClientOutput) throws ParseException;

    /**
     * Parse output from sso_client and return an initialized {@link SsoClientHttpResponse} object.
     * @param ssoClientOutput the output from sso_client
     * @throws ParseException
     */
    public SsoClientHttpResponse parse(InputStream ssoClientOutput) throws ParseException;
}
