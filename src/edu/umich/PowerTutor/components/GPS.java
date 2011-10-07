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
import edu.umich.PowerTutor.util.NotificationService;
import edu.umich.PowerTutor.util.Recycler;
import edu.umich.PowerTutor.util.SystemInfo;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

public class GPS extends PowerComponent {
  public static class GpsData extends PowerData {
    private static Recycler<GpsData> recycler = new Recycler<GpsData>();

    public static GpsData obtain() {
      GpsData result = recycler.obtain();
      if(result != null) return result;
      return new GpsData();
    }

    /* The time in seconds since the last iteration of data. */
    public double[] stateTimes;
    /* The number of satellites.  This number is only available while the GPS is
     * in the on state.  Otherwise it is 0.
     */
    public int satellites;

    private GpsData() {
      stateTimes = new double[GPS.POWER_STATES];
    }

    public void init(double[] stateTimes, int satellites) {
      for(int i = 0; i < GPS.POWER_STATES; i++) {
        this.stateTimes[i] = stateTimes[i];
      }
      this.satellites = satellites;
    }

    @Override
    public void recycle() {
      recycler.recycle(this);
    }

    @Override
    public void writeLogDataInfo(OutputStreamWriter out) throws IOException {
      StringBuilder res = new StringBuilder();
      res.append("GPS-state-times");
      for(int i = 0; i < GPS.POWER_STATES; i++) {
        res.append(" ").append(stateTimes[i]);
      }
      res.append("\nGPS-sattelites ").append(satellites).append("\n");
      out.write(res.toString());
    }
  }

  public static final int POWER_STATES = 3;
  public static final int POWER_STATE_OFF = 0;
  public static final int POWER_STATE_SLEEP = 1;
  public static final int POWER_STATE_ON = 2;
  public static final String[] POWER_STATE_NAMES = {"OFF", "SLEEP", "ON"};

  private static final String TAG = "GPS";

  private static final int HOOK_LIBGPS = 1;
  private static final int HOOK_GPS_STATUS_LISTENER = 2;
  private static final int HOOK_NOTIFICATIONS = 4;
  private static final int HOOK_TIMER = 8;

  /* A named pipe written to by the hacked libgps library. */
  private static String HOOK_GPS_STATUS_FILE = "/data/misc/gps.status";

  private GpsStatus.Listener gpsListener;
  private Thread statusThread;
  private PowerNotifications notificationReceiver;

  private Context context;
  private LocationManager locationManager;
  private GpsStatus lastStatus;
  private boolean hasUidInfo;
  private long sleepTime;
  private long lastTime;

  private GpsStateKeeper gpsState;
  private SparseArray<GpsStateKeeper> uidStates;

  private static final int GPS_STATUS_SESSION_BEGIN = 1;
  private static final int GPS_STATUS_SESSION_END = 2;
  private static final int GPS_STATUS_ENGINE_ON = 3;
  private static final int GPS_STATUS_ENGINE_OFF = 4;

