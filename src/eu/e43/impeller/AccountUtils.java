package eu.e43.impeller;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.json.JSONObject;

import android.accounts.Account;
import android.content.Context;
import eu.e43.impeller.account.OAuth;

public class AccountUtils {
	public static JSONObject whoIs(Context ctx, Account acct) throws Exception {
		URI uri = URI.create(acct.name);
		String host     = uri.getHost();

		URL url = new URL("https", host, "api/whoami");
		HttpURLConnection conn = OAuth.fetchAuthenticated(ctx, acct, url);
		
		return new JSONObject(Utils.readAll(conn.getInputStream()));
	}
}
