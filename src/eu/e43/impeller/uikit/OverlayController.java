package eu.e43.impeller.uikit;

/**
 * Created by oshepherd on 01/04/14.
 */
public interface OverlayController {
    public void onHidden();
    public void onShown();

    public boolean isImmersive();
}
