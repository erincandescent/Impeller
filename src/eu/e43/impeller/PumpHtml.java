package eu.e43.impeller;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

public class PumpHtml implements UrlImageViewCallback, ImageGetter {
	private static final String TAG = "PumpHtml";
	
	private String   m_html;
	private TextView m_view;
	private int      m_outstanding = 0;
	
	public static void setFromHtml(TextView view, String html) {
		new PumpHtml(view, html).parse();
	}
	
	private PumpHtml(TextView view, String html) {
		m_view = view;
		m_html = html;
	}
	
	private void parse() {
		m_view.setText(Html.fromHtml(m_html, this, null));
	}
	
	@Override
	public Drawable getDrawable(String url) {
		Bitmap b = UrlImageViewHelper.getCachedBitmap(url);
		if(b != null) {
			Log.v(TAG, "getDrawable(" + url + ") successful");
			Drawable d = new BitmapDrawable(m_view.getContext().getResources(), b);
			d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
			return d;
		} else {
			Log.v(TAG, "getDrawable(" + url + ") pending");
			m_outstanding++;
			UrlImageViewHelper.loadUrlDrawable(m_view.getContext(), url, this);
			return null;
		}
	}

	@Override
	public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
		m_outstanding--;
		Log.v(TAG, "onLoaded(" + url + ") -> " + m_outstanding + " outstanding");
		if(m_outstanding == 0) parse();
	}

}
