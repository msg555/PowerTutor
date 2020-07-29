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

package edu.umich.PowerTutor.service;

import edu.umich.PowerTutor.components.PowerComponent;
import edu.umich.PowerTutor.components.OLED;
import edu.umich.PowerTutor.phone.PhoneSelector;
import edu.umich.PowerTutor.phone.PhoneConstants;
import edu.umich.PowerTutor.phone.PowerFunction;
import edu.umich.PowerTutor.util.BatteryStats;
import edu.umich.PowerTutor.util.Counter;
import edu.umich.PowerTutor.util.HistoryBuffer;
import edu.umich.PowerTutor.util.NotificationService;
import edu.umich.PowerTutor.util.SystemInfo;
import edu.umich.PowerTutor.widget.PowerWidget;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** This class is responsible for starting the individual power component
 *  loggers (CPU, GPS, etc...) and collecting the information they generate.
 *  This information is used both to write a log file that will be send back
 *  to spidermoneky (or looked at by the user) and to implement the
 *  ICounterService IPC interface.
 */
public class PowerEstimator implements Runnable {
  private static final String TAG = "PowerEstimator";

  /* A dictionary used to assist in compression of the log files.  Strings that
   * appear more frequently should be put towards the end of the dictionary. It
   * is not critical that every string that be written to the log appear here.
   */
  private static final String DEFLATE_DICTIONARY =
      "onoffidleoff-hookringinglowairplane-modebatteryedgeGPRS3Gunknown" +
      "in-serviceemergency-onlyout-of-servicepower-offdisconnectedconnecting" +
      "associateconnectedsuspendedphone-callservicenetworkbegin.0123456789" +
      "GPSAudioWifi3GLCDCPU-power ";

  public static final int ALL_COMPONENTS = -1;
  public static final int ITERATION_INTERVAL = 1000; // 1 second

  private UMLoggerService context;
  private SharedPreferences prefs;
  private boolean plugged;

  private Vector<PowerComponent> powerComponents;
  private Vector<PowerFunction> powerFunctions;
  private Vector<HistoryBuffer> histories;
  private Map<Integer, String> uidAppIds;

  // Miscellaneous data.
  private HistoryBuffer oledScoreHistory;

  private Object fileWriteLock = new Object();
  private LogUploader logUploader;
  private OutputStreamWriter logStream;
  private DeflaterOutputStream deflateStream;
  
  private Object iterationLock = new Object();
  private long lastWrittenIteration;

  public PowerEstimator(UMLoggerService context){
    this.context = context;
    prefs = PreferenceManager.getDefaultSharedPreferences(context);
    powerComponents = new Vector<PowerComponent>();
    powerFunctions = new Vector<PowerFunction>();
    uidAppIds = new HashMap<Integer, String>();
    PhoneSelector.generateComponents(context, powerComponents, powerFunctions);

    histories = new Vector<HistoryBuffer>();
    for(int i = 0; i < powerComponents.size(); i++) {
      histories.add(new HistoryBuffer(300));
    }
    oledScoreHistory = new HistoryBuffer(0);

    logUploader = new LogUploader(context);
    openLog(true);
  }

  private void openLog(boolean init) {
    /* Open up the log file if possible. */
    try {
      String logFilename = context.getFileStreamPath(
                        "PowerTrace.log").getAbsolutePath();
      if(init && prefs.getBoolean("sendPermission", true) &&
         new File(logFilename).length() > 0) {
        /* There is data to send.  Make sure that gets going in the sending
         * process before we write over any old logs.
         */
        logUploader.upload(logFilename);
      }
      Deflater deflater = new Deflater();
      deflater.setDictionary(DEFLATE_DICTIONARY.getBytes());
      deflateStream = new DeflaterOutputStream(
                          new FileOutputStream(logFilename));
      logStream = new OutputStreamWriter(deflateStream);
    } catch(IOException e) {
      logStream = null;
      Log.e(TAG, "Failed to open log file.  No log will be kept.");
    }    
  }
  
