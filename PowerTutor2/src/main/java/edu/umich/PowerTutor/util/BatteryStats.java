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

package edu.umich.PowerTutor.util;

import java.io.File;

public class BatteryStats {
  private static final String TAG = "BatteryStats";
  private static BatteryStats instance = null;

  public static BatteryStats getInstance() {
    if(instance == null) {
      instance = new BatteryStats();
    }
    return instance;
  }

  private static final String[] VOLTAGE_FILES = {
    "/sys/class/power_supply/battery/voltage_now",
    "/sys/class/power_supply/battery/batt_vol",
  };
  private static final double[] VOLTAGE_CONV = {
    1e-6, // Source in microvolts.
    1e-3, // Source in millivolts.
  };

  private static final String[] CURRENT_FILES = {
    "/sys/class/power_supply/battery/current_now",
    //"/sys/class/power_supply/battery/batt_current", Doesn't seem good
  };
  private static final double[] CURRENT_CONV = {
    1e-6, // Source in microamps.
    //1e-3, // Source in milliamps.
  };

  private static final String[] TEMP_FILES = {
    "/sys/class/power_supply/battery/temp",
    "/sys/class/power_supply/battery/batt_temp",
  };
  private static final double[] TEMP_CONV = {
    1e-1, // Source in tenths of a centigrade.
    1e-1, // Source in tenths of a centigrade.
  };

  private static final String[] CHARGE_FILES = {
    "/sys/class/power_supply/battery/charge_counter",
  };
  private static final double[] CHARGE_CONV = {
    60*60*1e-6, // Source in micro amp hours.
  };

  private static final String[] CAPACITY_FILES = {
    "/sys/class/power_supply/battery/capacity",
  };
  private static final double[] CAPACITY_CONV = {
    1e-2, // Source in percentage.
  };

  private static final String[] FULL_CAPACITY_FILES = {
    "/sys/class/power_supply/battery/full_bat",
  };
  private static final double[] FULL_CAPACITY_CONV = {
    60*60*1e-6, // Source in micro amp hours.
  };

  SystemInfo sysInfo;

  String voltageFile;
  String currentFile;
  String tempFile;
  String chargeFile;
  String capacityFile;
  String fullCapacityFile;

  double voltageConv;
  double currentConv;
  double tempConv;
  double chargeConv;
  double capacityConv;
  double fullCapacityConv;

  private BatteryStats() {
    sysInfo = SystemInfo.getInstance();

    // Get voltage information.
    for(int i = 0; i < VOLTAGE_FILES.length; i++) {
      if(new File(VOLTAGE_FILES[i]).exists()) {
        voltageFile = VOLTAGE_FILES[i];
        voltageConv = VOLTAGE_CONV[i];
      }
    }

    // Get current information.
    for(int i = 0; i < CURRENT_FILES.length; i++) {
      if(new File(CURRENT_FILES[i]).exists()) {
        currentFile = CURRENT_FILES[i];
        currentConv = CURRENT_CONV[i];
      }
    }

    // Get temperature information.
    for(int i = 0; i < TEMP_FILES.length; i++) {
      if(new File(TEMP_FILES[i]).exists()) {
        tempFile = TEMP_FILES[i];
        tempConv = TEMP_CONV[i];
      }
    }

    // Get charge information.
    for(int i = 0; i < CHARGE_FILES.length; i++) {
      if(new File(CHARGE_FILES[i]).exists()) {
        chargeFile = CHARGE_FILES[i];
        chargeConv = CHARGE_CONV[i];
      }
    }

    // Get capacity information.
    for(int i = 0; i < CAPACITY_FILES.length; i++) {
      if(new File(CAPACITY_FILES[i]).exists()) {
        capacityFile = CAPACITY_FILES[i];
        capacityConv = CAPACITY_CONV[i];
      }
    }

    // Get full capacity information.
    for(int i = 0; i < FULL_CAPACITY_FILES.length; i++) {
      if(new File(FULL_CAPACITY_FILES[i]).exists()) {
        fullCapacityFile = FULL_CAPACITY_FILES[i];
        fullCapacityConv = FULL_CAPACITY_CONV[i];
      }
    }
  }

  public boolean hasVoltage() {
    return voltageFile != null;
  }

  public double getVoltage() {
    if(voltageFile == null) return -1.0;
    long volt = sysInfo.readLongFromFile(voltageFile);
    return volt == -1 ? -1.0 : voltageConv * volt;
  }

  public boolean hasCurrent() {
    return currentFile != null;
  }

  public double getCurrent() {
    long curr = sysInfo.readLongFromFile(currentFile);
    return curr == -1 ? -1.0 : currentConv * curr;
  }

  public boolean hasTemp() {
    return tempFile != null;
  }

  public double getTemp() {
    if(tempFile == null) return -1.0;
    long temp = sysInfo.readLongFromFile(tempFile);
    return temp == -1 ? -1.0 : tempConv * temp;
  }

  public boolean hasCharge() {
    return chargeFile != null ||
           hasFullCapacity() && hasCapacity();
  }

  public double getCharge() {
    if(chargeFile == null) {
      double r1 = getCapacity();
      double r2 = getFullCapacity();
      return r1 < 0 || r2 < 0 ? -1.0 : r1 * r2;
    }
    long charge = sysInfo.readLongFromFile(chargeFile);
    return charge == -1 ? -1.0 : chargeConv * charge;
  }

  public boolean hasCapacity() {
    return capacityFile != null;
  }

  public double getCapacity() {
    if(capacityFile == null) return -1.0;
    long cap = sysInfo.readLongFromFile(capacityFile);
    return cap == -1 ? -1.0 : capacityConv * cap;
  }

  public boolean hasFullCapacity() {
    return fullCapacityFile != null;
  }

  public double getFullCapacity() {
    if(fullCapacityFile == null) return -1.0;
    long cap = sysInfo.readLongFromFile(fullCapacityFile);
    return cap == -1 ? -1.0 : fullCapacityConv * cap;
  }
}
