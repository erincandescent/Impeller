/* Copyright 2013 Owen Shepherd. A part of Impeller.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.e43.impeller;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONObject;

public class Utils {
    public static Uri getHostUri(Context ctx, Account user, String... components) {
        AccountManager am = AccountManager.get(ctx);
        String host     = am.getUserData(user, "host");

        Uri.Builder b = new Uri.Builder();
        b.scheme("https");
        b.authority(host);

        for(String s : components) {
            b.appendPath(s);
        }

        return b.build();
    }

    public static Uri getUserUri(Context ctx, Account user, String... components) {
        AccountManager am = AccountManager.get(ctx);
        String username = am.getUserData(user, "username");
        ArrayList<String> parts = new ArrayList<String>();
        parts.add("api");
        parts.add("user");
        parts.add(username);
        for(String s : components)
            parts.add(s);

        return getHostUri(ctx, user, parts.toArray(components));
    }


	static public String readAll(Reader r) throws IOException {
		int nRead;
		char[] buf = new char[16 * 1024];
		StringBuilder bld = new StringBuilder();
		while((nRead = r.read(buf)) != -1) {
			bld.append(buf, 0, nRead);
		}
		return bld.toString();
	}

	static public String readAll(InputStream s) throws IOException {
		return readAll(new InputStreamReader(s, "UTF-8"));
	}

    static public OutputStream copyBytes(OutputStream out, InputStream in) throws IOException {
        int nRead;
        byte[] buf = new byte[32 * 1024];
        while((nRead = in.read(buf)) != -1) {
            out.write(buf, 0, nRead);
        }
        return out;
    }

    static public byte[] readAllBytes(InputStream s) throws IOException {
        ByteArrayOutputStream aos = new ByteArrayOutputStream();
        copyBytes(aos, s);
        return aos.toByteArray();
    }

	static public String encode(Map<String, String> params) {
		try {
			StringBuilder sb = new StringBuilder();
			for(Map.Entry<String, String> entry : params.entrySet()) {
				if(sb.length() > 0) {
					sb.append('&');
				}
				sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
				sb.append('=');
				sb.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
			}
			
			return sb.toString();
		} catch(UnsupportedEncodingException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static Map<String, String> getQueryMap(String query)  {
		try {
			String[] params = query.split("&");  
			Map<String, String> map = new HashMap<String, String>();  
			for (String param : params) {
				String[] parts = param.split("=", 2);
				String name;
					name = URLDecoder.decode(parts[0], "UTF-8");
				String value = URLDecoder.decode(parts[1], "UTF-8");  
				map.put(name, value);  
		     }  
		     return map;  
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	 }

	public static byte[] sha1(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(text.getBytes("utf-8"), 0, text.length());
			return md.digest();
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch(UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
    }
	
	private static final char[] HEX_DIGITS = new char[] {
		'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
		'a', 'b', 'c', 'd', 'e', 'f'
	};
	
	public static String sha1Hex(String text) {
		byte[] sha = sha1(text);
		char[] hex = new char[sha.length * 2];
		
		for(int i = 0; i < sha.length; i++) {
			hex[2 * i + 0] = HEX_DIGITS[sha[i]        & 0x0F];
			hex[2 * i + 1] = HEX_DIGITS[(sha[1] >> 4) & 0x0F];
		}
		
		return new String(hex);
	}
	
	public static String getProxyUrl(JSONObject obj) {
		if(obj.has("pump_io")) {
			JSONObject pump_io = obj.optJSONObject("pump_io");
			String url = pump_io.optString("proxyURL", null);
			if(url == null || url.length() == 0) return null;
			return url;
		} else return null;
	}
	
	public static String getImageUrl(JSONObject img) {
		String url = getProxyUrl(img);
		if(url == null)
			url = img.optString("url");
		return url;
	}

    public static int getCollectionItemCount(JSONObject obj, String collection) {
        JSONObject col = obj.optJSONObject("collection");
        if(col != null) {
            return col.optInt("totalItems");
        } else return 0;
    }

    public static void updateStatebar(View parent, JSONObject obj) {
        updateStatebar(parent,
                getCollectionItemCount(obj, "replies"),
                getCollectionItemCount(obj, "likes"),
                getCollectionItemCount(obj, "shares"));
    }

    public static void updateStatebar(View parent, int replies, int likes, int shares) {
        TextView commentsIcon = (TextView) parent.findViewById(R.id.commentsIcon);
        TextView  sharesIcon   = (TextView) parent.findViewById(R.id.sharesIcon);
        TextView  likesIcon    = (TextView) parent.findViewById(R.id.likesIcon);

        commentsIcon.setTypeface(ImpellerApplication.fontAwesome);
        sharesIcon.setTypeface(ImpellerApplication.fontAwesome);
        likesIcon.setTypeface(ImpellerApplication.fontAwesome);

        TextView  commentCount = (TextView) parent.findViewById(R.id.commentsCount);
        TextView  shareCount   = (TextView) parent.findViewById(R.id.sharesCount);
        TextView  likeCount    = (TextView) parent.findViewById(R.id.likesCount);

        commentCount.setText(String.valueOf(replies));
        shareCount.setText(String.valueOf(shares));
        likeCount.setText(String.valueOf(likes));
    }

    public static long parseDate(String date) {
        if(date == null)
            return new Date().getTime();

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("Zulu"));
        try {
            return df.parse(date).getTime();
        } catch (ParseException e) {
            return new Date().getTime();
        }
    }

    public static String humanDate(long milis) {
        return DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(new Date(milis));
    }

    public static String humanDate(String isoDate) {
        return humanDate(parseDate(isoDate));
    }

    public static int dip(Context ctx, int dip) {
        final float density = ctx.getResources().getDisplayMetrics().density;
        return (int) (density * dip + 0.5f);
    }

}
