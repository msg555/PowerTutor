/*
Copyright (C) 2011 The University of Michigan

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Please send inquiries to powertutor@umich.edu
*/

package edu.umich.PowerTutor.components;

import edu.umich.PowerTutor.PowerNotifications;
import edu.umich.PowerTutor.phone.PhoneConstants;
import edu.umich.PowerTutor.service.IterationData;
import edu.umich.PowerTutor.service.PowerData;
import edu.umich.PowerTutor.util.NativeLoader;
import edu.umich.PowerTutor.util.NotificationService;
import edu.umich.PowerTutor.util.Recycler;
import edu.umich.PowerTutor.util.SystemInfo;
import edu.umich.PowerTutor.util.ForegroundDetector;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.os.Process;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Random;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;

public class OLED extends PowerComponent {
  public static class OledData extends PowerData {
    private static Recycler<OledData> recycler = new Recycler<OledData>();

    public static OledData obtain() {
      OledData result = recycler.obtain();
      if(result != null) return result;
      return new OledData();
    }

    @Override
    public void recycle() {
      recycler.recycle(this);
    }

	  public int brightness;
    public double pixPower;
	  public boolean screenOn;
	
    private OledData() {
    }

    public void init() {
      this.screenOn = false;
    }

    public void init(int brightness, double pixPower) {
      screenOn = true;
      this.brightness = brightness;
      this.pixPower = pixPower;
    }
	
	  public void writeLogDataInfo(OutputStreamWriter out) throws IOException {
      out.write("OLED-brightness " + brightness + "\n");
      out.write("OLED-pix-power " + pixPower + "\n");
      out.write("OLED-screen-on " + screenOn + "\n");
    }
  }

	private static final String TAG = "OLED";
  private static final String[] BACKLIGHT_BRIGHTNESS_FILES = {
    "/sys/class/leds/lcd-backlight/brightness",
    "/sys/devices/virtual/leds/lcd-backlight/brightness",
    "/sys/devices/platform/trout-backlight.0/leds/lcd-backlight/brightness",
  };

  private Context context;
  private ForegroundDetector foregroundDetector;
  private BroadcastReceiver broadcastReceiver;
  private boolean screenOn;

  private File frameBufferFile;

  private int screenWidth;
  private int screenHeight;

  private static final int NUMBER_OF_SAMPLES = 500;
  private int[] samples;

  private String brightnessFile;

  /* Coefficients pre-computed for pix power calculations.
   */
  private double rcoef;
  private double gcoef;
  private double bcoef;
  private double modul_coef;

