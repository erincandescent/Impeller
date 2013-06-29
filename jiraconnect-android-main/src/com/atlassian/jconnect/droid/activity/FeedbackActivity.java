package com.atlassian.jconnect.droid.activity;

import static com.atlassian.jconnect.droid.ui.UiUtil.getTextFromView;

import java.util.Map;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.atlassian.jconnect.droid.R;
import com.atlassian.jconnect.droid.config.BaseConfig;
import com.atlassian.jconnect.droid.config.JmcInit;
import com.atlassian.jconnect.droid.dialog.AudioRecordingDialog;
import com.atlassian.jconnect.droid.service.FeedbackAttachment;
import com.atlassian.jconnect.droid.service.RemoteFeedbackServiceBinder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

/**
 * Activity to create feedback item (which is a JIRA issue).
 * 
 * @since 1.0
 */
public class FeedbackActivity extends Activity {
    private final static int ATTACHMENT_IMAGE = 1;
    private final static int ATTACHMENT_AUDIO = 2;

    private volatile AudioRecordingDialog audioRecordingDialog;

    private volatile String selectedImage = null;
    private volatile String selectedAudio = null;
    private volatile String selectedRecording = null;

    private volatile BaseConfig baseConfig;
    private volatile RemoteFeedbackServiceBinder feedbackServiceBinder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        JmcInit.start(this);
        setContentView(R.layout.jconnect_droid_feedback);
        baseConfig = new BaseConfig(this);
        feedbackServiceBinder = new RemoteFeedbackServiceBinder(this);
        if (baseConfig.hasError()) {
            Toast.makeText(getApplicationContext(), baseConfig.getError(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        feedbackServiceBinder.init();
        initSubmitButton();
        initRecordingDialog();
    }

    private void initSubmitButton() {
        final Button submit = (Button) findViewById(R.id.submit_button);
        submit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                final String feedback = getTextFromView(FeedbackActivity.this, R.id.feedback_text);
                createIssue(feedback);
                finish();
            }
        });
    }

    private void initRecordingDialog() {
        audioRecordingDialog = AudioRecordingDialog.forRecordingInTempDir(this, "recording.mp3");
    }

    @Override
    protected void onDestroy() {
        feedbackServiceBinder.destroy();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.jconnect_feedback_mainmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        // if (id == R.id.jconnect_droid_attach_image) {
        // startAttachment(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        // ATTACHMENT_IMAGE);
        // } else if (id == R.id.jconnect_droid_attach_audio) {
        // startAttachment(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        // ATTACHMENT_AUDIO);
        // } else
        if (id == R.id.jconnect_droid_attach_record_audio) {
            audioRecordingDialog.show();
        }
        return super.onOptionsItemSelected(item);
    }

    // private void startAttachment(Uri attachmentUri, int requestCode) {
    // final Intent pick = new Intent(Intent.ACTION_PICK, attachmentUri);
    // startActivityForResult(pick, requestCode);
    // }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent returnedIntent) {
        super.onActivityResult(requestCode, resultCode, returnedIntent);

        switch (requestCode) {
        case ATTACHMENT_IMAGE:
            if (resultCode == RESULT_OK) {
                Uri imageUri = returnedIntent.getData();
                String[] filePathColumn = { MediaStore.Images.Media.DATA };
                this.selectedImage = getFilePath(imageUri, filePathColumn);
            }
            break;
        case ATTACHMENT_AUDIO:
            if (resultCode == RESULT_OK) {
                Uri audioUri = returnedIntent.getData();
                String[] filePathColumn = { MediaStore.Audio.Media.DATA };
                this.selectedAudio = getFilePath(audioUri, filePathColumn);
            }
            break;
        }
    }

    private String getFilePath(Uri audioUri, String[] filePathColumn) {
        Cursor cursor = getContentResolver().query(audioUri, filePathColumn, null, null, null);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }

    private void createIssue(final String feedback) {
        setSelectedRecording();
        feedbackServiceBinder.getService().createFeedback(feedback, getAttachments());
    }

    private Iterable<FeedbackAttachment> getAttachments() {
        final ImmutableList.Builder<FeedbackAttachment> builder = ImmutableList.builder();
        addPersistentAttachments(builder);
        addTemporaryAttachments(builder);
        return builder.build();
    }

    private Map<String, String> persistentAttachments() {
        final Map<String, String> attachments = Maps.newHashMap();
        attachments.put("screenshot", selectedImage);
        attachments.put("audioFeedback", selectedAudio);
        return attachments;
    }

    private Map<String, String> temporaryAttachments() {
        final Map<String, String> attachments = Maps.newHashMap();
        attachments.put("recordedAudioFeedback", selectedRecording);
        return attachments;
    }

    private void addPersistentAttachments(ImmutableList.Builder<FeedbackAttachment> builder) {
        for (Map.Entry<String, String> attachment : persistentAttachments().entrySet()) {
            if (attachment.getValue() != null) {
                builder.add(FeedbackAttachment.persistent(attachment.getKey(), attachment.getValue()));
            }
        }
    }

    private void addTemporaryAttachments(ImmutableList.Builder<FeedbackAttachment> builder) {
        for (Map.Entry<String, String> attachment : temporaryAttachments().entrySet()) {
            if (attachment.getValue() != null) {
                builder.add(FeedbackAttachment.temporary(attachment.getKey(), attachment.getValue()));
            }
        }
    }

    private void setSelectedRecording() {
        if (audioRecordingDialog.hasRecording()) {
            selectedRecording = audioRecordingDialog.getRecording().getAbsolutePath();
        } else {
            selectedRecording = null;
        }
    }

}
