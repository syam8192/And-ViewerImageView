package jp.syam8192.viewerimageview;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.io.InputStream;

public class MainActivity extends Activity {

    private static final int REQUEST_GALLERY = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, REQUEST_GALLERY);

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trim();
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK) {
            try {
                InputStream in = getContentResolver().openInputStream(data.getData());
                Bitmap img = BitmapFactory.decodeStream(in);
                in.close();

                final ViewerImageView viewer = (ViewerImageView) findViewById(R.id.viewerView);
                final View frameView = findViewById(R.id.imageView);
                final RelativeLayout.LayoutParams viewerLayout = (RelativeLayout.LayoutParams)viewer.getLayoutParams();
                final RelativeLayout.LayoutParams frameLayout = (RelativeLayout.LayoutParams)viewer.getLayoutParams();

                viewer.setImageBitmap(img);
                viewer.setScrollInsets(44-5, 60-5, 44-5, 44-5);

                viewer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        viewer.minimumZoomScale = viewer.getZoomScaleCrop();
                        viewer.maximumZoomScale = viewer.getZoomScaleCrop() * 2;
                        viewer.setCenter(viewer.getZoomScaleCrop(), 0);
                        if (Build.VERSION.SDK_INT >= 16) {
                            viewer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        } else {
                            viewer.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void trim() {
        final ViewerImageView viewer = (ViewerImageView) findViewById(R.id.viewerView);

        final View frameView = findViewById(R.id.imageView);

        Bitmap bmp =  viewer.getClippedBitmap(0);

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bmp);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setAdjustViewBounds(true);
        Dialog dialog = new Dialog(this);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(imageView);
        dialog.show();

    }

}
