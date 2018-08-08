// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.android.tradefed.util.PublicApkUtil.ApkInfo;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
/** Unit tests for {@link PublicApkUtil} */
public class PublicApkUtilTest {

    @Test
    public void testParseApkInfo_regular() throws Exception {
        ApkInfo info = PublicApkUtil.ApkInfo.fromCsvLine(
                "684,ru.yandex.taxi,3.35.0,1000123,ru.yandex.taxi.apk");
        Assert.assertEquals(684, info.rank);
        Assert.assertEquals("ru.yandex.taxi", info.packageName);
        Assert.assertEquals("3.35.0", info.versionString);
        Assert.assertEquals("1000123", info.versionCode);
        Assert.assertEquals("ru.yandex.taxi.apk", info.fileName);
    }

    @Test
    public void testParseApkInfo_quotedComma() throws Exception {
        ApkInfo info = PublicApkUtil.ApkInfo.fromCsvLine(
                "685,com.aimp.player,\"v2.50, Build 336 (09.04.2017)\",336,com.aimp.player.apk");
        Assert.assertEquals(685, info.rank);
        Assert.assertEquals("com.aimp.player", info.packageName);
        Assert.assertEquals("v2.50, Build 336 (09.04.2017)", info.versionString);
        Assert.assertEquals("336", info.versionCode);
        Assert.assertEquals("com.aimp.player.apk", info.fileName);
    }

    @Test
    public void testParseApkInfo_extraColumn() throws Exception {
        try {
            PublicApkUtil.ApkInfo.fromCsvLine(
                    "684,ru.yandex.taxi,3.35.0,1000123,ru.yandex.taxi.apk,foobar");
            Assert.fail("Parsing didn't fail with extra column");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }

    @Test
    public void testParseApkInfo_missingColumn() throws Exception {
        try {
            PublicApkUtil.ApkInfo.fromCsvLine("684,ru.yandex.taxi,3.35.0,1000123");
            Assert.fail("Parsing didn't fail with missing column");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }

    @Test
    public void testParseApkInfo_invalidRank() throws Exception {
        try {
            PublicApkUtil.ApkInfo.fromCsvLine(
                    "foo684,ru.yandex.taxi,3.35.0,1000123,ru.yandex.taxi.apk");
            Assert.fail("Parsing didn't fail with invalid rank field");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }
}
