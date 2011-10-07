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

package edu.umich.PowerTutor.widget;

import java.io.Serializable;

import edu.umich.PowerTutor.service.PowerEstimator;
import edu.umich.PowerTutor.util.BatteryStats;
import edu.umich.PowerTutor.util.Counter;
import edu.umich.PowerTutor.util.SystemInfo;

public class DataSource implements Serializable {
  private static final long serialVersionUID = 14594389587290845L;

  public int id;
  public int[] params;

  private static final int ID_POWER = 0;
  private static final int ID_BATTERY_TIME = 1;
  private static final int ID_CHARGE = 2;
  private static final int ID_VOLTAGE = 3;
  private static final int ID_CURRENT = 4;
  private static final int ID_TEMP = 5;
  private static final int ID_PERC = 6;
  private static final String[] idShorts = {
    "Power",
    "Battery Time",
    "Charge",
    "Voltage",
    "Current",
    "Temperature",
    "Percent Battery",
  };
  private static final String[] idLongs = {
    "Displays power in mW",
    "Displays remaining battery lifetime.  Also can show time until battery " +
      "is fully charged",
    "Displays remaining battery charge in mAh",
    "Displays battery voltage in V",
    "Displays battery current in mA",
    "Displays battery temperature",
    "Displays the percent battery remaning",
  };

  private static final int POWER_INSTANT = 0;
  private static final int POWER_MINUTE = 1;
  private static final int POWER_HOUR = 2;
  private static final int POWER_DAY = 3;
  private static final int POWER_TOTAL = 4;
  private static final int POWER_SENSOR = 5;
  private static final String[] powerShorts = {
    "Instant",
    "Minute Average",
    "Hour Average",
    "Day Average",
    "Total Average",
    "Battery Sensors",
  };
  private static final String[] powerLongs = {
    "Estimated instantaneous power consumption",
    "Average power consumption over the last minute",
    "Average power consumption over the last hour",
    "Average power consumption over the last day",
    "Average power consumption while the profiler has been running",
    "Calculate power from battery current and voltage sensors",
  };

  private static final int CHARGE_SENSOR = 0;
  private static final int CHARGE_FULL = 1;
  private static final int CHARGE_1400 = 2;
  private static final String[] chargeShorts = {
    "Battery Sensor",
    "Fully Charged",
    "1400 mAh",
  };
  private static final String[] chargeLongs = {
    "Use your battery's charge readings",
    "Assume that your battery is fully charged",
    "Assume that you have 1400 mAh worth of charge " +
        "(A typical battery capacity)",
  };

  private static final int TEMP_CELCIUS = 0;
  private static final int TEMP_FARENHEIT = 1;
  private static final String[] tempShorts = {
    "Celcius",
    "Farenheit",
  };
  private static final String[] tempLongs = {
    "Display battery temperature in celcius",
    "Display battery temperature in farenheit",
  };

  private static final int BATT_LIFETIME = 0;
  private static final int BATT_CHARGETIME = 1;
  private static final String[] battShorts = {
    "Life Time",
    "Charge Time",
  };
  private static final String[] battLongs = {
    "Display remaining life time when battery plugged in",
    "Display time until battery fully charged when battery plugged in",
  };

  public String getTitle(int level) {
    if(level == 0) return "Select display type";
    switch(id) {
      case ID_POWER: return "Select power source";
      case ID_BATTERY_TIME:
        if(level == 1) return "Select power source";
        if(level == 2) return "Select charge source";
        return "Select charging behavior";
      case ID_TEMP: return "Select temperature scale";
    }
    return "";
  }

  public String[] getShortOptions(int level) {
    if(level == 0) return idShorts;
    switch(id) {
      case ID_POWER: return powerShorts;
      case ID_BATTERY_TIME: return level == 1 ? powerShorts :
                                   (level == 2 ? chargeShorts : battShorts);
      case ID_TEMP: return tempShorts;
    }
    return null;
  }

  public String[] getLongOptions(int level) {
    if(level == 0) return idLongs;
    switch(id) {
      case ID_POWER: return powerLongs;
      case ID_BATTERY_TIME: return level == 1 ? powerLongs :
                                   (level == 2 ? chargeLongs : battLongs);
      case ID_TEMP: return tempLongs;
    }
    return null;
  }

  public boolean hasOption(int level, int value) {
    BatteryStats bst = BatteryStats.getInstance();
    if(level == 0) {
      if(value == ID_PERC) return bst.hasCapacity();
      if(value == ID_CHARGE) return bst.hasCharge();
      if(value == ID_CURRENT) return bst.hasCurrent();
      return true;
    }
    switch(id) {
      case ID_POWER:
        return value != POWER_SENSOR || bst.hasCurrent() && bst.hasVoltage();
      case ID_BATTERY_TIME:
        if(level == 1) {
          return value != POWER_SENSOR || bst.hasCurrent() && bst.hasVoltage();
        } else if(level == 2) {
          return value == CHARGE_1400 ||
                 value == CHARGE_SENSOR && bst.hasCharge() ||
                 value == CHARGE_FULL && bst.hasFullCapacity();
        }
        return true;
      case ID_TEMP:
        return true;
    }
    return false;
  }

  public boolean setParam(int level, int value) {
    if(level == 0) {
      id = value;
      int numParams = 0;
      switch(id) {
        case ID_POWER: numParams = 1; break;
        case ID_BATTERY_TIME: numParams = 3; break;
        case ID_TEMP: numParams = 1; break;
      }
      if(numParams > 0) {
        params = new int[numParams];
      }
      return level == numParams;
    } else {
      params[level - 1] = value;
      if(id == ID_BATTERY_TIME) {
        BatteryStats bst = BatteryStats.getInstance();
        if(!(bst.hasCurrent() && bst.hasCharge() && bst.hasCapacity())) {
          return level + 1 == params.length;
        }
      }
      return level == params.length;
    }
  }

