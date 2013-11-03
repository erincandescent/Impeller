package eu.e43.impeller.uikit;

import java.net.URI;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.util.Log;
import android.widget.TextView;

import eu.e43.impeller.activity.ActivityWithAccount;

public class PumpHtml implements ImageLoader.Listener, ImageGetter {
	private static final String TAG = "PumpHtml";
	
	private ImageLoader			m_loader;
	private String   			m_html;
	private TextView 			m_view;
	private int      			m_outstanding = 0;
	
	public static void setFromHtml(ActivityWithAccount ctx, TextView view, String html) {
		new PumpHtml(ctx, view, html).parse();
	}
	
	private PumpHtml(ActivityWithAccount ctx, TextView view, String html) {
		m_loader = ctx.getImageLoader();
		m_view   = view;
		m_html   = html;
	}
	
	private void parse() {
		m_view.setText(Html.fromHtml(m_html, this, null));
	}
	
	@Override
	public Drawable getDrawable(String url) {
		Drawable d = m_loader.getCachedImage(url);
		if(d != null) {
			Log.v(TAG, "getDrawable(" + url + ") successful");
			return d;
		} else {
			Log.v(TAG, "getDrawable(" + url + ") pending");
			m_outstanding++;
			m_loader.load(this, url);
			return null;
		}
	}
	
	@Override
	public void loaded(BitmapDrawable dr, URI uri) {
		m_outstanding--;	
		Log.v(TAG, "loaded(" + uri + ") -> " + m_outstanding + " outstanding");
		if(m_outstanding == 0) parse();
	}

	@Override
	public void error(URI uri) {
		Log.w(TAG, "error(" + uri + ")");
	}

}
