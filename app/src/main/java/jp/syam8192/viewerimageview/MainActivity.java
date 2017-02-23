package jp.syam8192.viewerimageview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewTreeObserver;

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
                viewer.setImageBitmap(img);

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

}
