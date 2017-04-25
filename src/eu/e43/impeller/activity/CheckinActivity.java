package eu.e43.impeller.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.location.Address;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Spinner;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import eu.e43.impeller.api.Content;
import eu.e43.impeller.LocationServices;
import eu.e43.impeller.PostTask;
import eu.e43.impeller.R;
import eu.e43.impeller.uikit.LocationAdapter;

public class CheckinActivity extends ActivityWithAccount {
    private static final String TAG = "CheckinActivity";
    private LocationAdapter m_locations;
    private Spinner         m_location;
    private ProgressDialog  m_progress;

    @Override
    protected void onCreateEx(Bundle savedInstanceState) {
        setContentView(R.layout.activity_checkin);

        m_location = (Spinner) findViewById(R.id.location);
        m_locations = new LocationAdapter(this, m_location);
        m_location.setAdapter(m_locations);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.checkin, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch(id) {
            case R.id.action_post:
                return postCheckin();

            case R.id.action_cancel:
                setResult(Activity.RESULT_CANCELED);
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean postCheckin() {
        Address addr = (Address) m_location.getSelectedItem();
        if(addr == null) {
            Toast.makeText(this, "No location selected", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            JSONObject place = LocationServices.buildPlace(addr);

            JSONObject activity = new JSONObject();
            activity.put("verb", "checkin");
            activity.put("object", place);

            m_progress = ProgressDialog.show(this, "Posting...", "Posting check in");
            new PostTask(this, new PostCallback()).execute(activity.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error activity place", e);
            Toast.makeText(this, "Error building activity", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private class PostCallback implements PostTask.Callback {
        @Override
        public void call(JSONObject obj) {
            m_progress.dismiss();
            if(obj == null) {
                Toast.makeText(CheckinActivity.this, "Error submitting post", Toast.LENGTH_SHORT).show();
            } else {
                ContentValues cv = new ContentValues();
                cv.put("_json", obj.toString());
                getContentResolver().insert(getContentUris().activitiesUri, cv);
                getContentResolver().requestSync(m_account, Content.AUTHORITY, new Bundle());

                finish();
            }
        }
    }
}