  public String getTitle() {
    return idShorts[id];
  }
  
  public String getDescription() {
    switch(id) {
      case ID_POWER: return powerLongs[params[0]];
      case ID_BATTERY_TIME:
        return "Power Source: " + powerLongs[params[0]] + "\n" +
               "Charge Source: " + chargeLongs[params[1]];
      case ID_TEMP: return tempLongs[params[0]];
    }
    return idLongs[id];
  }

  public static DataSource[] getDefaults() {
    DataSource[] res = new DataSource[3];
    for(int i = 0; i < res.length; i++) res[i] = new DataSource();
    res[0].setParam(0, ID_POWER);
    res[0].setParam(1, POWER_TOTAL);
    res[1].setParam(0, ID_PERC);
    res[2].setParam(0, ID_TEMP);
    res[2].setParam(1, TEMP_CELCIUS);
    return res;
  }

  public String getValue(PowerEstimator p) {
    BatteryStats bst = BatteryStats.getInstance();
    switch(id) {
      case ID_POWER: {
        double pow = calcPower(p, params[0]);
        if(pow <= 0) {
          return "Power\n-";
        }
        return String.format("Power\n%1$.0f mW", 1000 * pow);
      } case ID_BATTERY_TIME: {
        if(bst.hasCurrent() && params[2] == BATT_CHARGETIME) {
          double curr = bst.getCurrent();
          double cp = bst.getCapacity();
          if(curr > 0 && cp >= 0.01) {
            // We have been asked to compute the charge time instead.
            long time = (long)(bst.getCharge() / cp * (1.0 - cp) / curr);
            return String.format("Charge time\n%1$d:%2$02d:%3$02d",
                                 time / 60 / 60, time / 60 % 60, time % 60);
          }
        }
          
        double pow = calcPower(p, params[0]);
        double charge = calcCharge(params[1]);
        double volt = bst.getVoltage();
        if(pow <= 0 || charge <= 0 || volt <= 0) {
          return "Batt. time\n-";
        }
        long time = (long)(charge * volt / pow);
        return String.format("Batt. time\n%1$d:%2$02d:%3$02d",
                             time / 60 / 60, time / 60 % 60, time % 60);
      } case ID_CHARGE: {
        return String.format("Charge\n%1$.1f mAh",
                             calcCharge(CHARGE_SENSOR) / 3.6);
      } case ID_VOLTAGE: {
        return String.format("Voltage\n%1$.2f V", bst.getVoltage());
      } case ID_CURRENT: {
        double curr = bst.getCurrent() * 1000;
        if(curr < 0) {
          return String.format("Current\n%1$.1f mA", -curr);
        } else {
          return String.format("Current\n%1$.1f mA\n(charging)", curr);
        }
      } case ID_TEMP: {
        if(params[0] == TEMP_FARENHEIT) {
          return String.format("Temp.\n%1$.1f \u00b0F",
                               bst.getTemp() * 9 / 5 + 32);
        } else {
          return String.format("Temp.\n%1$.1f \u00b0C", bst.getTemp());
        }
      } case ID_PERC: {
        return String.format("Batt. left\n%1$.0f%%", 100 * bst.getCapacity());
      }
    }
    return "";
  }

  private static final double POLY_WEIGHT = 0.02;

  private double calcPower(PowerEstimator p, int powId) {
    switch(powId) {
      case POWER_INSTANT: {
        int count = 0;
        int[] history = p.getComponentHistory(5 * 60, -1,
                                              SystemInfo.AID_ALL, -1);
        double weightedAvgPower = 0;
        for(int i = history.length - 1; i >= 0; i--) {
          if(history[i] != 0) {
            count++;
            weightedAvgPower *= 1.0 - POLY_WEIGHT;
            weightedAvgPower += POLY_WEIGHT * history[i] / 1000.0;
          }
        }
        if(count == 0) return -1.0;
        return weightedAvgPower / (1.0 - Math.pow(1.0 - POLY_WEIGHT, count));
      } case POWER_MINUTE:
      case POWER_HOUR:
      case POWER_DAY:
      case POWER_TOTAL: {
        int wind = 0;
        if(powId == POWER_MINUTE) wind = Counter.WINDOW_MINUTE;
        if(powId == POWER_HOUR) wind = Counter.WINDOW_HOUR;
        if(powId == POWER_DAY) wind = Counter.WINDOW_DAY;
        if(powId == POWER_TOTAL) wind = Counter.WINDOW_TOTAL;
        double total = 0;
        for(long x : p.getMeans(SystemInfo.AID_ALL, wind)) {
          total += x / 1000.0;
        }
        return total;
      } case POWER_SENSOR: {
        BatteryStats bst = BatteryStats.getInstance();
        double curr = bst.getCurrent();
        if(curr >= 0) return -1.0;
        return curr * bst.getVoltage();
      }
    }
    return -1.0;
  }

  private double calcCharge(int chargeId) {
    BatteryStats bst = BatteryStats.getInstance();
    switch(chargeId) {
      case CHARGE_SENSOR: return bst.getCharge();
      case CHARGE_FULL: return bst.getFullCapacity();
      case CHARGE_1400: return 1400 * 3.6; // mAh -> As
    }
    return -1.0;
  }
}