  public OLED(Context context, PhoneConstants constants) {
    this.context = context;
    screenOn = true;

    foregroundDetector = new ForegroundDetector((ActivityManager)
        context.getSystemService(context.ACTIVITY_SERVICE));
    broadcastReceiver = new BroadcastReceiver() {
      public void onReceive(Context context, Intent intent) {
        synchronized(this) {
          if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            screenOn = false;
          } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            screenOn = true;
          }
        }
      };
    };
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
    intentFilter.addAction(Intent.ACTION_SCREEN_ON);
    context.registerReceiver(broadcastReceiver, intentFilter);

    frameBufferFile = new File("/dev/fb0");
    if(!frameBufferFile.exists()) {
      frameBufferFile = new File("/dev/graphics/fb0");
    }
    if(frameBufferFile.exists()) try {
      /* Check if we already have permission to read the frame buffer. */
      boolean readOk = false;
      try {
        RandomAccessFile fin = new RandomAccessFile(frameBufferFile, "r");
        int b = fin.read();
        fin.close();
        readOk = true;
      } catch(IOException e) {
      }
      /* Don't have permission, try to change permission as root. */
      if(!readOk) {
        java.lang.Process p = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(p.getOutputStream());
        os.writeBytes("chown " + android.os.Process.myUid() +
                      " " + frameBufferFile.getAbsolutePath() + "\n");
        os.writeBytes("chown app_" + (android.os.Process.myUid() -
                      SystemInfo.AID_APP) +
                      " " + frameBufferFile.getAbsolutePath() + "\n");
        os.writeBytes("chmod 660 " + frameBufferFile.getAbsolutePath() + "\n");
        os.writeBytes("exit\n");
        os.flush();
        p.waitFor();
        if(p.exitValue() != 0) {
          Log.i(TAG, "failed to change permissions on frame buffer");
        }
      }
    } catch (InterruptedException e) {
      Log.i(TAG, "changing permissions on frame buffer interrupted");
    } catch (IOException e) {
      Log.i(TAG, "unexpected exception while changing permission on " +
            "frame buffer");
      e.printStackTrace();
    }

    DisplayMetrics metrics = new DisplayMetrics();
    WindowManager windowManager =
        (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
    windowManager.getDefaultDisplay().getMetrics(metrics);
    screenWidth = metrics.widthPixels;
    screenHeight = metrics.heightPixels;

    Random r = new Random();
    samples = new int[NUMBER_OF_SAMPLES];
    for(int i = 0; i < NUMBER_OF_SAMPLES; i++) {
      int a = screenWidth * screenHeight * i / NUMBER_OF_SAMPLES;
      int b = screenWidth * screenHeight * (i + 1) / NUMBER_OF_SAMPLES;
      samples[i] = a + r.nextInt(b - a);
    }

    double[] channel = constants.oledChannelPower();
    rcoef = channel[0] / 255 / 255;
    gcoef = channel[1] / 255 / 255;
    bcoef = channel[2] / 255 / 255;
    modul_coef = constants.oledModulation() / 255 / 255 / 3 / 3;

    for(int i = 0; i < BACKLIGHT_BRIGHTNESS_FILES.length; i++) {
      if(new File(BACKLIGHT_BRIGHTNESS_FILES[i]).exists()) {
        brightnessFile = BACKLIGHT_BRIGHTNESS_FILES[i];
      }
    }
  }

  @Override
  protected void onExit() {
    context.unregisterReceiver(broadcastReceiver);
    super.onExit();
  } 

  @Override
  public IterationData calculateIteration(long iteration) {
    IterationData result = IterationData.obtain();

    boolean screen;
    synchronized(this) {
      screen = screenOn;
    }

    int brightness;
    if(brightnessFile != null) {
      brightness = (int)SystemInfo.getInstance()
          .readLongFromFile(brightnessFile);
    } else {
      try {
        brightness = Settings.System.getInt(context.getContentResolver(),
                                            Settings.System.SCREEN_BRIGHTNESS);
      } catch(Settings.SettingNotFoundException ex) {
        Log.w(TAG, "Could not retrieve brightness information");
        return result;
      }
    }
    if(brightness < 0 || 255 < brightness) {
      Log.w(TAG, "Could not retrieve brightness information");
      return result;
    }

    double pixPower = 0;
    if(screen && frameBufferFile.exists()) {
      if(NativeLoader.jniLoaded()) {
        pixPower = getScreenPixPower(rcoef, gcoef, bcoef, modul_coef);
      } else try {
        RandomAccessFile fin = new RandomAccessFile(frameBufferFile, "r");

        for(int x : samples) {
          fin.seek(x * 4);
          int px = fin.readInt();
          int b = px >> 8 & 0xFF;
          int g = px >> 16 & 0xFF;
          int r = px >> 24 & 0xFF;

          /* Calculate the power usage of this one pixel if it were at full
           * brightness.  Linearly scale by brightness to get true power
           * consumption.  To calculate whole screen compute average of sampled
           * region and multiply by number of pixels.
           */
          int modul_val = r + g + b;
          pixPower += rcoef * (r * r) + gcoef * (g * g) + bcoef * (b * b) -
                      modul_coef * (modul_val * modul_val);
        }
        fin.close();
      } catch(FileNotFoundException e) {
        pixPower = -1;
      } catch(IOException e) {
        pixPower = -1;
        e.printStackTrace();
      }
      if(pixPower >= 0) {
        pixPower *= 1.0 * screenWidth * screenHeight / NUMBER_OF_SAMPLES;
      }
    }

    OledData data = OledData.obtain();
    if(!screen) {
      data.init();
    } else {
      data.init(brightness, pixPower);
    }
    result.setPowerData(data);

    if(screen) {
      OledData uidData = OledData.obtain();
      uidData.init(brightness, pixPower);
      result.addUidPowerData(foregroundDetector.getForegroundUid(), uidData);
    }

    return result;
  }

  @Override
  public boolean hasUidInformation() {
    return true;
  }

  @Override
  public String getComponentName() {
    return "OLED";
  }

  public static native double getScreenPixPower(double rcoef, double gcoef,
                                            double bcoef, double modul_coef);
}
