package com.atlassian.jconnect.droid.activity;

import static com.atlassian.jconnect.droid.jira.IssuesWithComments.isDummyIssues;
import static com.atlassian.jconnect.droid.ui.UiUtil.findTextView;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getLast;

import java.util.Date;
import java.util.List;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.atlassian.jconnect.droid.Api;
import com.atlassian.jconnect.droid.R;
import com.atlassian.jconnect.droid.config.BaseConfig;
import com.atlassian.jconnect.droid.config.JmcInit;
import com.atlassian.jconnect.droid.jira.Issue;
import com.atlassian.jconnect.droid.jira.IssuesWithComments;
import com.atlassian.jconnect.droid.persistence.IssuePersister;
import com.atlassian.jconnect.droid.service.AbstractWrappingServiceCallback;
import com.atlassian.jconnect.droid.service.RemoteFeedbackServiceBinder;
import com.atlassian.jconnect.droid.task.GetFeedbackItemsTask;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Activity that shows feedback items (JIRA issues) of given user.<br />
 * 
 * @since 1.0
 */
public class FeedbackInboxActivity extends ListActivity {
    private static final String LOG_TAG = "FeedbackInboxActivity";

    private BaseConfig baseConfig;
    private IssuePersister issuePersister;
    private IssuesWithComments currentIssues;
    private ProgressDialog spinner;
    private RemoteFeedbackServiceBinder serviceBinder;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        JmcInit.start(this);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.jconnect_droid_feedback_inbox);
        initServiceBinder();
        issuePersister = new IssuePersister(this);
        addCreateFeedbackListener();
        initSpinner();
        setListAdapter(new ViewAdapter(this, Lists.<Issue> newArrayList()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        initLocalIssues();
        getListView().post(new Runnable() {
            @Override
            public void run() {
                refreshIssuesFromServer();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        serviceBinder.destroy();
    }

    private void initServiceBinder() {
        serviceBinder = new RemoteFeedbackServiceBinder(this);
        serviceBinder.init();
    }

    @Override
    protected void onPause() {
        super.onPause();
        spinner.dismiss();
    }

    private void initLocalIssues() {
        currentIssues = issuePersister.getIssues();
        if (!isDummyIssues(currentIssues)) {
            Log.i(LOG_TAG, "Showing most recent issues.");
            showIssues(currentIssues);
        } else {
            Log.i(LOG_TAG, "Showing the spinner.");
            showSpinner();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.jconnect_feedback_inbox_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.jconnect_droid_feedbackinbox_refresh) {
            Log.i(LOG_TAG, "Showing the spinner from refresh.");
            showSpinner();
            initLocalIssues();
            refreshIssuesFromServer();
        }
        return super.onOptionsItemSelected(item);
    }

    private void addCreateFeedbackListener() {
        final View createFeedbackView = findViewById(R.id.jconnect_droid_feedbackinbox_create_feedback);
        createFeedbackView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                startActivity(Api.createFeedbackIntent(FeedbackInboxActivity.this));
            }
        });
    }

    private void initSpinner() {
        spinner = new ProgressDialog(this);
        spinner.setTitle(R.string.jconnect_droid_updating_feedback_inbox);
        spinner.setIndeterminate(true);
        spinner.setMessage(getResources().getText(R.string.jconnect_droid_refreshing_feedback));
    }

    public void refreshIssuesFromServer() {
        setProgressBarIndeterminateVisibility(true);
        serviceBinder.getService().retrieveFeedbackItems(new IssuesRetrievedCallback(this));
    }

    public void onRemoteIssuesRetrieved(GetFeedbackItemsTask.FeedbackItemsResult result) {
        showIssues(result.issues);
        issuePersister.updateUsingIssuesWithComments(result.issues);
    }

    public void onServerCheck(GetFeedbackItemsTask.FeedbackItemsResult result) {
        issuePersister.setLastServerCheck(result.issues.lastUpdated());
    }

    private void showIssues(IssuesWithComments issues) {
        currentIssues = checkNotNull(issues);
        getAdapter().clear();
        for (Issue issue : getAllIssuesToDisplay()) {
            getAdapter().add(issue);
        }
        getAdapter().notifyDataSetChanged();
    }

    private List<Issue> getAllIssuesToDisplay() {
        return currentIssues.issues();
    }

    public void showSpinner() {
        spinner.show();
    }

    public void hideSpinner() {
        spinner.hide();
    }

    @Override
    protected void onListItemClick(ListView listView, View selectedView, int position, long id) {
        Issue selected = findIssue(position);
        startActivity(Api.viewFeedbackIntent(this, selected));
    }

    private ViewAdapter getAdapter() {
        return (ViewAdapter) getListAdapter();
    }

    private Issue findIssue(int position) {
        return getAllIssuesToDisplay().get(position);
    }

    private static class ViewAdapter extends ArrayAdapter<Issue> {

        public ViewAdapter(Context context, List<Issue> issues) {
            super(context, R.layout.jconnect_droid_feedback_list_item, 0, issues);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View answer = getItemView(convertView, parent);
            final Issue issue = getItem(position);
            findTextView(answer, R.id.jconnect_droid_feedbackinbox_issuesummary).setText(issue.getTitle());
            if (!Iterables.isEmpty(issue.getComments())) {
                findTextView(answer, R.id.jconnect_droid_feedbackinbox_last_comment_ellipsis).setText(getLast(issue.getComments()).getText());
            } else {
                findTextView(answer, R.id.jconnect_droid_feedbackinbox_last_comment_ellipsis).setText(R.string.jconnect_droid_no_response);
            }
            findTextView(answer, R.id.jconnect_droid_feedbackinbox_update_date).setText(formatDateUpdated(issue.getDateUpdated()));
            return answer;
        }

        private String formatDateUpdated(Date dateUpdated) {
            if (dateUpdated == null) {
                return "";
            }
            return DateFormat.getDateFormat(getContext()).format(dateUpdated);
        }

        private View getItemView(View convertView, ViewGroup parent) {
            if (convertView != null) {
                return convertView;
            } else {
                return getInflater().inflate(R.layout.jconnect_droid_feedback_list_item, parent, false);
            }
        }

        private LayoutInflater getInflater() {
            return (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }
    }

    private static final class IssuesRetrievedCallback extends AbstractWrappingServiceCallback<FeedbackInboxActivity, IssuesWithComments> {

        public IssuesRetrievedCallback(FeedbackInboxActivity owner) {
            super(owner);
        }

        @Override
        protected void onSuccess(FeedbackInboxActivity owner, IssuesWithComments result) {
            if (result.hasIssues()) {
                owner.showIssues(result);
            }
            owner.hideSpinner();
            owner.setProgressBarIndeterminateVisibility(false);
        }

        @Override
        protected void onFailure(FeedbackInboxActivity owner, IssuesWithComments result) {
            owner.hideSpinner();
            owner.setProgressBarIndeterminateVisibility(false);
        }
    }

}
