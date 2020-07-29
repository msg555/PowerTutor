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

package edu.umich.PowerTutor.phone;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import edu.umich.PowerTutor.components.Sensors;

public class DreamConstants implements PhoneConstants {
  protected static final String OPER_TMOBILE = "T - Mobile";
  protected static final String OPER_ATT = "AT&T";

  /* TODO: Figure out if this is really appropriate or how we should convert
   * the sensor power ratings (in mA) to mW.  I'm not sure we'll try to model
   * these thing's power usage but for the developer it's definitely interesting
   * to get some (perhaps rough?) idea of how much power sensors are using.
   */
  protected double BATTERY_VOLTAGE = 3.7;

  public DreamConstants(Context context) {
    SensorManager sensorManager = (SensorManager)context.getSystemService(
                                      Context.SENSOR_SERVICE);
    sensorPowerArray = new double[Sensors.MAX_SENSORS];
    for(int i = 0; i < Sensors.MAX_SENSORS; i++) {
      Sensor sensor = sensorManager.getDefaultSensor(i);
      if(sensor != null) {
        sensorPowerArray[i] = sensor.getPower() * BATTERY_VOLTAGE;
      }
    }
  }

  public String modelName() {
    return "dream";
  }

  public double maxPower() {
    return 2800;
  }

  public double lcdBrightness() {
    return 2.40276;
  }

  public double lcdBacklight() {
    return 121.4606 + 166.5;
  }

  public double oledBasePower() {
    throw new RuntimeException("oledBasePower() called on device with no " +
                               "OLED display");
  }

  public double[] oledChannelPower() {
    throw new RuntimeException("oledChannelPower() called on device with no " +
                               "OLED display");
  }

  public double oledModulation() {
    throw new RuntimeException("oledModulation() called on device with no " +
                               "OLED display");
  }

  private static final double[] arrayCpuPowerRatios = {3.4169, 4.3388};
  public double[] cpuPowerRatios() {
    return arrayCpuPowerRatios;
  }

  private static final double[] arrayCpuFreqs = {245.36, 383.38};
  public double[] cpuFreqs() {
    return arrayCpuFreqs;
  }

  public double audioPower() {
    return 384.62;
  }

  private static final double[] arrayGpsStatePower = {0.0, 173.55, 429.55};
  public double[] gpsStatePower() {
    return arrayGpsStatePower;
  }

  public double gpsSleepTime() {
    return 6.0;
  }

  public double wifiLowPower() {
    return 38.554;
  }

  public double wifiHighPower() {
    return 720;
  }

  public double wifiLowHighTransition() {
    return 15;
  }

  public double wifiHighLowTransition() {
    return 8;
  }

  private static final double[] arrayWifiLinkRatios = {
    47.122645, 46.354821, 43.667437, 43.283525, 40.980053, 39.44422, 38.676581,
    34.069637, 29.462693, 20.248805, 11.034917, 6.427122
  };
  public double[] wifiLinkRatios() {
    return arrayWifiLinkRatios;
  }

  private static final double[] arrayWifiLinkSpeeds = {
    1, 2, 5.5, 6, 9, 11, 12, 18, 24, 36, 48, 54
  };
  public double[] wifiLinkSpeeds() {
    return arrayWifiLinkSpeeds;
  }

  public String threegInterface() {
    return "rmnet0";
  }

  public double threegIdlePower(String oper) {
    if(OPER_TMOBILE.equals(oper)) {
      return 10;
    }
    return 10;
  }

  public double threegFachPower(String oper) {
    if(OPER_TMOBILE.equals(oper)) {
      return 401;
    }
    return 401;
  }

  public double threegDchPower(String oper) {
    if(OPER_TMOBILE.equals(oper)) {
      return 570;
    }
    return 570;
  }

  public int threegDchFachDelay(String oper) {
    if(OPER_TMOBILE.equals(oper)) {
      return 6;
    } else if(OPER_ATT.equals(oper)) {
      return 5;
    }
    return 4;
  }

  public int threegFachIdleDelay(String oper) {
    if(OPER_TMOBILE.equals(oper)) {
      return 4;
    } else if(OPER_ATT.equals(oper)) {
      return 12;
    }
    return 6;
  }

  public int threegUplinkQueue(String oper) {
    return 151;
  }

  public int threegDownlinkQueue(String oper) {
    return 119;
  }

  private double[] sensorPowerArray;
  public double[] sensorPower() {
    return sensorPowerArray;
  }

  public double getMaxPower(String componentName) {
    if("LCD".equals(componentName)) {
      return lcdBacklight() + lcdBrightness() * 255;
    } else if("CPU".equals(componentName)) {
      double[] ratios = cpuPowerRatios();
      return ratios[ratios.length - 1] * 100;
    } else if("Audio".equals(componentName)) {
      return audioPower();
    } else if("GPS".equals(componentName)) {
      double[] gpsPow = gpsStatePower();
      return gpsPow[gpsPow.length - 1];
    } else if("Wifi".equals(componentName)) {
      // TODO: Get a better estimation going here.
      return 800;
    } else if("3G".equals(componentName)) {
      return threegDchPower("");
    } else if("Sensors".equals(componentName)) {
      double res = 0;
      for(double x : sensorPower()) res += x;
      return res;
    } else {
      return 900;
    }
  }
}
