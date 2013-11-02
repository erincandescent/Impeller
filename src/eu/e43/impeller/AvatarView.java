package eu.e43.impeller;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;

/**
 * Created by OShepherd on 02/11/13.
 */
public class AvatarView extends View {
    private Bitmap m_avatar;
    private Paint  m_paint;
    private BitmapShader m_shader;
    private Matrix m_shaderMatrix;
    private RectF m_srcRect;
    private RectF m_destRect;

    public AvatarView(Context context) {
        super(context);
        init();
    }

    public AvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AvatarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        m_shaderMatrix = new Matrix();
        m_destRect = new RectF();
        m_srcRect  = new RectF();

        setAvatar(null);
        setWillNotDraw(false);
    }

    public void setAvatar(Bitmap bmp) {
        if(bmp == null) {
            bmp = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.noavatar);
        }

        m_avatar = bmp;
        m_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        m_shader = new BitmapShader(m_avatar, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        m_paint.setShader(m_shader);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        m_srcRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
        m_destRect.set(0, 0, m_avatar.getScaledWidth(canvas), m_avatar.getScaledHeight(canvas));
        m_shaderMatrix.setRectToRect(m_destRect, m_srcRect, Matrix.ScaleToFit.CENTER);
        m_shader.setLocalMatrix(m_shaderMatrix);

        final float halfWidth = canvas.getWidth() / 2;
        final float halfHeight = canvas.getHeight() / 2;
        final float radius = Math.max(halfWidth, halfHeight);
        canvas.drawCircle(halfWidth, halfHeight, radius, m_paint);
    }
}
