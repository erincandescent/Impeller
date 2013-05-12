package eu.e43.impeller;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import oauth.signpost.OAuthConsumer;

import org.json.JSONObject;

import android.accounts.Account;
import eu.e43.impeller.account.OAuth;

public class AccountUtils {
	public static JSONObject whoIs(OAuthConsumer cons, Account acct) throws Exception {
		URI uri = URI.create(acct.name);
		String host     = uri.getHost();

		URL url = new URL("https", host, "api/whoami");
		HttpURLConnection conn = OAuth.fetchAuthenticated(cons, url);
		
		return new JSONObject(Utils.readAll(conn.getInputStream()));
	}
}