  /** This is the loop that keeps updating the power profile
   */
  public void run() {
    SystemInfo sysInfo = SystemInfo.getInstance();
    PackageManager pm = context.getPackageManager();
    BatteryStats bst = BatteryStats.getInstance();

    int components = powerComponents.size();
    long beginTime = SystemClock.elapsedRealtime();
    for(int i = 0; i < components; i++) {
      powerComponents.get(i).init(beginTime, ITERATION_INTERVAL);
      powerComponents.get(i).start();
    }
    IterationData[] dataTemp = new IterationData[components];

    PhoneConstants phoneConstants = PhoneSelector.getConstants(context);
    long[] memInfo = new long[4];

    int oledId = -1;
    for(int i = 0; i < components; i++) {
      if("OLED".equals(powerComponents.get(i).getComponentName())) {
        oledId = i;
        break;
      }
    }

    double lastCurrent = -1;

    /* Indefinitely collect data on each of the power components. */
    boolean firstLogIteration = true;
    for(long iter = -1; !Thread.interrupted(); ) {
      long curTime = SystemClock.elapsedRealtime();
      /* Compute the next iteration that we can make the ending of.  We wait
         for the end of the iteration so that the components had a chance to
         collect data already.
       */
      iter = (long)Math.max(iter + 1,
                            (curTime - beginTime) / ITERATION_INTERVAL);
      /* Sleep until the next iteration completes. */
      try {
        Thread.currentThread().sleep(
            beginTime + (iter + 1) * ITERATION_INTERVAL - curTime);
      } catch(InterruptedException e) {
        break;
      }

      int totalPower = 0;
      for(int i = 0; i < components; i++) {
        PowerComponent comp = powerComponents.get(i);
        IterationData data = comp.getData(iter);
        dataTemp[i] = data;
        if(data == null) {
          /* No data present for this timestamp.  No power charged.
           */
          continue;
        }
        
        SparseArray<PowerData> uidPower = data.getUidPowerData();
        for(int j = 0; j < uidPower.size(); j++) {
          int uid = uidPower.keyAt(j);
          PowerData powerData = uidPower.valueAt(j);
          int power = (int)powerFunctions.get(i).calculate(powerData);
          powerData.setCachedPower(power);
          histories.get(i).add(uid, iter, power);
          if(uid == SystemInfo.AID_ALL) {
            totalPower += power;
          }
          if(i == oledId) {
            OLED.OledData oledData = (OLED.OledData)powerData;
            if(oledData.pixPower >= 0) {
              oledScoreHistory.add(uid, iter, (int)(1000 * oledData.pixPower));
            }
          }
        }
      }

      /* Update the uid set. */
      synchronized(fileWriteLock) { synchronized(uidAppIds) {
        for(int i = 0; i < components; i++) {
          IterationData data = dataTemp[i];
          if(data == null) {
            continue;
          }
          SparseArray<PowerData> uidPower = data.getUidPowerData();
          for(int j = 0; j < uidPower.size(); j++) {
            int uid = uidPower.keyAt(j);
            if(uid < SystemInfo.AID_APP) {
              uidAppIds.put(uid, null);
            } else  {
              /* We only want to update app names when logging so the associcate
               * message gets written.
               */
              String appId = uidAppIds.get(uid);
              String newAppId = sysInfo.getAppId(uid, pm);
              if(!firstLogIteration && logStream != null &&
                 (appId == null || !appId.equals(newAppId))) {
                try {
                  logStream.write("associate " + uid + " " + newAppId + "\n");
                } catch(IOException e) {
                  Log.w(TAG, "Failed to write to log file");
                }
              }
              uidAppIds.put(uid, newAppId);
            }
          }
        }
      }}

      synchronized(iterationLock) {
        lastWrittenIteration = iter;
      }

      /* Update the icon display every 15 iterations. */
      if(iter % 15 == 14) {
        final double POLY_WEIGHT = 0.02;
        int count = 0;
        int[] history = getComponentHistory(5 * 60, -1,
                                            SystemInfo.AID_ALL, -1);
        double weightedAvgPower = 0;
        for(int i = history.length - 1; i >= 0; i--) {
          if(history[i] != 0) {
            count++;
            weightedAvgPower *= 1.0 - POLY_WEIGHT;
            weightedAvgPower += POLY_WEIGHT * history[i] / 1000.0;
          }
        }
        double avgPower = -1;
        if(count != 0) {
          avgPower = weightedAvgPower /
                         (1.0 - Math.pow(1.0 - POLY_WEIGHT, count));
        }
        avgPower *= 1000;

        context.updateNotification((int)Math.min(8, 1 +
                                   8 * avgPower / phoneConstants.maxPower()),
                                   avgPower);
      }

      /* Update the widget. */
      if(iter % 60 == 0) {
        PowerWidget.updateWidget(context, this);
      }

      if(bst.hasCurrent()) {
        double current = bst.getCurrent();
        if(current != lastCurrent) {
          writeToLog("batt_current " + current + "\n");
          lastCurrent = current;
        }
      }
      if(iter % (5*60) == 0) {
        if(bst.hasTemp()) {
          writeToLog("batt_temp " + bst.getTemp() + "\n");
        }
        if(bst.hasCharge()) {
          writeToLog("batt_charge " + bst.getCharge() + "\n");
        }
      }
      if(iter % (30*60) == 0) {
        if(Settings.System.getInt(context.getContentResolver(),
                                  "screen_brightness_mode", 0) != 0) {
          writeToLog("setting_brightness automatic\n");
        } else {
          int brightness = Settings.System.getInt(
                                context.getContentResolver(),
                                Settings.System.SCREEN_BRIGHTNESS, -1);
          if(brightness != -1) {
            writeToLog("setting_brightness " + brightness + "\n");
          }
        }
        int timeout = Settings.System.getInt(
                            context.getContentResolver(),
                            Settings.System.SCREEN_OFF_TIMEOUT, -1);
        if(timeout != -1) {
          writeToLog("setting_screen_timeout " + timeout + "\n");
        }
        String httpProxy = Settings.Secure.getString(
                                context.getContentResolver(),
                                Settings.Secure.HTTP_PROXY);
        if(httpProxy != null) {
          writeToLog("setting_httpproxy " + httpProxy + "\n");
        }
      }

      /* Let's only grab memory information every 10 seconds to try to keep log
       * file size down and the notice_data table size down.
       */
      boolean hasMem = false;
      if(iter % 10 == 0) {
        hasMem = sysInfo.getMemInfo(memInfo);
      }

      synchronized(fileWriteLock) {
        if(logStream != null) try {
          if(firstLogIteration) {
            firstLogIteration = false;
            logStream.write("time " + System.currentTimeMillis() + "\n");
            Calendar cal = new GregorianCalendar();
            logStream.write("localtime_offset " +
                            (cal.get(Calendar.ZONE_OFFSET) +
                             cal.get(Calendar.DST_OFFSET)) + "\n");
            logStream.write("model " + phoneConstants.modelName() + "\n");
            if(NotificationService.available()) {
              logStream.write("notifications-active\n");
            }
            if(bst.hasFullCapacity()) {
              logStream.write("batt_full_capacity " + bst.getFullCapacity()
                              + "\n");
            }
            synchronized(uidAppIds) {
              for(int uid : uidAppIds.keySet()) {
                if(uid < SystemInfo.AID_APP) {
                  continue;
                }
                logStream.write("associate " + uid + " " + uidAppIds.get(uid)
                                + "\n");
              }
            }
          }
          logStream.write("begin " + iter + "\n");
          logStream.write("total-power " + (long)Math.round(totalPower) + '\n');
          if(hasMem) {
            logStream.write("meminfo " + memInfo[0] + " " + memInfo[1] +
                            " " + memInfo[2] + " " + memInfo[3] + "\n");
          }
          for(int i = 0; i < components; i++) {
            IterationData data = dataTemp[i];
            if(data != null) {
              String name = powerComponents.get(i).getComponentName();
              SparseArray<PowerData> uidData = data.getUidPowerData();
              for(int j = 0; j < uidData.size(); j++) {
                int uid = uidData.keyAt(j);
                PowerData powerData = uidData.valueAt(j);
                if(uid == SystemInfo.AID_ALL) {
                  logStream.write(name + " " + (long)Math.round(
                      powerData.getCachedPower()) + "\n");
                  powerData.writeLogDataInfo(logStream);
                } else {
                  logStream.write(name + "-" + uid + " " + (long)Math.round(
                                  powerData.getCachedPower()) + "\n");
                }
              }
              data.recycle();
            }
          }
        } catch(IOException e) {
          Log.w(TAG, "Failed to write to log file");
        }

        if(iter % 15 == 0 && prefs.getBoolean("sendPermission", true)) {
          /* Allow for LogUploader to decide if the log needs to be uploaded and
           * begin uploading if it decides it's necessary.
           */
          if(logUploader.shouldUpload()) {
            try {
              logStream.close();
            } catch(IOException e) {
              Log.w(TAG, "Failed to flush and close log stream");
            }
            logStream = null;
            logUploader.upload(context.getFileStreamPath(
                               "PowerTrace.log").getAbsolutePath());
            openLog(false);
            firstLogIteration = true;
          }
        }
      }
    }

    /* Blank the widget's display and turn off power button. */
    PowerWidget.updateWidgetDone(context);

    /* Have all of the power component threads exit. */
    logUploader.interrupt();
    for(int i = 0; i < components; i++) {
      powerComponents.get(i).interrupt();
    }
    try {
      logUploader.join();
    } catch(InterruptedException e) {
    }
    for(int i = 0; i < components; i++) {
      try {
        powerComponents.get(i).join();
      } catch(InterruptedException e) {
      }
    }

    /* Close the logstream so that everything gets flushed and written to file
     * before we have to quit.
     */
    synchronized(fileWriteLock) {
      if(logStream != null) try {
        logStream.close();
      } catch(IOException e) {
        Log.w(TAG, "Failed to flush log file on exit");
      }
    }
  }
  
