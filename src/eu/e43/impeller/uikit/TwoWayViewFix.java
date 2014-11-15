package eu.e43.impeller.uikit;

import android.content.Context;
import android.util.AttributeSet;

import org.lucasr.twowayview.widget.TwoWayView;

/**
 * Created by oshepherd on 2014-10-18.
 */
public class TwoWayViewFix extends TwoWayView {
    public TwoWayViewFix(Context context) {
        super(context);
    }

    public TwoWayViewFix(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TwoWayViewFix(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /* RecyclerView has a scroll bug. Workaround.
     * See http://stackoverflow.com/questions/25178329/recyclerview-and-swiperefreshlayout
     */
    @Override
    public boolean canScrollVertically(int direction) {
        // If scrolling up
        if (direction < 0) {
            boolean original = super.canScrollVertically(direction);
            return original || (getChildAt(0) != null && getChildAt(0).getTop() < 0);
        } else return super.canScrollVertically(direction);

    }
}
