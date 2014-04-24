package eu.e43.impeller.uikit;

import android.media.MediaPlayer;
import android.view.View;
import android.webkit.WebChromeClient;

import eu.e43.impeller.activity.MainActivity;

public class BrowserChrome extends WebChromeClient implements OverlayController {
    private static final String TAG = "BrowserChrome";

    private MainActivity m_activity;

    private View               m_customView;
    private CustomViewCallback m_customViewCallback;

    public BrowserChrome(MainActivity activity) {
        m_activity = activity;
    }

    @Override
    public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
        onShowCustomView(view, callback);
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        super.onShowCustomView(view, callback);

        if (m_customView != null) {
            onHideCustomView();
        }

        m_customView            = view;
        m_customViewCallback    = callback;

        m_activity.showOverlay(this, view);
    }

    @Override
    public void onHideCustomView() {
        super.onHideCustomView();
        hideCustomView();
    }

    public void hideCustomView() {
        m_activity.hideOverlay(this);
        m_customViewCallback.onCustomViewHidden();

        m_customView = null;
        m_customViewCallback = null;
    }

    @Override
    public void onHidden() {
        super.onHideCustomView();
    }

    @Override
    public void onShown() {
    }

    public boolean isImmersive() {
        return true;
    }
}