  public void plug(boolean plugged) {
    logUploader.plug(plugged);
  }

  public void writeToLog(String m) {
    synchronized(fileWriteLock) {
      if(logStream != null) try {
        logStream.write(m);
      } catch(IOException e) {
        Log.w(TAG, "Failed to write message to power log");
      }
    }
  }

  public String[] getComponents() {
    int components = powerComponents.size();
    String[] ret = new String[components];
    for(int i = 0; i < components; i++) {
      ret[i] = powerComponents.get(i).getComponentName();
    }
    return ret;
  }

  public int[] getComponentsMaxPower() {
    PhoneConstants constants = PhoneSelector.getConstants(context);
    int components = powerComponents.size();
    int[] ret = new int[components];
    for(int i = 0; i < components; i++) {
      ret[i] = (int)constants.getMaxPower(
          powerComponents.get(i).getComponentName());
    }
    return ret;
  }

  public int getNoUidMask() {
    int components = powerComponents.size();
    int ret = 0;
    for(int i = 0; i < components; i++) {
      if(!powerComponents.get(i).hasUidInformation()) {
        ret |= 1 << i;
      }
    }
    return ret;
  }

  public int[] getComponentHistory(int count, int componentId, int uid,
                                   long iteration) {
    if(iteration == -1) synchronized(iterationLock) {
      iteration = lastWrittenIteration;
    }
    int components = powerComponents.size();
    if(componentId == ALL_COMPONENTS) {
      int[] result = new int[count];
      for(int i = 0; i < components; i++) {
        int[] comp = histories.get(i).get(uid, iteration, count);
        for(int j = 0; j < count; j++) {
          result[j] += comp[j];
        }
      }
      return result;
    }
    if(componentId < 0 || components <= componentId) return null;
    return histories.get(componentId).get(uid, iteration, count);
  }

