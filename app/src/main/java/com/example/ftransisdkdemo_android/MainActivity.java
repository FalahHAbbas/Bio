package com.example.ftransisdkdemo_android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;
import com.futronictech.AnsiSDKLib;
import com.futronictech.UsbDeviceDataExchangeImpl;
import com.telpo.tps550.api.fingerprint.FingerPrint;
import java.util.Locale;

public class MainActivity extends Activity {

  public static final int MESSAGE_SHOW_MSG = 1;
  public static final int MESSAGE_SHOW_IMAGE = 2;
  public static final int MESSAGE_SHOW_ERROR_MSG = 3;
  public static final int MESSAGE_END_OPERATION = 4;
  private static final int OPERATION_CAPTURE = 1;

  private Button mButtonCapture;

  private Button mButtonStop;

  private ImageView mFingerImage;
  private static Bitmap mBitmapFP = null;


  private CheckBox mUsbHostMode;

  private int mPendingOperation = 0;

  private OperationThread mOperationThread = null;


  private UsbDeviceDataExchangeImpl usb_host_ctx = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    new OpenTask().execute();

    mFingerImage = findViewById(R.id.imageViewFinger);
    mButtonCapture = findViewById(R.id.buttonCapture);
    mButtonStop = findViewById(R.id.buttonStop);

    mUsbHostMode = findViewById(R.id.checkBoxUsbHost);

    ArrayAdapter<CharSequence> adapter1 = ArrayAdapter
        .createFromResource(this, R.array.FingerNameArray, android.R.layout.simple_spinner_item);
    adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    ArrayAdapter<CharSequence> adapter2 = ArrayAdapter
        .createFromResource(this, R.array.MatchScoreArray, android.R.layout.simple_spinner_item);
    adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    mUsbHostMode.setChecked(true);
    mButtonStop.setEnabled(false);

    usb_host_ctx = new UsbDeviceDataExchangeImpl(this, mHandler);

    mButtonCapture.setOnClickListener(v -> {
      if (mUsbHostMode.isChecked()) {
        if (usb_host_ctx.OpenDevice(0, true)) {
          StartCapture();
        } else {
          if (usb_host_ctx.IsPendingOpen()) {
            mPendingOperation = OPERATION_CAPTURE;
          } else {
            Toast.makeText(MainActivity.this,
                "Can not start capture operation.\nCan't open scanner device", Toast.LENGTH_SHORT)
                .show();
          }
        }
      } else {
        StartCapture();
      }
    });

