package edu.umich.PowerTutor.service;

import edu.umich.PowerTutor.ui.UMLogger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;

/* This class is responsible for all of the policy decisions on when to actually
 * send log information back to our log collecting servers and is also
 * responsible for actually sending the data should it decide that it is
 * appropriate.
 */
public class LogUploader {
  private static final String TAG = "LogUploader";

  public static final String UPLOAD_FILE = "PowerTrace_Upload.log";

  private static final long NONE_LOG_LENGTH = 1 << 20; // 1 MiB
  private static final long WIFI_LOG_LENGTH = 1 << 17; // 128 KiB
  private static final long THREEG_LOG_LENGTH = 1 << 19; // 512 KiB

  private static final int CONNECTION_NONE = 0;
  private static final int CONNECTION_WIFI = 1;
  private static final int CONNECTION_3G = 2;

  private boolean plugged;

  private File logFile;
  private ConnectivityManager connectivityManager;
  private TelephonyManager telephonyManager;

  private Thread uploadThread;

  public LogUploader(Context context) {
    telephonyManager = (TelephonyManager)context.getSystemService(
                                             Context.TELEPHONY_SERVICE); 
    connectivityManager = (ConnectivityManager)context.getSystemService(
                                                 Context.CONNECTIVITY_SERVICE);
    logFile = context.getFileStreamPath(UPLOAD_FILE);
  }

  public synchronized boolean shouldUpload() {
    switch(connectionAvailable()) {
      case CONNECTION_WIFI:
        return plugged && logFile.length() > WIFI_LOG_LENGTH;
      case CONNECTION_3G:
        return plugged && logFile.length() > THREEG_LOG_LENGTH;
      default: // CONNECTION_NONE
        return logFile.length() > NONE_LOG_LENGTH;
    }
  }

  public synchronized void plug(boolean plugged) {
    this.plugged = plugged;
  }

  private int connectionAvailable() {
    /* TODO: Maybe we should only send data when the device is plugged in.
     */
    NetworkInfo info = connectivityManager.getActiveNetworkInfo();
    if(info == null || !connectivityManager.getBackgroundDataSetting()) {
      return CONNECTION_NONE;
    }
    int netType = info.getType();
    int netSubtype = info.getSubtype();
    if (netType == ConnectivityManager.TYPE_WIFI) {
      return info.isConnected() ? CONNECTION_WIFI : CONNECTION_NONE;
    } else if (netType == ConnectivityManager.TYPE_MOBILE
        && netSubtype == TelephonyManager.NETWORK_TYPE_UMTS
        && !telephonyManager.isNetworkRoaming()) {
      return info.isConnected() ? CONNECTION_3G : CONNECTION_NONE;
    }
    return CONNECTION_NONE;
  }

  public void upload(String origFile) {
    if(new File(origFile).renameTo(logFile)) {
      interrupt();
      uploadThread = new Thread() {
        public void run() {
          long runID = System.currentTimeMillis();
          for(int iter = 1; !interrupted(); iter++) {
            if(send(runID)) {
              break;
            }
            if(iter > 12) iter = 12; // The max wait is a little over 1 hour.
            Log.i(TAG, "Failed to send log.  Will try again in " + (1 << iter) +
                       " seconds");
            try {
              do {
                sleep(1000 * (1 << iter)); // Sleep for 2^iter seconds.
              } while(connectionAvailable() == CONNECTION_NONE);
            } catch(InterruptedException e) {
              break;
            }
          }
        }
      };
      uploadThread.start();
    } else {
      Log.w(TAG, "Failed to move log file before sending");
    }
  }

  public boolean isUploading() {
    return uploadThread != null && uploadThread.isAlive();
  }

  public void interrupt() {
    if(uploadThread != null) {
      uploadThread.interrupt();
    }
  }

  public void join() throws InterruptedException {
    if(uploadThread != null) {
      uploadThread.join();
    }
  }

  public boolean send(long runID) {
    Log.i(TAG, "Sending log data");
    Socket s = new Socket();
    try {
      s.setSoTimeout(4000);
      s.connect(new InetSocketAddress(UMLogger.SERVER_IP, UMLogger.SERVER_PORT),
                15000);
    } catch(IOException e) {
      /* Failed to connect to server.  Try again later.
       */
      return false;
    }

    try {
      BufferedInputStream in = new BufferedInputStream(
                                    new FileInputStream(logFile), 1024);
      BufferedOutputStream sockOut = new BufferedOutputStream(
                                          s.getOutputStream(), 1024);

      /* Write the prefix string to the server. */
      sockOut.write(getPrefix(runID, logFile.length()));
      sockOut.write(0);

      /* Write the log file to the server. */
      byte[] buf = new byte[1024];
      while(true) {
        int sz = in.read(buf, 0, buf.length);
        if(sz == -1) break;
        sockOut.write(buf, 0, sz);
      }
      sockOut.flush();
      int response = s.getInputStream().read();
      in.close();
      s.close();

      if(response != 0) {
        Log.w(TAG, "Log data not accepted by server");
      }
    } catch(SocketTimeoutException e) {
      /* Connection trouble with server.  Try again later.
       */
      return false;
    } catch(IOException e) {
      Log.w(TAG, "Unexpected exception sending log.  Dropping log data");
      e.printStackTrace();
    }
    logFile.delete();
    return true;
  }

  private byte[] getPrefix(long runID, long payloadLength) {
    String deviceID = telephonyManager.getDeviceId();
    return (UMLogger.CURRENT_VERSION + '|' + sanatize(Build.DEVICE) + '|' +
           getMD5(deviceID) + "|" + payloadLength).getBytes();
  }

  /* Just strip out any | characters present.  Normal DEVICE strings shouldn't
   * have a | but this string can be set by anyone so we should treat it as
   * adversarial.
   */
  private String sanatize(String s) {
    StringBuffer buf = new StringBuffer();
    for(int i = 0; i < s.length(); i++) {
      if(s.charAt(i) != '|') {
        buf.append(s.charAt(i));
      }
    }
    return buf.toString();
  }

  private String getMD5(String s){
    MessageDigest m = null;
    try {
      m = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // Well this sucks...
      e.printStackTrace();
      return "nohash";
    }
    m.update(s.getBytes(), 0, s.length());
    return new BigInteger(1, m.digest()).toString(16);
  }
}
