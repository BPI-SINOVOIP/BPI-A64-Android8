/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.tradefed.presubmit;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Enumeration;
import java.util.Map;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test to check for duplicate files in different jars. */
@RunWith(JUnit4.class)
public class DupFileTest {

    // List of jars to check.
    private List<String> mJarsToCheck;
    private static final String[] mSkipDirs = new String[] {"com/fasterxml/", "com/google/api/"};

    @Before
    public void setUp() throws Exception {
        mJarsToCheck =
                new ArrayList<>(
                        Arrays.asList(
                                "google-tf-prod-tests.jar",
                                "google-tradefed-contrib.jar",
                                "google-tradefed.jar",
                                "tf-prod-tests.jar",
                                "tradefed-contrib.jar",
                                "tradefed.jar"));
    }

    /** test if there are duplicate files in different jars. */
    @Test
    public void testDupFilesExist() throws IOException {
        // Get list of jars.
        List<File> jars = getListOfBuiltJars();

        // Create map of files to jars.
        Map<String, List<String>> filesToJars = getMapOfFilesAndJars(jars);

        // Check if there are any files with the same name in diff jars.
        int dupedFiles = 0;
        StringBuilder dupedFilesSummary = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : filesToJars.entrySet()) {
            String file = entry.getKey();
            List<String> jarFiles = entry.getValue();

            if (jarFiles.size() != 1) {
                dupedFiles++;
                dupedFilesSummary.append(file + ": " + jarFiles.toString() + "\n");
            }
        }

        if (dupedFiles != 0) {
            fail(
                    String.format(
                            "%d files are duplicated in different jars:\n%s",
                            dupedFiles, dupedFilesSummary.toString()));
        }
    }

    /** Create map of file to jars */
    private Map<String, List<String>> getMapOfFilesAndJars(List<File> jars) throws IOException {
        Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
        JarFile jarFile;
        List<String> jarFileList;
        // Map all the files from all the jars.
        for (File jar : jars) {
            jarFile = new JarFile(jar);
            jarFileList = getListOfFiles(jarFile);
            jarFile.close();

            // Add in the jar file to the map.
            for (String file : jarFileList) {
                if (!map.containsKey(file)) {
                    map.put(file, new LinkedList<String>());
                }

                map.get(file).add(jar.getName());
            }
        }
        return map;
    }

    /** Get the list of jars specified in the path. */
    private List<File> getListOfBuiltJars() {
        // testJarPath is the path of the jar file that contains this test
        // class.  We assume the other jars live in the same dir as this test
        // class' jar.
        String testJarPath =
                DupFileTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        File jarFilePath = new File(testJarPath);
        String jarFileParentPath = jarFilePath.getParent();
        List<File> listOfJars = new ArrayList<File>();
        File jarToCheck;
        for (String jar : mJarsToCheck) {
            jarToCheck = new File(jarFileParentPath, jar);
            if (jarToCheck.exists()) {
                listOfJars.add(jarToCheck);
            }
        }
        return listOfJars;
    }

    /** Return the list of files in the jar. */
    private List<String> getListOfFiles(JarFile jar) {
        List<String> files = new ArrayList<String>();
        Enumeration<JarEntry> e = jar.entries();
        while (e.hasMoreElements()) {
            JarEntry entry = e.nextElement();
            String filename = entry.getName();
            if (checkThisFile(filename)) {
                files.add(filename);
            }
        }
        return files;
    }

    /**
     * Check if we should add this file to list of files. We only want to check for class and xml
     * files so that's our first filter. We'll also skip some top level dirs because they are built
     * into the main prod-tests jars (the make files include the same local static java libs.
     */
    private Boolean checkThisFile(String filename) {
        if (!filename.endsWith(".class") && !filename.endsWith(".xml")) {
            return false;
        }

        for (String skipDir : mSkipDirs) {
            if (filename.startsWith(skipDir)) {
                return false;
            }
        }

        return true;
    }
}
