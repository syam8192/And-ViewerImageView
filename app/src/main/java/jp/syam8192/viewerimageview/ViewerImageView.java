package jp.syam8192.viewerimageview;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

/**
 * スクロール・ズーム操作可能なImageView.
 * Created by syam8192 on 2015/07/29.
 * jp.syam8192.viewerimageview.ViewerImageView
 */
public class ViewerImageView extends ImageView
        implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    public boolean scrollEnabled = true;        // スクロール・ズーム 有効/無効.
    public boolean constraintEnabled = true;    // スクロール・ズーム範囲制限 有効/無効.
    public Rect scrollInsets = null;            // スクロール余白（right,bottomも余白の幅）.(px)
    public float minimumZoomScale = 1.0f;       // ズーム値の最小.
    public float maximumZoomScale = 5.0f;       // ズーム値の最大.
    private float fitScale = -1.0f;         // 内側にフィットするズーム値.
    private float cropScale = -1.0f;        // 外側にフィットするズーム値.

    private final Matrix matrix = new Matrix();       // imageMatrixの更新用オブジェクト.
    private final Matrix touchedMatrix = new Matrix();// タッチ開始したときの画像変形行列(imageMatrix).
    private final Matrix workMatrix = new Matrix(); // 作業用.

    // タッチ矩形＝タッチした２点を対角の頂点とした、水平・垂直線の矩形.
    private final RectF touchedRect = new RectF();    // タッチ開始したときのタッチ矩形.
    private final RectF currentRect = new RectF();    // 現在のタッチ矩形.
    private boolean needsRefreshTouchedValues = false;       // touchedRect更新フラグ.

    //
    // imageMatrixアニメーション用.
    //
    private final Handler mHandler = new Handler();
    private final float[] fromValues = new float[9];  // アニメーション開始したときのmatrixの配列.
    private final float[] toValues = new float[9];    // アニメーションの目標値.
    private float animationProgress = -1.0f;
    private double startTime;
    private double duration;
    public Interpolator interpolator = new AnticipateOvershootInterpolator();

    // UI関連.
    GestureDetector gesturedetector = null;
    private boolean ignoreTouch = false;        // true ならタッチイベントを無視するフラグ.

    //
    // コンストラクタs.
    //
    public ViewerImageView(Context context) {
        this(context, null);
    }

    public ViewerImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewerImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.commonSettings(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ViewerImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.commonSettings(context);
    }

    private void commonSettings(Context context) {
        this.setScaleType(ScaleType.MATRIX);
        if (this.gesturedetector == null) {
            this.gesturedetector = new GestureDetector(context, this);
        }
        this.scrollInsets = new Rect(0, 0, 0, 0);
    }

    public void setScrollInsets(float left, float top, float right, float bottom) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();

        this.scrollInsets = new Rect(
                (int) (left * metrics.density),
                (int) (top * metrics.density),
                (int) (right * metrics.density),
                (int) (bottom * metrics.density));
    }

    /**
     * View内の矩形を指定してそこに画像を移動する.
     * constraintEnabled=trueの場合、移動後してからさらに制限範囲内に移動する.
     *
     * @param toRect   移動先の矩形.
     * @param duration 移動にかける時間.
     */
    public void setImageRect(Rect toRect, double duration) {
        Matrix toMatrix = new Matrix();
        RectF fromRect = new RectF(
                0, 0,
                this.getDrawable().getIntrinsicWidth(),
                this.getDrawable().getIntrinsicHeight());

        RectF rectf = new RectF(toRect.left, toRect.top, toRect.right, toRect.bottom);

        toMatrix.setRectToRect(fromRect, rectf, Matrix.ScaleToFit.FILL);
        toMatrix.getValues(this.toValues);
        this.startMatrixAnimation(duration);
    }

    /**
     * 内側にフィットするスケール値を返す.
     *
     * @return スケール値.
     */
    public float getZoomScaleFit() {
        if (this.fitScale < 0.0f) {
            this.fitScale = this.getZoomScaleFit(getWidth(), getHeight());
        }
        return this.fitScale;
    }

    /**
     * 内側にフィットするスケール値を返す.
     *
     * @return スケール値.
     */
    public float getZoomScaleFit(int width, int height) {
        int iw = this.getDrawable().getIntrinsicWidth();
        int ih = this.getDrawable().getIntrinsicHeight();
        if (iw * height > ih * width) {
            return (float) width / (float) iw;
        } else {
            return (float) height / (float) ih;
        }
    }

    /**
     * 外側にフィットするスケール値を返す.
     *
     * @return スケール値.
     */
    public float getZoomScaleCrop() {
        if (this.cropScale < 0.0f) {
            this.cropScale = this.getZoomScaleCrop(getWidth(), getHeight());
        }
        return this.cropScale;
    }

    /**
     * 外側にフィットするスケール値を返す.
     *
     * @return スケール値.
     */
    public float getZoomScaleCrop(int width, int height) {
        int iw = this.getDrawable().getIntrinsicWidth();
        int ih = this.getDrawable().getIntrinsicHeight();
        if (iw * height > ih * width) {
            return (float) height / (float) ih;
        } else {
            return (float) width / (float) iw;
        }
    }

    /**
     * 指定したスケールで中央を表示した状態にする.
     * @param zoomScale ズーム値.
     * @param duration  指定のズーム値に変化するのにかける時間.
     */
    public void setCenter(float zoomScale, double duration) {
        this.setMatrixAnimation(
                (this.getWidth() - ((float) this.getDrawable().getIntrinsicWidth() * zoomScale)) / 2.0f,
                (this.getHeight() - ((float) this.getDrawable().getIntrinsicHeight() * zoomScale))
                        / 2.0f,
                zoomScale, duration);
    }

    /**
     * 範囲制限有効・無効化.
     *
     * @param enabled  true = 有効 / false = 無効.
     * @param duration 有効にした場合に範囲内に納めるアニメーションの時間.
     */
    @SuppressWarnings("unused")
    public void setConstraintEnabled(boolean enabled, double duration) {
        this.constraintEnabled = enabled;
        if (setConstraintMatrix()) {
            this.setConstraintMatrixAnimation(duration);
        }
    }

    public boolean isInConstraints() {
        return animationProgress <= 0 && !this.setConstraintMatrix();
    }

    /**
     * 平行移動・ズーム率・時間を指定してアニメーションを実行する.
     *
     * @param transX   移動値.
     * @param transY   移動値.
     * @param zoom     ズーム値.
     * @param duration アニメーション時間.
     */
    public void setMatrixAnimation(float transX, float transY, float zoom, double duration) {
        for (int i = 0; i < 9; i++) {
            toValues[i] = 0;
        }
        toValues[0] = zoom;
        toValues[2] = transX;
        toValues[4] = zoom;
        toValues[5] = transY;
        toValues[8] = 1.0f;
        this.startMatrixAnimation(duration);
    }

    /**
     * いま見えている範囲（scrollInsetsが設定されている場合はその内側）を切り取ったBitmapオブジェクトを返す.
     * 表示範囲が制限内に収まっていない場合は null を返す.
     *
     *
     * @param maxPxWidth 画像の幅の最大(px).切り取り結果がこれを超える場合は超えないようにスケーリングする.
     *                   0以下 を指定すると制限しない.
     */
    public Bitmap getClippedBitmap(int maxPxWidth) {
        if ( ! this.isInConstraints() ) {
            return null;
        }
        float[] m = this.getImageMatrixValues();
        float trimmedWidth = (float)(getWidth() - scrollInsets.right - scrollInsets.left) / m[0];
        float trimmedHeight = (float)(getHeight() - scrollInsets.top - scrollInsets.bottom) / m[4];
        Matrix matrix = new Matrix();
        matrix.reset();
        if ( maxPxWidth > 0 && trimmedWidth > maxPxWidth ) {
            float s = maxPxWidth / trimmedWidth;
            matrix.setScale(s,s,0,0);
            trimmedWidth = maxPxWidth;
            trimmedHeight = trimmedWidth * getHeight() / getWidth();
        }
        Bitmap source = ((BitmapDrawable)this.getDrawable()).getBitmap();
        Bitmap resultBmp = Bitmap.createBitmap(source,
                (int)((scrollInsets.left - m[2] ) / m[0] ),
                (int)((scrollInsets.top - m[5] ) / m[4] ),
                (int)trimmedWidth,
                (int)trimmedHeight,
                matrix, true );
        return resultBmp;
    }

    /**
     * 画面更新. 更新した時点でまだアニメーションの続きがあるならフレーム更新を予約する.
     */
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (animationProgress >= 0) {
            mHandler.post(updateProc);
        }
    }

    /**
     * タッチイベントハンドラ.
     */
    @Override
    public boolean onTouchEvent(@SuppressWarnings("NullableProblems") MotionEvent ev) {
        if (this.ignoreTouch) {
            return true;
        }
        ev.setLocation(ev.getRawX(), ev.getRawY());
        int action = ev.getAction() & 0xff;
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                // スライド.
                if (!this.scrollEnabled) {
                    break;
                }
                if (needsRefreshTouchedValues) {
                    touchedMatrix.set(this.getImageMatrix());
                    this.setRect(this.touchedRect, ev);
                    needsRefreshTouchedValues = false;
                }
                this.setRect(this.currentRect, ev);
                this.matrix
                        .setRectToRect(this.touchedRect, this.currentRect, Matrix.ScaleToFit.FILL);
                this.matrix.preConcat(this.touchedMatrix);
                this.updateImageMatrix(this.matrix);
                break;
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!this.scrollEnabled) {
                    break;
                }
                stopMatrixAnimation();
                this.onTouched();
                needsRefreshTouchedValues = true;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                this.onReleased();
                if (setConstraintMatrix()) {
                    this.ignoreTouch = true;
                    this.setConstraintMatrixAnimation(350);
                }
                // ↓ フォールスルー.
            case MotionEvent.ACTION_POINTER_UP:
                needsRefreshTouchedValues = true;
                break;
            default:
                break;
        }

        return this.gesturedetector.onTouchEvent(ev) || super.onTouchEvent(ev);

    }

    private final float[] workValues = new float[9];

    public float[] getImageMatrixValues() {
        this.getImageMatrix().getValues(this.workValues);
        return this.workValues;
    }

    protected void updateImageMatrix(Matrix mat) {
        this.setImageMatrix(mat);
        this.onMatrixAnimation(this.animationProgress);
        this.invalidate();
    }

    /**
     * スクロール・ズームの範囲制限内に納める変換行列の値を toValues にセットする.
     * 必要ないようであれば toValuesには現在の変換行列の値がそのまま入る.
     * @return 制限内に収まっていない（＝このあとアニメーション開始する必要がある）場合は true.
     */
    private boolean setConstraintMatrix() {
        if (!this.constraintEnabled) {
            return false;
        }
        this.getImageMatrix().getValues(toValues);
        float dx = 0;
        float dy = 0;
        boolean scaleChanged = false;
        if (toValues[0] > this.maximumZoomScale || toValues[4] > this.maximumZoomScale) {
            this.getImageMatrix().invert(workMatrix);
            float[] c = {(float) this.getWidth() / 2.0f, (float) this.getHeight() / 2.0f};
            workMatrix.mapPoints(c);
            float sw = (float) this.getWidth() / this.maximumZoomScale / 2.0f;
            float sh = (float) this.getHeight() / this.maximumZoomScale / 2.0f;
            RectF sr = new RectF(c[0] - sw, c[1] - sh, c[0] + sw, c[1] + sh);
            RectF dr = new RectF(0, 0, this.getWidth(), this.getHeight());
            workMatrix.setRectToRect(sr, dr, Matrix.ScaleToFit.FILL);
            workMatrix.getValues(toValues);
            scaleChanged = true;
        }
        if (toValues[0] < this.minimumZoomScale || toValues[4] < this.minimumZoomScale) {
            toValues[0] = this.minimumZoomScale;
            toValues[4] = this.minimumZoomScale;
            scaleChanged = true;
        }
        float w = toValues[0] * (float) this.getDrawable().getIntrinsicWidth();
        float h = toValues[4] * (float) this.getDrawable().getIntrinsicHeight();
        float top = this.scrollInsets.top;
        float left = this.scrollInsets.left;
        float right = this.getWidth() - w - this.scrollInsets.right;
        float bottom = this.getHeight() - h - this.scrollInsets.bottom;
        if (w <= (this.getWidth() - this.scrollInsets.left - this.scrollInsets.right)) {
            float lim = (this.getWidth() - this.scrollInsets.right + this.scrollInsets.left - w)
                    / 2.0f;
            // センタリング.
            if (toValues[2] != lim) {
                dx = toValues[2] - lim;
                toValues[2] = lim;
            }
        } else {
            // 移動制限.
            if (toValues[2] > left) {
                dx = toValues[2];
                toValues[2] = left;
            }
            if (toValues[2] < right) {
                dx = toValues[2] - (right);
                toValues[2] = right;
            }
        }
        if (h <= this.getHeight() - this.scrollInsets.top - this.scrollInsets.bottom) {
            float lim = (this.getHeight() - this.scrollInsets.bottom + this.scrollInsets.top - h)
                    / 2.0f;
            // センタリング.
            if (toValues[5] != lim) {
                dy = toValues[5] - lim;
                toValues[5] = lim;
            }
        } else {
            if (toValues[5] > top) {
                dy = toValues[5];
                toValues[5] = top;
            }
            if (toValues[5] < bottom) {
                dy = toValues[5] - (bottom);
                toValues[5] = bottom;
            }
        }
        return (dx != 0.0f || dy != 0.0f || scaleChanged);
    }

    /**
     * 移動・ズームの制限.
     */
    protected void setConstraintMatrixAnimation(double duration) {
        this.startMatrixAnimation(duration);
    }

    /**
     * imageMatrixアニメーション開始.
     */
    protected void startMatrixAnimation(double d) {
        if (d > 0) {
            this.getImageMatrix().getValues(this.fromValues);
            this.startTime = System.currentTimeMillis();
            this.duration = d;
            this.animationProgress = 0.0f;
        } else {
            stopMatrixAnimation();
            this.matrix.setValues(toValues);
            this.updateImageMatrix(matrix);
        }
        this.invalidate();
    }

    /**
     * ImageMatrixアニメーション開始.
     */
    void startMatrixAnimationWithConstraints(float[] m, double duration) {
        final Matrix srcmat = new Matrix();
        srcmat.set(this.getImageMatrix());  // 今の場所をとっておいて
        matrix.setValues(m);
        setImageMatrix(matrix);             // いったん移動先に飛ばし、
        this.setConstraintMatrix();         // 範囲制限かけた結果をアニメの移動先にして
        setImageMatrix(srcmat);             // とっておいた元の場所に戻してから
        this.startMatrixAnimation(duration);     // アニメーション開始.
    }

    /**
     * imageMatrixアニメーション停止.
     */
    protected void stopMatrixAnimation() {
        this.animationProgress = -1.0f;
        this.interpolator = new OvershootInterpolator();
        this.ignoreTouch = false;
    }

    /**
     * imageMatrixアニメーションのフレーム更新.
     */
    private final Runnable updateProc = new Runnable() {
        private float[] m = new float[9];
        private final Matrix frameMatrix = new Matrix();

        @Override
        public void run() {
            animationProgress = (float) (System.currentTimeMillis() - startTime) / (float) duration;
            animationProgress = Math.min(animationProgress, 1.0f);
            float pp = interpolator.getInterpolation(animationProgress);
            for (int i = 0; i < 9; i++) {
                m[i] = (pp * toValues[i]) + ((1.0f - pp) * fromValues[i]);
            }
            frameMatrix.setValues(m);
            updateImageMatrix(frameMatrix);

            if (animationProgress >= 1.0f) {
                stopMatrixAnimation();
                onFinishedMatrixAnimation();
                if (setConstraintMatrix()) {
                    setConstraintMatrixAnimation(300);
                }
            }
        }
    };

    /**
     * タッチ矩形をセットする.
     */
    private RectF setRect(RectF rect, MotionEvent ev) {
        if (ev.getPointerCount() > 1) {
            return this.setRect(rect, ev.getX(0), ev.getY(0), ev.getX(1), ev.getY(1));
        } else {
            // タッチが１点のときは x, y に +1 した仮想の点をもつ（結果的に画像は平行移動する）.
            return this.setRect(rect, ev.getX(0), ev.getY(0), ev.getX(0) + 1, ev.getY(0) + 1);
        }
    }

    /**
     * ２点を対角とする垂直・水平な辺の矩形を表す矩形をrectに設定して、それを返す.
     *
     * @param rect 設定先 RectFオブジェクト.
     * @param x0   点 1 の X 座標.
     * @param y0   点 1 の Y 座標.
     * @param x1   点 2 の X 座標.
     * @param y1   点 2 の Y 座標.
     * @return 設定した結果の RectF オブジェクト.
     */
    private RectF setRect(RectF rect, float x0, float y0, float x1, float y1) {
        rect.left = Math.min(x0, x1);
        rect.right = Math.max(x0, x1);
        rect.top = Math.min(y0, y1);
        rect.bottom = Math.max(y0, y1);
        if (rect.right - rect.left > rect.bottom - rect.top) {
            rect.bottom = rect.top + rect.right - rect.left;
        } else {
            rect.right = rect.left + rect.bottom - rect.top;
        }
        return rect;
    }

    protected void onTouched() {
        // override point.
    }

    protected void onReleased() {
        // override point.
    }

    protected void onMatrixAnimation(float progress) {
        // override point.
    }

    protected void onFinishedMatrixAnimation() {
        // override point.
    }

    //
    // ジェスチャ関連.
    //
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        final float[] m = this.getImageMatrixValues();
        float scale;
        double duration;
        if (m[0] < getZoomScaleFit()) {
            this.interpolator = new AccelerateDecelerateInterpolator();
            duration = 300;
            scale = this.fitScale;
        } else if (m[0] > getZoomScaleCrop()) {
            this.ignoreTouch = true;
            this.interpolator = new OvershootInterpolator();
            this.setCenter( getZoomScaleFit() , 400);
            return true;
        } else if (m[0] == getZoomScaleCrop()) {
            this.interpolator = new AccelerateDecelerateInterpolator();
            duration = 400;
            scale = this.maximumZoomScale;
        } else {
            this.interpolator = new AccelerateDecelerateInterpolator();
            duration = 300;
            scale = getZoomScaleCrop();
        }

        float px = (e.getX() - m[2]) / m[0];
        float py = (e.getY() - m[5]) / m[4];
        toValues[0] = toValues[4] = scale;
        toValues[2] = ((getWidth() / 2.0f) - px * scale);
        toValues[5] = ((getHeight() / 2.0f) - py * scale);

        this.ignoreTouch = true;
        this.startMatrixAnimationWithConstraints(toValues, duration);

        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        float[] m = this.getImageMatrixValues();
        float vx = (velocityX) / 10;
        float vy = (velocityY) / 10;
        float v = (float) Math.sqrt(vx * vx + vy * vy);
        if (v > m[0]) {
            m[2] += vx;
            m[5] += vy;
            this.interpolator = new DecelerateInterpolator();
            this.startMatrixAnimationWithConstraints(m, 300);
        }
        return true;
    }

}
