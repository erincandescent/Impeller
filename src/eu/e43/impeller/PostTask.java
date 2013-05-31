package eu.e43.impeller;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;

import oauth.signpost.OAuthConsumer;

import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import eu.e43.impeller.account.OAuth;

public class PostTask extends AsyncTask<String, Void, JSONObject> {
	private static final String TAG = "PostTask";
	
	public interface Callback {
		public void call(JSONObject obj);
	}
	
	Callback			 	m_cb = null;
	ActivityWithAccount		m_ctx = null;
	
	public PostTask(ActivityWithAccount ctx, Callback cb) {
		super();
		m_ctx = ctx;
		m_cb = cb;
	}
	
	@Override
	protected JSONObject doInBackground(String... activity_) {
		try {
			String activity = activity_[0];
			Log.i(TAG, "Posting " + activity);
			OAuthConsumer cons = OAuth.getConsumerForAccount(m_ctx, m_ctx.m_account);
		
			Uri outboxUri = Feed.getFeedUri(m_ctx, m_ctx.m_account, "feed");
		
			URL url = new URL(outboxUri.toString());
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			cons.sign(conn);
			
			OutputStream os = conn.getOutputStream();
			OutputStreamWriter wr = new OutputStreamWriter(os);
			wr.write(activity);
			wr.close();
						
			if(conn.getResponseCode() != 200) {
				Log.e(TAG, "Error posting: " + Utils.readAll(conn.getErrorStream()));
				return null;
			}
			
			JSONObject result = new JSONObject(Utils.readAll(conn.getInputStream()));
			
			return result;
		} catch (Exception e) {
			Log.e(TAG, "Error posting", e);
			return null;
		}
	}
	
	@Override
	protected void onPostExecute(JSONObject res) {
		m_cb.call(res);
	}
	
}