  public long[] getTotals(int uid, int windowType) {
    int components = powerComponents.size();
    long[] ret = new long[components];
    for(int i = 0; i < components; i++) {
      ret[i] = histories.get(i).getTotal(uid, windowType) *
               ITERATION_INTERVAL / 1000;
    }
    return ret;
  }
  
  public long getRuntime(int uid, int windowType) {
    long runningTime = 0;
    int components = powerComponents.size();
    for(int i = 0; i < components; i++) {
      long entries = histories.get(i).getCount(uid, windowType);
      runningTime = entries > runningTime ? entries : runningTime;
    }
    return runningTime * ITERATION_INTERVAL / 1000;
  }

  public long[] getMeans(int uid, int windowType) {
    long[] ret = getTotals(uid, windowType);
    long runningTime = getRuntime(uid, windowType);
    runningTime = runningTime == 0 ? 1 : runningTime;
    for(int i = 0; i < ret.length; i++) {
      ret[i] /= runningTime;
    }
    return ret;
  }

  public UidInfo[] getUidInfo(int windowType, int ignoreMask) {
    long iteration;
    synchronized(iterationLock) {
      iteration = lastWrittenIteration;
    }
    int components = powerComponents.size();
    synchronized(uidAppIds) {
      int pos = 0;
      UidInfo[] result = new UidInfo[uidAppIds.size()];
      for(Integer uid : uidAppIds.keySet()) {
        UidInfo info = UidInfo.obtain();
        int currentPower = 0;
        for(int i = 0; i < components; i++) {
          if((ignoreMask & 1 << i) == 0) {
            currentPower += histories.get(i).get(uid, iteration, 1)[0];
          }
        }
        double scale = ITERATION_INTERVAL / 1000.0;
        info.init(uid, currentPower,
            sumArray(getTotals(uid, windowType), ignoreMask) *
            ITERATION_INTERVAL / 1000,
            getRuntime(uid, windowType) * ITERATION_INTERVAL / 1000);
        result[pos++] = info;
      }
      return result;
    }
  }

  private long sumArray(long[] A, int ignoreMask) {
    long ret = 0;
    for(int i = 0; i < A.length; i++) {
      if((ignoreMask & 1 << i) == 0) {
        ret += A[i];
      }
    }
    return ret;
  }

  public long getUidExtra(String name, int uid) {
    if("OLEDSCORE".equals(name)) {
      long entries = oledScoreHistory.getCount(uid, Counter.WINDOW_TOTAL);
      if(entries <= 0) return -2;
      double result = oledScoreHistory.getTotal(uid, Counter.WINDOW_TOTAL) /
                      1000.0;
      result /= entries;
      PhoneConstants phoneConstants = PhoneSelector.getConstants(context);
      result *= 255 / (phoneConstants.getMaxPower("OLED") -
                       phoneConstants.oledBasePower());
      return (long)Math.round(result * 100);
    }
    return -1;
  }
}