  public GPS(Context context, PhoneConstants constants) {
    this.context = context;
    uidStates = new SparseArray<GpsStateKeeper>();
    sleepTime = (long)Math.round(1000.0 * constants.gpsSleepTime());

    hasUidInfo = NotificationService.available();

    int hookMethod = 0;
    final File gpsStatusFile = new File(HOOK_GPS_STATUS_FILE);
    if(gpsStatusFile.exists()) {
      /* The libgps hack appears to be available.  Let's use this to gather
       * our status updates from the GPS.
       */
      hookMethod = HOOK_LIBGPS;
    } else {
      /* We can always use the status listener hook and perhaps the notification
       * hook if we are running eclaire or higher and the notification hook
       * is installed.  We can only do this on eclaire or higher because it
       * wasn't until eclaire that they fixed a bug where they didn't maintain
       * a wakelock while the gps engine was on.
       */
      hookMethod = HOOK_GPS_STATUS_LISTENER;
      try {
        if(NotificationService.available() &&
           Integer.parseInt(Build.VERSION.SDK) >= 5 /* eclaire or higher */) {
          hookMethod |= HOOK_NOTIFICATIONS;
        }
      } catch(NumberFormatException e) {
        Log.w(TAG, "Could not parse sdk version: " + Build.VERSION.SDK);
      }
    }
    /* If we don't have a way of getting the off<->sleep transitions through
     * notifications let's just use a timer and simulat the state of the gps
     * instead.
     */
    if((hookMethod & (HOOK_LIBGPS | HOOK_NOTIFICATIONS)) == 0) {
      hookMethod |= HOOK_TIMER;
    }

    /* Create the object that keeps track of the physical GPS state. */
    gpsState = new GpsStateKeeper(hookMethod, sleepTime);

    /* No matter what we are going to register a GpsStatus listener so that we
     * can get the satellite count.  Also if anything goes wrong with the
     * libgps hook we will revert to using this.
     */
    locationManager = (LocationManager)
                      context.getSystemService(Context.LOCATION_SERVICE);
    gpsListener = new GpsStatus.Listener() {
      public void onGpsStatusChanged(int event){
        if(event == GpsStatus.GPS_EVENT_STARTED) {
          gpsState.updateEvent(GPS_STATUS_SESSION_BEGIN,
                               HOOK_GPS_STATUS_LISTENER);
        } else if(event == GpsStatus.GPS_EVENT_STOPPED) {
          gpsState.updateEvent(GPS_STATUS_SESSION_END,
                               HOOK_GPS_STATUS_LISTENER);
        }
        synchronized(GPS.this) {
          lastStatus = locationManager.getGpsStatus(lastStatus);
        }
      }
    };
    locationManager.addGpsStatusListener(gpsListener);

    /* No matter what we register a notification service listener as well so
     * that we can get uid information if it's available.
     */
    if(hasUidInfo) {
      notificationReceiver = new NotificationService.DefaultReceiver() {
        public void noteStartWakelock(int uid, String name, int type) {
          if(uid == SystemInfo.AID_SYSTEM &&
             "GpsLocationProvider".equals(name)) {
            gpsState.updateEvent(GPS_STATUS_ENGINE_ON, HOOK_NOTIFICATIONS);
          }
        }

        public void noteStopWakelock(int uid, String name, int type) {
          if(uid == SystemInfo.AID_SYSTEM &&
             "GpsLocationProvider".equals(name)) {
            gpsState.updateEvent(GPS_STATUS_ENGINE_OFF, HOOK_NOTIFICATIONS);
          }
        }

        public void noteStartGps(int uid) {
          updateUidEvent(uid, GPS_STATUS_SESSION_BEGIN, HOOK_NOTIFICATIONS);
        }

        public void noteStopGps(int uid) {
          updateUidEvent(uid, GPS_STATUS_SESSION_END, HOOK_NOTIFICATIONS);
        }
      };
      NotificationService.addHook(notificationReceiver);
    }

    if(gpsStatusFile.exists()) {
      /* Start a thread to read from the named pipe and feed us status updates.
       */
      statusThread = new Thread() {
        public void run() {
          try {
            java.io.FileInputStream fin =
                new java.io.FileInputStream(gpsStatusFile);
            for(int event = fin.read(); !interrupted() && event != -1;
                event = fin.read()) {
              gpsState.updateEvent(event, HOOK_LIBGPS);
            }
          } catch(IOException e) {
            e.printStackTrace();
          }
          if(!interrupted()) {
            // TODO: Have this instead just switch to use different hooks.
            Log.w(TAG, "GPS status thread exited. " +
                  "No longer gathering gps data.");
          }
        }
      };
      statusThread.start();
    }
  }

  private void updateUidEvent(int uid, int event, int source) {
    synchronized(uidStates) {
      GpsStateKeeper state = uidStates.get(uid);
      if(state == null) {
        state = new GpsStateKeeper(HOOK_NOTIFICATIONS | HOOK_TIMER, sleepTime,
                                   lastTime);
        uidStates.put(uid, state);
      }
      state.updateEvent(event, source);
    }
  }

  @Override
  protected void onExit() {
    if(gpsListener != null) {
      locationManager.removeGpsStatusListener(gpsListener);
    }
    if(statusThread != null) {
      statusThread.interrupt();
    }
    if(notificationReceiver != null) {
      NotificationService.removeHook(notificationReceiver);
    }
    super.onExit();
  }

  @Override
  public IterationData calculateIteration(long iteration) {
    IterationData result = IterationData.obtain();

    /* Get the number of satellites that were available in the last update. */
    int satellites = 0;
    synchronized(this) {
      if(lastStatus != null) {
        for(GpsSatellite satellite : lastStatus.getSatellites()) {
          satellites++;
        }
      }
    }

    /* Get the power data for the physical gps device. */
    GpsData power = GpsData.obtain();
    synchronized(gpsState) {
      double[] stateTimes = gpsState.getStateTimesLocked();
      int curState = gpsState.getCurrentStateLocked();
      power.init(stateTimes, curState == POWER_STATE_ON ? satellites : 0);
      gpsState.resetTimesLocked();
    }
    result.setPowerData(power);

    /* Get the power data for each uid if we have information on it. */
    if(hasUidInfo) synchronized(uidStates) {
      lastTime = beginTime + iterationInterval * iteration;
      for(int i = 0; i < uidStates.size(); i++) {
        int uid = uidStates.keyAt(i);
        GpsStateKeeper state = uidStates.valueAt(i);

        double[] stateTimes = state.getStateTimesLocked();
        int curState = state.getCurrentStateLocked();
        GpsData uidPower = GpsData.obtain();
        uidPower.init(stateTimes, curState == POWER_STATE_ON ? satellites : 0);
        state.resetTimesLocked();

        result.addUidPowerData(uid, uidPower);

        /* Remove state information for uids no longer using the gps. */
        if(curState == POWER_STATE_OFF) {
          uidStates.remove(uid);
          i--;
        }
      }
    }

    return result;
  }

