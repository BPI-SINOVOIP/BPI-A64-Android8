package com.google.android.tradefed.targetprep;

import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ITargetPreparer} that fetches and installs an apk downloaded through mpm fetch, using
 * rapid-tool to locate the latest (optional: golden only) completed candidate of the specified
 * project.
 */
@OptionClass(alias = "mpm-apk-fetcher")
public class MpmApkFetcher implements ITargetPreparer, IBuildReceiver{

    private static final String LOG_TAG = MpmApkFetcher.class.getSimpleName();
    private static final String RAPID_NAME_REGEX = "\\s*([\\w\\.]*)\\s*";
    private static final String RAPID_STATUS_REGEX = "\\s*(\\w*)\\s*";
    private static final String RAPID_TAGS_REGEX = "\\s*([\\w\\s\\[\\]\\']*)\\s*";

    @Option(name = "apk-filename",
            description = "the filename of the apk to install",
            importance = Importance.ALWAYS)
    private String mApkFilename = null;

    @Option(name = "mpm-package-name",
            description = "the package name to fetch with mpm",
            importance = Importance.ALWAYS)
    private String mMpmPackageName = null;

    @Option(name = "rapid-project-name",
            description = "the rapid project name to be fetched",
            importance = Importance.ALWAYS)
    private String mRapidProjectName;

    @Option(name = "fetch-golden",
            description = "fetch only the latest golden candidate",
            importance = Importance.ALWAYS)
    private boolean mFetchGolden = false;

    @Option(name = "mpm-timeout",
            description = "the maximum time in milliseconds to allow mpm commands to run.")
    private long mMpmTimeout = 1000 * 60 * 5; // 5 minutes

    private IBuildInfo mBuildInfo;
    private ITestDevice mDevice;
    private IRunUtil mRunUtil = new RunUtil();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        mDevice = device;
        mBuildInfo = buildInfo;

        /* put mpm fetch files into subdir of rootOutputDir because it creates a hidden folder
         * that lives under rootOutputDir that we want to clean up:
         * i.e.:
         *   rootOutputDir/apks/...
         *   rootOutputDir/apks.mpm/... */
        File rootOutputDir = prepOutputDir(device);
        File apkOutputDir = new File(rootOutputDir, "apks");

        String latestVersion = findLatestCompletedRapidCandidate(mRapidProjectName, mFetchGolden);
        mBuildInfo.addBuildAttribute("rapid_candidate", latestVersion);

        doMpmFetch(mMpmPackageName, latestVersion, apkOutputDir);

        File apk = FileUtil.getFileForPath(apkOutputDir, mApkFilename);
        installApk(device, apk);
        mBuildInfo.addBuildAttribute("apk_ver", getApkVersion(apk));

