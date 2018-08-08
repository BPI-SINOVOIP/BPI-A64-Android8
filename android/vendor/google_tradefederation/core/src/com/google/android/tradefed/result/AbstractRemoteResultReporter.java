package com.google.android.tradefed.result;

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.util.Email;
import com.android.tradefed.util.IEmail;
import com.android.tradefed.util.IEmail.Message;
import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Base class for result reporters which post to a remote service.
 */
public abstract class AbstractRemoteResultReporter extends CollectingTestListener {

    private static final String LOG_TAG = "AbstractRemoteResultReporter";

    @Option(name = "report-error-email-sender", description =
        "The email sender address to use for sending report error messages.",
        importance = Importance.IF_UNSET)
    private String mReportErrorEmailSender = null;

    @Option(name = "report-error-email-destination", description =
        "The email destination address(es) to use for sending report error messages.",
        importance = Importance.IF_UNSET)
    private Collection<String> mReportErrorEmailDestination = new HashSet<String>();

    void setReportErrorEmailSender(String sender) {
        mReportErrorEmailSender = sender;
    }

    void addReportErrorEmailDestination(String destination) {
        mReportErrorEmailDestination.add(destination);
    }

    /**
     * Return a mailer object to use.
     * @return a mailer object
     */
    @VisibleForTesting
    IEmail getMailer() {
        return new Email();
    }

    /**
     * Sends an report error email if sender & destination email addresses are configured.
     *
     * @param name the name of remote service
     * @param message error message
     */
    protected void notifyReportError(String name, String message) {
        notifyReportError(name, message, null);
    }

    /**
     * Sends an report error email if sender & destination email addresses are configured.
     *
     * @param name the name of remote service
     * @param message error message
     * @param summaryUrl a test summary url
     */
    protected void notifyReportError(String name, String message, String summaryUrl) {
        Log.logAndDisplay(LogLevel.INFO, LOG_TAG, "Sending a report error email...");
        if (mReportErrorEmailDestination == null || mReportErrorEmailDestination.isEmpty()) {
            Log.logAndDisplay(LogLevel.WARN, LOG_TAG,
                    "no email destination configured for report error messages");
            return;
        }
        if (mReportErrorEmailSender == null || mReportErrorEmailSender.isEmpty()) {
            Log.logAndDisplay(LogLevel.WARN, LOG_TAG,
                    "no email sender configured for report error messages");
            return;
        }

        IEmail mailer = getMailer();
        Message msg = new Message();
        msg.setSender(mReportErrorEmailSender);
        msg.setSubject(generateReportErrorEmailSubject(name));
        msg.setBody(generateReportErrorEmailBody(message, summaryUrl));
        Iterator<String> toAddress = mReportErrorEmailDestination.iterator();
        while (toAddress.hasNext()) {
            msg.addTo(toAddress.next());
        }

        try {
            mailer.send(msg);
        } catch (IllegalArgumentException | IOException e) {
            Log.logAndDisplay(LogLevel.ERROR, LOG_TAG,
                    String.format("Failed to send email: %s", e));
        }
    }

    /**
     * A method to generate the subject for a service error email message.
     *
     * @param name the name of service
     * @return email message subject
     */
    private String generateReportErrorEmailSubject(String name) {
        final IInvocationContext context = getInvocationContext();
        final StringBuilder subj = new StringBuilder();
        subj.append(name);
        subj.append(" report error for ");
        if (!appendUnlessNull(subj, context.getTestTag())) {
            subj.append("(unknown suite)");
        }
        subj.append(" on ");
        for (IBuildInfo build: context.getBuildInfos()) {
            subj.append("{");
            appendUnlessNull(subj, build.getBuildFlavor());
            appendUnlessNull(subj, build.getBuildBranch());
            subj.append("}");
        }
        return subj.toString();
    }

    /**
     * A method to generate the body for a service error email message.
     *
     * @param message an error message
     * @return email message body
     */
    private String generateReportErrorEmailBody(String message, String summaryUrl) {
        StringBuilder bodyBuilder = new StringBuilder();

        for (IBuildInfo build : getInvocationContext().getBuildInfos()) {
            bodyBuilder.append("{");
            for (Map.Entry<String, String> buildAttr : build.getBuildAttributes().entrySet()) {
                bodyBuilder.append(buildAttr.getKey());
                bodyBuilder.append(": ");
                bodyBuilder.append(buildAttr.getValue());
                bodyBuilder.append("\n");
            }
            bodyBuilder.append("}\n");
        }
        bodyBuilder.append("host: ");
        try {
            bodyBuilder.append(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            bodyBuilder.append("unknown");
            CLog.e(e);
        }
        bodyBuilder.append("\n\n");

        bodyBuilder.append(message);
        bodyBuilder.append("\n\n");

        bodyBuilder.append(String.format("Test results:  %d passed, %d failedr\n",
                getNumTestsInState(TestStatus.PASSED), getNumAllFailedTests()));
        for (TestRunResult result : getRunResults()) {
            if (!result.getRunMetrics().isEmpty()) {
                bodyBuilder.append(String.format("'%s' test run metrics: %s\n", result.getName(),
                        result.getRunMetrics()));
            }
        }
        bodyBuilder.append("\n");

        if (summaryUrl != null) {
            bodyBuilder.append("Invocation summary report: ");
            bodyBuilder.append(summaryUrl);
        }
        return bodyBuilder.toString();
    }

    /**
     * Appends {@code str + " "} to {@code builder} IFF {@code str} is not null.
     * @return {@code true} if str is not null, {@code false} if str is null.
     */
    private static boolean appendUnlessNull(StringBuilder builder, String str) {
        if (str == null) {
            return false;
        } else {
            builder.append(str);
            builder.append(" ");
            return true;
        }
    }

}