    mButtonStop.setOnClickListener(v -> {
      if (mOperationThread != null) {
        mOperationThread.Cancel();
      }
    });

  }


  @SuppressLint("HandlerLeak")
  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MESSAGE_SHOW_MSG:
          String showMsg = (String) msg.obj;
          Toast.makeText(MainActivity.this, showMsg, Toast.LENGTH_SHORT).show();
          break;

        case MESSAGE_SHOW_ERROR_MSG:
          String showErr = (String) msg.obj;
          Toast.makeText(MainActivity.this, showErr, Toast.LENGTH_SHORT).show();

          break;

        case MESSAGE_SHOW_IMAGE:
          mFingerImage.setImageBitmap(mBitmapFP);
          break;
        case MESSAGE_END_OPERATION:
          EndOperation();
          break;

        case UsbDeviceDataExchangeImpl.MESSAGE_ALLOW_DEVICE: {
          if (usb_host_ctx.ValidateContext()) {
            if (mPendingOperation == OPERATION_CAPTURE) {
              StartCapture();
            }
          } else {
            Toast.makeText(MainActivity.this, "Can't open scanner device", Toast.LENGTH_SHORT)
                .show();
          }

          break;
        }

        case UsbDeviceDataExchangeImpl.MESSAGE_DENY_DEVICE: {
          Toast.makeText(MainActivity.this, "User deny scanner device", Toast.LENGTH_SHORT).show();
          break;
        }

      }
    }
  };

  private Bitmap CreateFingerBitmap(int imgWidth, int imgHeight, byte[] imgBytes) {
    int[] pixels = new int[imgWidth * imgHeight];
    for (int i = 0; i < imgWidth * imgHeight; i++) {
      pixels[i] = imgBytes[i];
    }

    Bitmap emptyBmp = Bitmap.createBitmap(pixels, imgWidth, imgHeight, Config.RGB_565);

    int width, height;
    height = emptyBmp.getHeight();
    width = emptyBmp.getWidth();

    Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
    Canvas c = new Canvas(result);
    Paint paint = new Paint();
    ColorMatrix cm = new ColorMatrix();
    cm.setSaturation(0);
    ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
    paint.setColorFilter(f);
    c.drawBitmap(emptyBmp, 0, 0, paint);

    return result;
  }

  private void StartCapture() {
    PrepareOperation();
    mOperationThread = new CaptureThread(mUsbHostMode.isChecked());
    mOperationThread.start();
  }

  private class OperationThread extends Thread {

    private boolean mCanceled = false;

    OperationThread() {

    }

    boolean IsCanceled() {
      return mCanceled;
    }

    void Cancel() {
      mCanceled = true;

      try {
        this.join();    //5sec timeout
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  private class CaptureThread extends OperationThread {

    private AnsiSDKLib ansi_lib;
    private boolean mUseUsbHost;

    CaptureThread(boolean useUsbHost) {
      ansi_lib = new AnsiSDKLib();
      mUseUsbHost = useUsbHost;
    }

    public void run() {
      boolean dev_open = false;

      try {
        if (mUseUsbHost) {
          if (!ansi_lib.OpenDeviceCtx(usb_host_ctx)) {
            mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage())
                .sendToTarget();
            mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
            return;
          }
        } else {
          if (!ansi_lib.OpenDevice(0)) {
            mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage())
                .sendToTarget();
            mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
            return;
          }
        }

        dev_open = true;

        if (!ansi_lib.FillImageSize()) {
          mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage())
              .sendToTarget();
          mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
          return;
        }

        byte[] img_buffer = new byte[ansi_lib.GetImageSize()];

        for (; !IsCanceled(); ) {
          long lT1 = SystemClock.uptimeMillis();
          if (ansi_lib.CaptureImage(img_buffer)) {
            long op_time = SystemClock.uptimeMillis() - lT1;
            String op_info = String.format(Locale.US, "Capture done. Time is %d(ms)", op_time);
            mHandler.obtainMessage(MESSAGE_SHOW_MSG, -1, -1, op_info).sendToTarget();

            mBitmapFP = CreateFingerBitmap(
                ansi_lib.GetImageWidth(),
                ansi_lib.GetImageHeight(),
                img_buffer);

            Log.e("mBitmapFP", mBitmapFP.getHeight() + "");
            mHandler.obtainMessage(MESSAGE_SHOW_IMAGE).sendToTarget();
            break;
          } else {
            int lastError = ansi_lib.GetErrorCode();

            if (lastError == AnsiSDKLib.FTR_ERROR_EMPTY_FRAME ||
                lastError == AnsiSDKLib.FTR_ERROR_NO_FRAME ||
                lastError == AnsiSDKLib.FTR_ERROR_MOVABLE_FINGER) {
              Thread.sleep(100);
            } else {
              String error = String
                  .format("Capture failed. Error: %s.", ansi_lib.GetErrorMessage());
              mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget();
              break;
            }
          }
        }
      } catch (Exception e) {
        mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, e.getMessage()).sendToTarget();
      }

      if (dev_open) {
        ansi_lib.CloseDevice();
      }

      mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
    }
  }

  private void EnableControls(boolean enable) {
    mButtonCapture.setEnabled(enable);
    mUsbHostMode.setEnabled(enable);
    mButtonStop.setEnabled(!enable);
  }

  private void PrepareOperation() {
    Toast.makeText(MainActivity.this, "Put finger on scanner", Toast.LENGTH_SHORT).show();
    EnableControls(false);
  }

  private void EndOperation() {
    EnableControls(true);
  }


  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (mOperationThread != null) {
      mOperationThread.Cancel();
    }

    if (usb_host_ctx != null) {
      usb_host_ctx.CloseDevice();
      usb_host_ctx.Destroy();
      usb_host_ctx = null;
    }
    new CloseTask().execute();
  }

  @SuppressLint("StaticFieldLeak")
  private class OpenTask extends AsyncTask<Void, Void, Void> {
    @Override
    protected Void doInBackground(Void... params) {
      try {
        FingerPrint.fingerPrintPower(1);
      } catch (Exception e) {
        e.printStackTrace();
      }
      return null;
    }
  }

  @SuppressLint("StaticFieldLeak")
  private class CloseTask extends AsyncTask<Void, Void, Void> {

    @Override
    protected Void doInBackground(Void... params) {
      FingerPrint.fingerPrintPower(0);
      return null;
    }
  }
}