        // cleanup -- remove downloaded binaries
        FileUtil.recursiveDelete(rootOutputDir);
    }

    /**
     * Gets a file pointer to a new temp directory that doesn't exist yet, and the dir name is
     * appended with a random number in order to avoid collision with the same test running in
     * parallel on the same host for a different device -- ie. /tmp/heart-141432412341234125/
     */
    private File prepOutputDir(ITestDevice device) throws TargetSetupError {
        File outputDir;
        try {
            outputDir = FileUtil.createTempDir("heart"); // so that we get file pointer to dir
        } catch (IOException e) {
            throw new TargetSetupError(e.getMessage(), device.getDeviceDescriptor());
        }
        FileUtil.recursiveDelete(outputDir); // because mpm fetch expects folder to not exist yet
        CLog.d("outputDir: %s: %s", outputDir.exists(), outputDir.getAbsolutePath());
        return outputDir;
    }

    /**
     * Finds the latest completed rapid candidate nested within all of the rapid releases of the
     * given rapid project, and (optionally) can return only the latest golden candidate.
     *
     * @param project the rapid project, i.e. fitness.android
     * @param fetchGolden whether to fetch only the latest gold candidate
     * @return the name of the latest compelted rapid candidate (optionally golden)
     * @throws TargetSetupError
     */
    private String findLatestCompletedRapidCandidate(String project, boolean fetchGolden)
            throws TargetSetupError {
         updateRapidTool();
         for (String release : getRapidReleases(project)) {
             String candidate = getLatestCompletedRapidCandidate(project, release, fetchGolden);
             if (candidate == null) {
                 continue;
             }
             return candidate;
         }

         String errMsg;
         if (mFetchGolden) {
             errMsg = "Couldn't find any active completed golden rapid candidate.";
         } else {
             errMsg = "Couldn't find any active completed rapid candidate.";
         }
         throw new TargetSetupError(errMsg, mDevice.getDeviceDescriptor());
    }

    /**
     * Gets the latest completed rapid candidate by using the rapid-tool on the given
     * project/release, and (optionally) can return only the latest golden candidate.
     *
     * @param project the rapid project, i.e. fitness.android
     * @param release the rapid release, i.e. fitness.android_20141219_1
     * @param fetchGolden whether to fetch only the latest gold candidate
     * @return the name of the latest completed rapid candidate (optionally golden)
     * @throws TargetSetupError
     */
    private String getLatestCompletedRapidCandidate(String project, String release,
            boolean fetchGolden) throws TargetSetupError {
        for (String line : doRapidLs(project + "/" + release).split("\n")) {
            RapidLine candidate = parseRapidCandidate(line);
            if (candidate == null
                    || candidate.getStatus().toUpperCase().contains("COMPLETE") == false) {
                continue;
            }
            if (fetchGolden && candidate.getTags().toUpperCase().contains("GOLD") == false) {
                continue;
            }
            return candidate.getName();
        }
        return null;
    }

    /**
     * Gets the list of rapid release for the given rapid project.
     *
     * @param project the rapid project, i.e. fitness.android
     * @return the list of rapid release for the given rapid project.
     * @throws TargetSetupError
     */
    private List<String> getRapidReleases(String project) throws TargetSetupError {
        List<String> releases = new ArrayList<String>();
        for (String line : doRapidLs(project).split("\n")) {
            RapidLine release = parseRapidRelease(line);
            if (release == null) {
                continue;
            }
            releases.add(release.getName());
        }
        return releases;
    }

    /**
     * Parses one rapid release line that's output to stdout after running rapid-tool ls, into a
     * parsed RapidLine object containing the relevant attributes.
     *
     * @param rawRapidOutputLine the rapid output line to parse.
     * @return the parsed RapidLine object containing the relevant candidate attributes.
     */
    private RapidLine parseRapidRelease(String rawRapidOutputLine) {
        /* see if it's a project/release pattern */
        Pattern pattern = Pattern.compile(
                "^" + RAPID_NAME_REGEX + "\\/" + RAPID_NAME_REGEX
                + "\\|" + RAPID_STATUS_REGEX + "\\|.*\\|" + RAPID_TAGS_REGEX + "$");
        Matcher matcher = pattern.matcher(rawRapidOutputLine);
        if (matcher.matches()) {
            String release = matcher.group(2);
            String status = matcher.group(3);
            String tags = matcher.group(4);
            return new RapidLine(release, status, tags);
        }
        return null;
    }

    /**
     * Parses one rapid candidate line that's output to stdout after running rapid-tool ls, into a
     * parsed RapidLine object containing the relevant attributes.
     *
     * @param rawRapidOutputLine the rapid output line to parse.
     * @return the parsed RapidLine object containing the relevant candidate attributes.
     */
    private RapidLine parseRapidCandidate(String rawRapidOutputLine) {
        /* see if it's a project/release/candidate pattern */
        Pattern pattern = Pattern.compile(
                "^" + RAPID_NAME_REGEX + "\\/" + RAPID_NAME_REGEX + "\\/" + RAPID_NAME_REGEX
                + "\\|" + RAPID_STATUS_REGEX + "\\|.*\\|" + RAPID_TAGS_REGEX + "$");
        Matcher matcher = pattern.matcher(rawRapidOutputLine);
        if (matcher.matches() && matcher.groupCount() == 5) {
            String candidate = matcher.group(3);
            String status = matcher.group(4);
            String tags = matcher.group(5);
            return new RapidLine(candidate, status, tags);
        }
        return null;
    }

    /**
     * Runs the rapid-tool ls command to retrieve the rapid entries of the given
     * rapid project/release/candidate name.
     * @param name the name of the rapid project/release/candidate to fetch info about
     * @return the stdout of the rapid entries returned by rapid-tool ls.
     *
     * @throws TargetSetupError
     */
    private String doRapidLs(String name) throws TargetSetupError {
        return runCmd(String.format("rapid-tool ls --verbose %s", name));
    }

    /**
     * Fetches all the latest files hosted through mpm fetch for the specified mpm package,
     * and downloads them to outputDir.
     *
     * @param mpmPackageName the name of the mpm package to fetch, which typically corresponds to
     * the rapid candidate (i.e. mobile/android/fitness)
     * @param version the version of the mpm package to fetch, which can either be a specific
     * rapid candidate (i.e. fitness.android_20141202_2_RC04) or 'latest'
     * @param outputDir the directory to output all the rapid content of the given rapid project.
     * the dir must not yet exist.
     */
    private void doMpmFetch(String mpmPackageName, String version, File outputDir)
            throws TargetSetupError {
        runCmd(String.format("mpm fetch -a -v %s %s %s", version, mpmPackageName, outputDir));
    }

    /**
     * Installs the specified apk file on the device.
     */
    private void installApk(ITestDevice device, File apk) throws TargetSetupError,
            DeviceNotAvailableException {
        if (!apk.exists()) {
            throw new TargetSetupError(String.format("%s does not exist",
                    apk.getAbsolutePath()), device.getDeviceDescriptor());
        }
        Log.i(LOG_TAG, String.format("Installing %s on %s", apk.getName(),
                device.getSerialNumber()));
        String[] options = {};
        String result = device.installPackage(apk, true, options);
        if (result != null) {
            throw new TargetSetupError(String.format("Failed to install %s on device %s. "
                    + "Reason: %s", apk.getAbsolutePath(), device.getSerialNumber(), result),
                    device.getDeviceDescriptor());
        }
    }

    /**
     * Gets the version number of the specified apk file.
     *
     * @throws TargetSetupError
     */
    private String getApkVersion(File apk) throws TargetSetupError {
        String aaptOutput = runCmd(String.format(
                "aapt dump badging %s | grep versionName", apk.getAbsolutePath()));
        Pattern pattern = Pattern.compile("versionName=\\'([\\d\\.\\-]*)\\'");
        Matcher matcher = pattern.matcher(aaptOutput);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return "unknown";
    }

    /**
     * Runs the specified cmd on the host machine, returning the stdout of that cmd.
     *
     * @param cmd the command to run on the host
     * @return the stdout of the cmd result
     * @throws TargetSetupError
     */
    private String runCmd(String cmd) throws TargetSetupError {
        CLog.d("About to run command: %s", cmd);
        CommandResult result = mRunUtil.runTimedCmd(mMpmTimeout, cmd.split("\\s+"));
        if (result == null || !result.getStatus().equals(CommandStatus.SUCCESS)) {
            throw new TargetSetupError(String.format(
                    "Failed to run [%s] for build %s. stdout: %s\n, stderr: %s",
                    cmd, mBuildInfo.getBuildId(), result.getStdout(), result.getStderr()),
                    mDevice.getDeviceDescriptor());
        }
        CLog.v("output:\n%s", result.getStdout());
        return result.getStdout();
    }

    /**
     * Updates the rapid tool to latest live version if an update is available, and verifies that
     * rapid tool is installed. If not yet installed, installs it.
     */
    private void updateRapidTool() throws TargetSetupError {
        String cmd = String.format("rapid-tool");
        CLog.d("About to run command: %s", cmd);
        mRunUtil.runTimedCmd(mMpmTimeout, cmd.split("\\s+"));
        assertRapidToolInstalled();
    }

    /**
     * Asserts that the rapid-tool is currently installed.
     * @throws TargetSetupError
     */
    private void assertRapidToolInstalled() throws TargetSetupError {
        String output = runCmd("which rapid-tool");
        if (!output.contains("/usr/bin/rapid-tool")) {
            throw new TargetSetupError("rapid-tool doesn't appear installed",
                    mDevice.getDeviceDescriptor());
        }
    }

    /**
     * Container to store info that was parsed from a rapid output line.
     */
    private class RapidLine {

        private String mName;
        private String mStatus;
        private String mTags;

        public RapidLine(String name, String status, String tags) {
            mName = name;
            mStatus = status;
            mTags = tags;
        }

        public String getName() {
            return mName;
        }

        public String getStatus() {
            return mStatus;
        }

        public String getTags() {
            return mTags;
        }
    }
}
