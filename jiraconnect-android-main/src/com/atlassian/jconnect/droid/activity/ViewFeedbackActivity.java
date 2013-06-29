package com.atlassian.jconnect.droid.activity;

import static com.atlassian.jconnect.droid.ui.UiUtil.findTextView;
import static com.atlassian.jconnect.droid.ui.UiUtil.findView;
import static com.atlassian.jconnect.droid.ui.ViewAdapterUtils.getOrInflate;
import static com.google.common.collect.Lists.newArrayList;
import android.app.ListActivity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;

import com.atlassian.jconnect.droid.Api;
import com.atlassian.jconnect.droid.R;
import com.atlassian.jconnect.droid.config.JmcInit;
import com.atlassian.jconnect.droid.jira.Comment;
import com.atlassian.jconnect.droid.jira.Issue;
import com.atlassian.jconnect.droid.service.AbstractWrappingServiceCallback;
import com.atlassian.jconnect.droid.service.RemoteFeedbackServiceBinder;
import com.google.common.base.Strings;

public class ViewFeedbackActivity extends ListActivity {
    private static final String TAG = ViewFeedbackActivity.class.getName();

    private Issue issue;

    private RemoteFeedbackServiceBinder serviceBinder;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        JmcInit.start(this);
        // getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.jconnect_droid_view_feedback);
        initIssue(savedInstanceState);
        setListAdapter(new CommentsViewAdapter());
        initServiceBinder();
        initSendButton();
    }

    @Override
    protected void onDestroy() {
        serviceBinder.destroy();
        super.onDestroy();
    }

    private void initIssue(Bundle state) {
        if (getIntent().getExtras().containsKey(Api.ISSUE_EXTRA)) {
            issue = Api.getIssue(getIntent());
        } else if (state != null && state.containsKey(Api.ISSUE_EXTRA)) {
            // TODO secure against any exceptions (Dariuz to turn into issue)
            issue = state.getParcelable(Api.ISSUE_EXTRA);
        }
        if (issue == null) {
            throw new IllegalStateException("Issue to display must be passed to this activity");
        }
    }

    private void initServiceBinder() {
        serviceBinder = new RemoteFeedbackServiceBinder(this);
        serviceBinder.init();
    }

    private void initSendButton() {
        Button sendFeedback = findReplyButton();
        sendFeedback.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String reply = findReplyTextView().getText().toString();
                if (!Strings.isNullOrEmpty(reply)) {
                    sendReply(reply);
                }
            }
        });
    }

    private void sendReply(String reply) {
        setSending(true);
        serviceBinder.getService().reply(issue, reply, new ReplyCallback(this));
    }

    private void setSending(boolean isSending) {
        findReplyTextView().setEnabled(!isSending);
        findReplyButton().setEnabled(!isSending);
        Log.i(TAG, "Are we currently sending? " + isSending);
        setProgressBarIndeterminateVisibility(isSending);
    }

    private Button findReplyButton() {
        return findView(this, R.id.jconnect_droid_viewfeedback_reply_button, Button.class);
    }

    private EditText findReplyTextView() {
        return findView(this, R.id.jconnect_droid_viewfeedback_reply_text, EditText.class);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /*
         * TODO should put created issues here as well...? (Dariuz to turn into
         * issue)
         */
        if (issue != null) {
            outState.putParcelable(Api.ISSUE_EXTRA, issue);
        }
    }

    private CommentsViewAdapter getAdapter() {
        return (CommentsViewAdapter) getListAdapter();
    }

    private class CommentsViewAdapter extends ArrayAdapter<Comment> {

        public CommentsViewAdapter() {
            super(ViewFeedbackActivity.this, R.layout.jconnect_droid_view_feedback_list_item, 0, newArrayList(issue.getComments()));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View itemView = getOrInflate(getContext(), R.layout.jconnect_droid_view_feedback_list_item, convertView, parent);
            final Comment current = getItem(position);
            final String username = getUsername(current);
            final SpannableString contents = new SpannableString(username + ": " + current.getText());
            contents.setSpan(new StyleSpan(Typeface.BOLD), 0, username.length() + 2, 0);
            findTextView(itemView, R.id.jconnect_droid_viewfeedback_contents).setText(contents);
            findTextView(itemView, R.id.jconnect_droid_viewfeedback_date).setText(formatDate(current));
            return itemView;
        }

        private String getUsername(Comment current) {
            return current.isSystemUser() ? getString(R.string.jconnect_droid_me) : current.getUsername();
        }

        private CharSequence formatDate(Comment current) {

            return getContext().getString(R.string.jconnect_droid_sent, DateFormat.getDateFormat(getContext()).format(current.getDate()));
        }
    }

    private static class ReplyCallback extends AbstractWrappingServiceCallback<ViewFeedbackActivity, Comment> {

        public ReplyCallback(ViewFeedbackActivity owner) {
            super(owner);
        }

        @Override
        protected void onSuccess(ViewFeedbackActivity owner, Comment result) {
            owner.getAdapter().add(result);
            owner.getAdapter().notifyDataSetChanged();
            owner.findReplyTextView().getText().clear();
            owner.setSending(false);
        }

        @Override
        protected void onFailure(ViewFeedbackActivity owner, Comment result) {
            owner.setSending(false);
        }
    }

}