  @Override
  public boolean hasUidInformation() {
    return hasUidInfo;
  }

  /* This class is used to maintain the actual GPS state in addition to
   * simulating individual uid states.
   */
  private static class GpsStateKeeper {
    private double[] stateTimes;
    private long lastTime;
    private int curState;

    /* The sum of whatever hook sources are valid.  See the HOOK_ constants. */
    private int hookMask;
    /* The time that the GPS hardware should turn off.  This is only used
     * if HOOK_TIMER is in the hookMask.
     */
    private long offTime;
    /* Gives the time that the GPS stays in the sleep state after the session
     * has ended in milliseconds.
     */
    private long sleepTime;

    public GpsStateKeeper(int hookMask, long sleepTime) {
      this(hookMask, sleepTime, SystemClock.elapsedRealtime());
    }

    public GpsStateKeeper(int hookMask, long sleepTime, long lastTime) {
      this.hookMask = hookMask;
      this.sleepTime = sleepTime; /* This isn't required if HOOK_TIEMR is not
                                   * set. */
      this.lastTime = lastTime;
      stateTimes = new double[POWER_STATES];
      curState = POWER_STATE_OFF;
      offTime = -1;
    }

    /* Make sure that you have a lock on this before calling. */
    public double[] getStateTimesLocked() {
      updateTimesLocked();

      /* Let's normalize the times so that power measurements are consistent. */
      double total = 0;
      for(int i = 0; i < POWER_STATES; i++) {
        total += stateTimes[i];
      }
      if(total == 0) total = 1;
      for(int i = 0; i < POWER_STATES; i++) {
        stateTimes[i] /= total;
      }

      return stateTimes;
    }

    public void resetTimesLocked() {
      for(int i = 0; i < POWER_STATES; i++) {
        stateTimes[i] = 0;
      }
    }

    public int getCurrentStateLocked() {
      return curState;
    }

    /* Make sure that you have a lock on this before calling. */
    private void updateTimesLocked() {
      /* Update the time we were in the previous state. */
      long curTime = SystemClock.elapsedRealtime();

      /* Check if the GPS has gone to sleep as a result of a timer. */
      if((hookMask & HOOK_TIMER) != 0 && offTime != -1 &&
         offTime < curTime) {
        stateTimes[curState] += (offTime - lastTime) / 1000.0;
        curState = POWER_STATE_OFF;
        offTime = -1;
      }

      /* Update the amount of time that we've been in the current state. */
      stateTimes[curState] += (curTime - lastTime) / 1000.0;
      lastTime = curTime;
    }
    
    /* When a hook source gets an event it should report it to updateEvent.
     * The only exception is HOOK_TIMER which is handled within this class
     * itself.
     */
    public void updateEvent(int event, int source) {
      synchronized(this) {
        if((hookMask & source) == 0) {
          /* We are not using this hook source, ignore. */
          return;
        }

        updateTimesLocked();
        int oldState = curState;
        switch(event) {
          case GPS_STATUS_SESSION_BEGIN:
            curState = POWER_STATE_ON;
            break;
          case GPS_STATUS_SESSION_END:
            if(curState == POWER_STATE_ON) {
              curState = POWER_STATE_SLEEP;
            }
            break;
          case GPS_STATUS_ENGINE_ON:
            if(curState == POWER_STATE_OFF) {
              curState = POWER_STATE_SLEEP;
            }
            break;
          case GPS_STATUS_ENGINE_OFF:
            curState = POWER_STATE_OFF;
            break;
          default:
            Log.w(TAG, "Unknown GPS event captured");
        }
        if(curState != oldState) {
          if(oldState == POWER_STATE_ON && curState == POWER_STATE_SLEEP) {
            offTime = SystemClock.elapsedRealtime() + sleepTime;
          } else {
            /* Any other state transition should reset the off timer. */
            offTime = -1;
          }
        }
      }
    }
  }

  @Override
  public String getComponentName() {
    return "GPS";
  }
}

