package com.atlassian.jconnect.droid.dialog;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaRecorder;
import android.util.Log;

import com.atlassian.jconnect.droid.R;
import com.atlassian.jconnect.droid.ui.UiUtil;

/**
 * A dialog to record audio feedback.
 * 
 * @since v1.0
 */
public class AudioRecordingDialog extends ProgressDialog {
    public static AudioRecordingDialog forRecordingInTempDir(Context context, String recordingFileName) {
        final File output = new File(context.getExternalCacheDir(), recordingFileName);
        return new AudioRecordingDialog(context, output);
    }

    final CloseDialogListener listener = new CloseDialogListener();
    private final File recording;
    private MediaRecorder recorder;

    public AudioRecordingDialog(Context context, File recording) {
        super(context);
        this.recording = checkNotNull(recording);
    }

    public AudioRecordingDialog(Context context, int theme, File recording) {
        super(context, theme);
        this.recording = checkNotNull(recording);
    }

    private void initView() {
        setTitle(R.string.jconnect_droid_record_feedback);
        setProgressStyle(STYLE_SPINNER);
        setButton(BUTTON_POSITIVE, getContext().getString(R.string.jconnect_droid_finish_recording), listener);
        setButton(BUTTON_NEGATIVE, getContext().getString(R.string.jconnect_droid_cancel_recording), listener);
    }

    @Override
    public void show() {
        try {
            cleanUpRecordingFile();
            prepareRecorder();
            initView();
            recorder.start();
            super.show();
        } catch (Exception e) {
            Log.e("AudioRecordingDialog", "Failed to start dialog", e);
            UiUtil.alert(getContext(), R.string.jconnect_droid_recording_failed, e.getMessage());
        }
    }

    private void cleanUpRecordingFile() {
        if (!recording.delete()) {
            checkState(!recording.exists(), "Could not clean up recording file " + recording.getAbsolutePath());
        }
    }

    private void prepareRecorder() throws IOException {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setOutputFile(recording.getAbsolutePath());
        recorder.prepare();
    }

    public void stopRecording() {
        recorder.stop();
        recorder.release();
    }

    public File getRecording() {
        return recording;
    }

    public boolean hasRecording() {
        return recording.exists();
    }

    private class CloseDialogListener implements OnClickListener, OnCancelListener {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            stopRecordingAndClose(which != BUTTON_NEGATIVE);
            dismiss();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            stopRecordingAndClose(false);
        }

        private void stopRecordingAndClose(boolean saveFile) {
            stopRecording();
            if (!saveFile) {
                cleanUpRecordingFile();
            }
        }
    }
}
