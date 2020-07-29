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
import edu.umich.PowerTutor.service.IterationData;
import edu.umich.PowerTutor.service.PowerData;
import edu.umich.PowerTutor.util.NotificationService;
import edu.umich.PowerTutor.util.Recycler;
import edu.umich.PowerTutor.util.SystemInfo;

import android.content.Context;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.TreeMap;

public class Sensors extends PowerComponent {
	private final String TAG = "Sensors";
  public static final int MAX_SENSORS = 10;

  public static class SensorData extends PowerData {
    private static Recycler<SensorData> recycler = new Recycler<SensorData>();

    public static SensorData obtain() {
      SensorData result = recycler.obtain();
      if(result != null) return result;
      return new SensorData();
    }

    @Override
    public void recycle() {
      recycler.recycle(this);
    }
    
    public double[] onTime;

    private SensorData() {
      onTime = new double[MAX_SENSORS];
    }

	  public void writeLogDataInfo(OutputStreamWriter out) throws IOException {
      StringBuilder res = new StringBuilder();
      for(int i = 0; i < MAX_SENSORS; i++) {
        if(onTime[i] > 1e-7) {
          res.append("Sensors-time ").append(i).append(" ")
             .append(onTime[i]).append("\n");
        }
      }
      out.write(res.toString());
    }
  }

  private Context context;
  private SensorManager sensorManager;
  private PowerNotifications sensorHook;

  private SensorStateKeeper sensorState;
  private SparseArray<SensorStateKeeper> uidStates;

  public Sensors(Context context) {
    this.context = context;
    sensorState = new SensorStateKeeper();
    uidStates = new SparseArray<SensorStateKeeper>();

    if(!NotificationService.available()) {
      Log.w(TAG, "Sensor component created although no notification service " +
            "available to receive sensor usage information");
      return;
    }
    sensorManager = (SensorManager)context.getSystemService(
        Context.SENSOR_SERVICE);
    sensorHook = new NotificationService.DefaultReceiver() {
      public void noteStartSensor(int uid, int sensor) {
        if(sensor < 0 || MAX_SENSORS <= sensor) {
          Log.w(TAG, "Received sensor outside of accepted range");
          return;
        }
        synchronized(sensorState) {
          sensorState.startSensor(sensor);
          SensorStateKeeper uidState = uidStates.get(uid);
          if(uidState == null) {
            uidState = new SensorStateKeeper();
            uidStates.put(uid, uidState);
          }
          uidState.startSensor(sensor);
        }
      }

      public void noteStopSensor(int uid, int sensor) {
        if(sensor < 0 || MAX_SENSORS <= sensor) {
          Log.w(TAG, "Received sensor outside of accepted range");
          return;
        }
        synchronized(sensorState) {
          sensorState.stopSensor(sensor);
          SensorStateKeeper uidState = uidStates.get(uid);
          if(uidState == null) {
            uidState = new SensorStateKeeper();
            uidStates.put(uid, uidState);
          }
          uidState.stopSensor(sensor);
        }
      }
    };
    NotificationService.addHook(sensorHook);
  }

  @Override
  protected void onExit() {
    super.onExit();
    NotificationService.removeHook(sensorHook);
  } 

  @Override
  public IterationData calculateIteration(long iteration) {
    IterationData result = IterationData.obtain();
    synchronized(sensorState) {
      SensorData globalData = SensorData.obtain();
      sensorState.setupSensorTimes(globalData.onTime, iterationInterval);
      result.setPowerData(globalData);

      for(int i = 0; i < uidStates.size(); i++) {
        int uid = uidStates.keyAt(i);
        SensorStateKeeper uidState = uidStates.valueAt(i);
        SensorData uidData = SensorData.obtain();
        uidState.setupSensorTimes(uidData.onTime, iterationInterval);
        result.addUidPowerData(uid, uidData);

        if(uidState.sensorsOn() == 0) {
          uidStates.remove(uid);
          i--;
        }
      }
    }
    return result;
  }

  private static class SensorStateKeeper {
    private int[] nesting;
    private long[] times;
    private long lastTime;
    private int count;

    public SensorStateKeeper() {
      nesting = new int[MAX_SENSORS];
      times = new long[MAX_SENSORS];
      lastTime = SystemClock.elapsedRealtime();
    }

    public void startSensor(int sensor) {
      if(nesting[sensor]++ == 0) {
        times[sensor] -= SystemClock.elapsedRealtime() - lastTime;
        count++;
      }
    }

    public void stopSensor(int sensor) {
      if(nesting[sensor] == 0) {
        return;
      } else if(--nesting[sensor] == 0) {
        times[sensor] += SystemClock.elapsedRealtime() - lastTime;
        count--;
      }
    }

    public int sensorsOn() {
      return count;
    }

    public void setupSensorTimes(double[] sensorTimes, long iterationInterval) {
      long now = SystemClock.elapsedRealtime();
      long div = now - lastTime;
      if(div <= 0) div = 1;
      for(int i = 0; i < MAX_SENSORS; i++) {
        sensorTimes[i] = 1.0 * (times[i] +
                         (nesting[i] > 0 ? now - lastTime : 0)) / div;
        times[i] = 0;
      }
      lastTime = now;
    }
  }

  @Override
  public boolean hasUidInformation() {
    return true;
  }

  @Override
  public String getComponentName() {
    return "Sensors";
  }
}
