package com.atlassian.jconnect.droid.acra;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import com.atlassian.jconnect.droid.jira.Issue;
import com.atlassian.jconnect.droid.jira.IssueParser;
import com.atlassian.jconnect.droid.net.RestURLGenerator;
import com.atlassian.jconnect.droid.net.params.CreateIssueParams;
import com.atlassian.jconnect.droid.persistence.IssuePersister;
import com.google.common.base.Strings;

import org.acra.ErrorReporter;
import org.acra.ReportField;
import org.acra.collector.CrashReportData;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static java.lang.String.format;

public class JiraReportSender implements ReportSender {
    public static final String TAG = JiraReportSender.class.getName();
    public static final String CF_SERVER_URL = "serverURL";
    public static final String CF_API_KEY = "apiKey";
    public static final String CF_PROJECT_KEY = "projectKey";
    public static final String CF_UUID = "uuid";
    public static final String CF_UDID = "udid";

    private static final char EOL = '\n';

    private Context context = null;

    public JiraReportSender(Context context) {
        this.context = context;
    }

    @Override
    public void send(CrashReportData reportData) throws ReportSenderException {
        // In here we have to send the report back to jira
        ErrorReporter erInstance = ErrorReporter.getInstance();
        CreateIssueParams.Builder builder = new CreateIssueParams.Builder(erInstance.getCustomData(CF_SERVER_URL), erInstance.getCustomData(CF_PROJECT_KEY),
                erInstance.getCustomData(CF_API_KEY));
        builder.uuid(erInstance.getCustomData(CF_UUID));
        builder.udid(erInstance.getCustomData(CF_UDID));
        builder.appVersion(reportData.getProperty(ReportField.APP_VERSION_CODE));
        builder.appPackageName(reportData.getProperty(ReportField.PACKAGE_NAME));
        builder.summary(getFirstLine(reportData.getProperty(ReportField.STACK_TRACE)));
        builder.description(generateDescription(reportData));
        builder.isCrash(true);

        StringBuilder environmentBuilder = new StringBuilder();
        environmentBuilder.append("== Environment Data ==").append(EOL);
        appendLine(environmentBuilder, "Phone Brand: ", reportData.getProperty(ReportField.BRAND));
        appendLine(environmentBuilder, "Phone Model: ", reportData.getProperty(ReportField.PHONE_MODEL));
        appendLine(environmentBuilder, "Product: ", reportData.getProperty(ReportField.PRODUCT));
        appendLine(environmentBuilder, "Available Hard Drive Storage: ", reportData.getProperty(ReportField.AVAILABLE_MEM_SIZE));
        appendLine(environmentBuilder, "Total Hard Drive Storage: ", reportData.getProperty(ReportField.TOTAL_MEM_SIZE));
        environmentBuilder.append(EOL);
        environmentBuilder.append("== Extra Data ==").append(EOL);
        environmentBuilder.append(reportData.getProperty(ReportField.ENVIRONMENT));

        StringBuilder settingsBuilder = new StringBuilder();
        settingsBuilder.append("== System Settings ==").append(EOL);
        settingsBuilder.append(reportData.getProperty(ReportField.SETTINGS_SYSTEM)).append(EOL);
        settingsBuilder.append(EOL);
        settingsBuilder.append("== Secure Settings ==").append(EOL);
        settingsBuilder.append(reportData.getProperty(ReportField.SETTINGS_SECURE));
        // Add in multipartEntity
        CreateIssueParams issueParams = builder.build();
        MultipartEntity multipartEntity = issueParams.toMultipartEntity();
        try {
            addPretendFileToMultipart(multipartEntity, "environment.txt", environmentBuilder.toString());
            addPretendFileToMultipart(multipartEntity, "settings.txt", settingsBuilder.toString());
            addPretendFileToMultipart(multipartEntity, "stacktrace.txt", reportData.getProperty(ReportField.STACK_TRACE));
            addPretendFileToMultipart(multipartEntity, "build.txt", reportData.getProperty(ReportField.BUILD));
            addPretendFileToMultipart(multipartEntity, "display.txt", reportData.getProperty(ReportField.DISPLAY));
            addPretendFileToMultipart(multipartEntity, "initial_configuration.txt", reportData.getProperty(ReportField.INITIAL_CONFIGURATION));
            addPretendFileToMultipart(multipartEntity, "crash_configuration.txt", reportData.getProperty(ReportField.CRASH_CONFIGURATION));
            addPretendFileToMultipart(multipartEntity, "device_features.txt", reportData.getProperty(ReportField.DEVICE_FEATURES));
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Could not find UTF-8 charset. Apparently it did not exist...");
        }

        Issue issue = null;
        final AndroidHttpClient client = AndroidHttpClient.newInstance("JIRA Connect Android Client");
        try {
            final HttpPost post = RestURLGenerator.getIssueCreateRequest(issueParams);
            post.setEntity(multipartEntity);
            final HttpResponse resp = client.execute(post);
            final StatusLine status = resp.getStatusLine();
            final String responseAsString = EntityUtils.toString(resp.getEntity());
            if (status.getStatusCode() == HttpStatus.SC_OK) {
                issue = (new IssueParser(TAG)).parse(new JSONObject(responseAsString));
            } else {
                Log.e(TAG, format("Received %s: %s: %s", status.getStatusCode(), status.getReasonPhrase(), responseAsString));
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to create JIRA issue", e);
        } catch (ParseException e) {
            Log.e(TAG, "Failed to parse the JIRA issue that was returned.", e);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse or navigate JSON that we were given.", e);
        } finally {
            client.close();
        }

        if (issue != null) {
            String issueKey = issue.getKey();
            if (issueKey != null && !issueKey.equals("CRASHES-DISABLED")) {
                IssuePersister issuePersister = new IssuePersister(context);
                issuePersister.addCreatedIssue(issue);
            }
        }
    }

    private static final void addPretendFileToMultipart(MultipartEntity entity, String fileName, String fileBody) throws UnsupportedEncodingException {
        entity.addPart(fileName, new PretendFileBody(fileName, fileBody));
    }

    private String getFirstLine(String text) {
        return text.substring(0, text.indexOf('\n'));
    }

    private static final long BYTES_PER_MEGABYTE = 1024 * 1024;

    private String generateDescription(CrashReportData reportData) {
        StringBuilder sb = new StringBuilder();

        sb.append("This is an automated bug report. A summary is given here and detailed data is contained in attatchments.\n\n");
        appendLine(sb, "App Package Name:", reportData.getProperty(ReportField.PACKAGE_NAME));
        appendLine(sb, "App Version Code:", reportData.getProperty(ReportField.APP_VERSION_CODE));
        appendLine(sb, "App Version Name:", reportData.getProperty(ReportField.APP_VERSION_NAME));
        sb.append(EOL);

        appendLine(sb, "Stack Trace:", code(reportData.getProperty(ReportField.STACK_TRACE)));

        String logcat = reportData.getProperty(ReportField.LOGCAT);
        if (!Strings.isNullOrEmpty(logcat)) {
            appendLine(sb, "logcat output:", quote(logcat));
        }

        appendLine(sb, "Display:", quote(reportData.getProperty(ReportField.DISPLAY)));

        long availableMemSize = Long.parseLong(reportData.getProperty(ReportField.AVAILABLE_MEM_SIZE));
        long totalMemSize = Long.parseLong(reportData.getProperty(ReportField.TOTAL_MEM_SIZE));
        sb.append(bold("Hard Disk Space:"));
        sb.append(availableMemSize / BYTES_PER_MEGABYTE);
        sb.append("MB avaliable out of ");
        sb.append(totalMemSize / BYTES_PER_MEGABYTE);
        sb.append("MB total").append(EOL);

        appendLine(sb, "App Private Files Path:", reportData.getProperty(ReportField.FILE_PATH));

        // sb.append(bold("App Running Time:"));
        // Duration duration = new Duration(userAppStartDate, userCrashDate);
        // sb.append(duration.toPeriod().toString(ISOPeriodFormat.alternateExtended()));
        // sb.append(" (");
        // sb.append(userAppStartDate.toString(ISODateTimeFormat.dateTimeNoMillis()));
        // sb.append(" - ");
        // sb.append(userCrashDate.toString(ISODateTimeFormat.dateTimeNoMillis()));
        // sb.append(")\n");

        // So acra puts in a N/A into this field when it was not provided.
        // Because, you know, it likes wasting space...
        String userEmail = reportData.getProperty(ReportField.USER_EMAIL);
        if (!Strings.isNullOrEmpty(userEmail) && !userEmail.equals("N/A")) {
            appendLine(sb, "User Email:", userEmail);
        }

        String dumpSysMemInfo = reportData.getProperty(ReportField.DUMPSYS_MEMINFO);
        if (!Strings.isNullOrEmpty(dumpSysMemInfo)) {
            sb.append('\n');
            appendLine(sb, "System Memory Information:", noformat(dumpSysMemInfo));
        }

        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, String title, String value) {
        sb.append(bold(title));
        sb.append(value);
        sb.append(EOL);
    }

    private static String bold(String text) {
        return "*" + text + "* ";
    }

    private static String quote(String text) {
        return "{quote}\n" + text + "\n{quote}";
    }

    private static String code(String text) {
        return "{code}\n" + text + "\n{code}";
    }

    private static String noformat(String text) {
        return "{noformat}\n" + text + "\n{noformat}";
    }
}
