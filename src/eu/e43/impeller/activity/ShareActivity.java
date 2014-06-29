package eu.e43.impeller.activity;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.tokenautocomplete.TokenCompleteTextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import eu.e43.impeller.Constants;
import eu.e43.impeller.PostTask;
import eu.e43.impeller.R;
import eu.e43.impeller.content.PumpContentProvider;
import eu.e43.impeller.uikit.PeopleAdapter;
import eu.e43.impeller.uikit.PersonTokenViewAdapter;

public class ShareActivity extends ActivityWithAccount implements
        View.OnClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ShareActivity";
    private static final int LOADER_PEOPLE = 0;
    private ProgressDialog m_progress = null;
    private PeopleAdapter  m_peopleAdapter = null;
    private JSONObject     m_object = null;

    private TokenCompleteTextView m_postTo, m_postCc;

    @Override
    protected void onCreateEx(Bundle savedInstanceState) {
        setContentView(R.layout.activity_share);

        String objJSON = getIntent().getStringExtra(Constants.EXTRA_ACTIVITYSTREAMS_OBJECT);
        if(objJSON == null) {
            finishActivity(RESULT_CANCELED);
            return;
        }

        try {
            m_object = new JSONObject(objJSON);
        } catch (JSONException e) {
            Log.e(TAG, "Receiving object", e);
            finishActivity(RESULT_CANCELED);
            return;
        }

        m_peopleAdapter = new PeopleAdapter(this);
        PersonTokenViewAdapter ad = new PersonTokenViewAdapter(this);

        m_postTo = (TokenCompleteTextView) findViewById(R.id.postTo);
        m_postCc = (TokenCompleteTextView) findViewById(R.id.postCc);

        m_postTo.setAdapter(m_peopleAdapter);
        m_postCc.setAdapter(m_peopleAdapter);
        m_postTo.setViewAdapter(ad);
        m_postCc.setViewAdapter(ad);

        findViewById(R.id.ok).setOnClickListener(this);
        findViewById(R.id.cancel).setOnClickListener(this);
    }

    @Override
    protected void gotAccount(Account a) {
        m_peopleAdapter.buildSpecialObjects();
        m_postCc.addObject(m_peopleAdapter.getFollowersObject());

        getSupportLoaderManager().initLoader(LOADER_PEOPLE, null, this);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.ok:
                doPost();
                break;

            case R.id.cancel:
                finish();
                break;
        }
    }

    private void doPost() {
        m_progress = ProgressDialog.show(this,
                getString(R.string.sharing_title),
                getString(R.string.sharing_status));
        m_progress.setIndeterminate(true);

        try {
            JSONObject activity = new JSONObject();
            activity.put("verb",  "share");
            activity.put("object", m_object);

            List<Object> toPeople = m_postTo.getObjects();
            JSONArray to = new JSONArray();
            for(Object dest : toPeople) {
                to.put(dest);
            }

            List<Object> ccPeople = m_postCc.getObjects();
            JSONArray cc = new JSONArray();
            for(Object dest : ccPeople) {
                cc.put(dest);
            }

            activity.put("to", to);
            activity.put("cc", cc);

            new PostTask(this, new Callback()).execute(activity.toString());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /* XXX We have code duplication here with PostActivity. We should fix that XXX */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(id == LOADER_PEOPLE) {
            Uri uri = getContentUris().objectsUri;

            return new CursorLoader(this, uri,
                    new String[] { "_ID", "_json" },
                    "objectType='person'", null,
                    "id ASC");
        } else throw new RuntimeException();
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if(loader != null && data != null) {
            data.setNotificationUri(
                    getContentResolver(),
                    ((CursorLoader) loader).getUri());
        }

        if(data != null) {
            Log.i(TAG, "LoadFinished with " + data.getCount());
        } else {
            Log.w(TAG, "LoadFinished with NULL");
        }

        m_peopleAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        m_peopleAdapter.swapCursor(null);
    }
    /* XXX End duplication XXX */

    class Callback implements PostTask.Callback {
        @Override
        public void call(JSONObject obj) {
            if(obj != null) {
                ContentValues cv = new ContentValues();
                cv.put("_json", obj.toString());
                getContentResolver().insert(getContentUris().activitiesUri, cv);
                getContentResolver().requestSync(m_account, PumpContentProvider.AUTHORITY, new Bundle());

                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(ShareActivity.this, R.string.post_error, Toast.LENGTH_SHORT).show();
                m_progress.dismiss();
                m_progress = null;
            }
        }
    }
}
