// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep.clockwork;

import com.google.android.tradefed.targetprep.clockwork.ClockworkCompanionPreparer;

import junit.framework.TestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link ClockworkCompanionPreparer}
 *
 */
public class ClockworkCompanionPreparerTest extends TestCase {

    private ClockworkCompanionPreparer mPreparer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPreparer = new ClockworkCompanionPreparer();
    }

    /**
     * Verifies that default built-in filter works
     */
    public void testGetApksToInstallNoFilter() throws Exception {
        String[] names = {"foo", "bar", "foo.apk", "bar.apk"};
        String[] expected = {"foo.apk", "bar.apk"};
        File[] files = stringsToFiles(names);
        List<File> apks = mPreparer.getApksToInstall(files, new ArrayList<String>());
        checkFilesAgainstStrings(apks, expected);
    }

    /**
     * Verifies that regex filters works
     */
    public void testGetApksWithFilter() throws Exception {
        String[] names = {"Abb.apk", "Cdd.apk", "Eff.apk", "Ghh.apk"};
        String[] patterns = {"A.*\\.apk", "G.*\\.apk"};
        String[] expected = {"Abb.apk", "Ghh.apk"};
        File[] files = stringsToFiles(names);
        List<File> apks = mPreparer.getApksToInstall(files, Arrays.asList(patterns));
        checkFilesAgainstStrings(apks, expected);
    }

    /**
     * Verifies that ordering of filters works
     */
    public void testGetApksOrdered() throws Exception {
        String[] names = {"bar", "Velvet.apk", "foo", "GmsCore.apk", "yadda",
                "ClockworkCompanionGoogle.apk", "blah", "whatever"};
        List<String> nameList = Arrays.asList(names);
        Collections.shuffle(nameList);
        names = nameList.toArray(new String[]{});
        String[] patterns = {"GmsCore\\.apk", "Velvet\\.apk", "ClockworkCompanionGoogle\\.apk"};
        String[] expected = {"GmsCore.apk", "Velvet.apk", "ClockworkCompanionGoogle.apk"};
        File[] files = stringsToFiles(names);
        List<File> apks = mPreparer.getApksToInstall(files, Arrays.asList(patterns));
        checkFilesAgainstStrings(apks, expected);
    }

    /**
     * Verifies that explicit filters followed by catch-all filters does not generate list with dup
     * entries of apk files
     */
    public void testGetApksNoDup() throws Exception {
        String[] names = {"bar.apk", "Velvet.apk", "foo.apk", "GmsCore.apk", "yadda.apk",
                "ClockworkCompanionGoogle.apk", "blah.apk", "whatever"};
        List<String> nameList = Arrays.asList(names);
        names = nameList.toArray(new String[]{});
        String[] patterns = {"GmsCore\\.apk", "Velvet\\.apk",
                "ClockworkCompanionGoogle\\.apk", ".*\\.apk"};
        String[] expected = {"GmsCore.apk", "Velvet.apk", "ClockworkCompanionGoogle.apk",
                "bar.apk", "foo.apk", "yadda.apk", "blah.apk"};
        File[] files = stringsToFiles(names);
        List<File> apks = mPreparer.getApksToInstall(files, Arrays.asList(patterns));
        checkFilesAgainstStrings(apks, expected);
    }

    File[] stringsToFiles(String[] names) {
        File[] ret = new File[names.length];
        for (int i = 0; i < names.length; i++) {
            ret[i] = new File(names[i]);
        }
        return ret;
    }

    void checkFilesAgainstStrings(List<File> files, String[] strings) {
        assertTrue("list of files and names are of different lengths",
                files.size() == strings.length);
        for (int i = 0; i < files.size(); i++) {
            assertTrue(String.format("names are different at index %d: expected: %s, actual: %s",
                    i, strings[i], files.get(i).getName()),
                    strings[i].equals(files.get(i).getName()));
        }
    }
}
