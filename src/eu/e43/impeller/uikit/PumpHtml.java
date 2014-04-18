package eu.e43.impeller.uikit;

import java.net.URI;
import java.net.URISyntaxException;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.Log;
import android.widget.TextView;

import eu.e43.impeller.activity.ActivityWithAccount;

public class PumpHtml implements ImageLoader.Listener, ImageGetter {
	private static final String TAG = "PumpHtml";
	
	private ImageLoader			    m_loader;
	private TextView 			    m_view;
    private SpannableStringBuilder  m_builder;

	public static void setFromHtml(ActivityWithAccount ctx, TextView view, String html) {
		new PumpHtml(ctx, view, html);
	}
	
	private PumpHtml(ActivityWithAccount ctx, TextView view, String html) {
		m_loader  = ctx.getImageLoader();
		m_view    = view;
        m_builder = SpannableStringBuilder.valueOf(Html.fromHtml(html, this, null));
        m_view.setText(m_builder);
	}
	
	@Override
	public Drawable getDrawable(String url) {
		Drawable d = m_loader.getCachedImage(url);
		if(d != null) {
			Log.v(TAG, "getDrawable(" + url + ") successful");
			return d;
		} else {
			Log.v(TAG, "getDrawable(" + url + ") pending");
            m_loader.load(this, url);
			return null;
		}
	}
	
	@Override
	public void loaded(BitmapDrawable dr, URI uri) {

		Log.v(TAG, "loaded(" + uri + ")");

        Editable spanned = m_builder;
        ImageSpan[] imgs = spanned.getSpans(0, spanned.length(), ImageSpan.class);

        for(ImageSpan img : imgs) {
            URI asUri;
            try {
                asUri = new URI(img.getSource());
            } catch (URISyntaxException e) {
                Log.e(TAG, "Error parsing image URI", e);
                continue;
            }

            if(asUri.equals(uri)) {
                ImageSpan newSpan = new ImageSpan(dr, img.getSource());
                spanned.setSpan(newSpan, spanned.getSpanStart(img), spanned.getSpanEnd(img),
                        spanned.getSpanFlags(img));
                spanned.removeSpan(img);
            }
        }

        m_view.setText(m_builder);
	}

	@Override
	public void error(URI uri) {
		Log.w(TAG, "error(" + uri + ")");
	}

}
