package eu.e43.impeller.activity;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;

import com.atlassian.jconnect.droid.Api;

import eu.e43.impeller.R;

public class AboutActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        findViewById(R.id.btnFeedbackInbox).setOnClickListener(this);
        findViewById(R.id.btnSubmitFeedback).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.btnFeedbackInbox:
                startActivity(Api.viewFeedbackInboxIntent(this));
                break;

            case R.id.btnSubmitFeedback:
                startActivity(Api.createFeedbackIntent(this));
                break;
        }
    }
}
