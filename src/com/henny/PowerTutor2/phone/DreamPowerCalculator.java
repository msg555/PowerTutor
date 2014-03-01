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

*/

package com.henny.PowerTutor2.phone;

import com.henny.PowerTutor2.components.*;
import com.henny.PowerTutor2.components.Audio.AudioData;
import com.henny.PowerTutor2.components.CPU.CpuData;
import com.henny.PowerTutor2.components.GPS.GpsData;
import com.henny.PowerTutor2.components.LCD.LcdData;
import com.henny.PowerTutor2.components.OLED.OledData;
import com.henny.PowerTutor2.components.Sensors.SensorData;
import com.henny.PowerTutor2.components.Threeg.ThreegData;
import com.henny.PowerTutor2.components.Wifi.WifiData;

import android.content.Context;

public class DreamPowerCalculator implements PhonePowerCalculator {
  protected PhoneConstants coeffs;

  public DreamPowerCalculator(Context context) {
    this(new DreamConstants(context));
  }

  protected DreamPowerCalculator(PhoneConstants coeffs) {
    this.coeffs = coeffs;
  }

  public double getLcdPower(LcdData data) {
    return data.screenOn ?
           coeffs.lcdBrightness() * data.brightness + coeffs.lcdBacklight() : 0;
  } 

  public double getOledPower(OledData data) {
    throw new RuntimeException("getOledPower() should not be called for Dream");
  }

  public double getCpuPower(CpuData data) {
    /* Find the two nearest cpu frequency and linearly interpolate
     * the power ratio for that frequency.
     */
    double[] powerRatios = coeffs.cpuPowerRatios();
    double[] freqs = coeffs.cpuFreqs();
    double ratio;
    if(powerRatios.length == 1) {
      ratio = powerRatios[0];
    } else {
      double sfreq = data.freq;
      if(sfreq < freqs[0]) sfreq = freqs[0];
      if(sfreq > freqs[freqs.length - 1]) sfreq = freqs[freqs.length - 1];

      int ind = upperBound(freqs, sfreq);
      if(ind == 0) ind++;
      if(ind == freqs.length) ind--;
      ratio = powerRatios[ind - 1] + (powerRatios[ind] - powerRatios[ind - 1]) /
                                     (freqs[ind] - freqs[ind - 1]) *
                                     (sfreq - freqs[ind - 1]);
    }
    return Math.max(0, ratio * (data.usrPerc + data.sysPerc));
  }

  public double getAudioPower(AudioData data) {
    return data.musicOn ? coeffs.audioPower() : 0;
  }

  public double getGpsPower(GpsData data) {
    double result = 0;
    double statePower[] = coeffs.gpsStatePower();
    for(int i = 0; i < GPS.POWER_STATES; i++) {
      result += data.stateTimes[i] * statePower[i];
    }
    return result;
  }

  public double getWifiPower(WifiData data) {
    if(!data.wifiOn) {
      return 0;
    } else if(data.powerState == Wifi.POWER_STATE_LOW) {
      return coeffs.wifiLowPower();
    } else if(data.powerState == Wifi.POWER_STATE_HIGH) {
      double[] linkSpeeds = coeffs.wifiLinkSpeeds();
      double[] linkRatios = coeffs.wifiLinkRatios();
      double ratio;
      if(linkSpeeds.length == 1) {
        /* If there is only one set speed we have to use its ratio as we have
         * nothing else to go on.
         */
        ratio = linkRatios[0];
      } else {
        /* Find the two nearest speed/ratio pairs and linearly interpolate
         * the ratio for this link speed.
         */
        int ind = upperBound(linkSpeeds, data.linkSpeed);
        if(ind == 0) ind++;
        if(ind == linkSpeeds.length) ind--;
        ratio = linkRatios[ind - 1] + (linkRatios[ind] - linkRatios[ind - 1]) /
                                      (linkSpeeds[ind] - linkSpeeds[ind - 1]) *
                                      (data.linkSpeed - linkSpeeds[ind - 1]);
      }
      return Math.max(0, coeffs.wifiHighPower() + ratio * data.uplinkRate);
    }
    throw new RuntimeException("Unexpected power state");
  }

  public double getThreeGPower(ThreegData data) {
    if(!data.threegOn) {
      return 0;
    } else {
      switch(data.powerState) {
        case Threeg.POWER_STATE_IDLE:
          return coeffs.threegIdlePower(data.oper);
        case Threeg.POWER_STATE_FACH:
          return coeffs.threegFachPower(data.oper);
        case Threeg.POWER_STATE_DCH:
          return coeffs.threegDchPower(data.oper);
      }
    }
    return 0;
  }

  public double getSensorPower(SensorData data) {
    double result = 0;
    double[] powerUse = coeffs.sensorPower();
    for(int i = 0; i < Sensors.MAX_SENSORS; i++) {
      result += data.onTime[i] * powerUse[i];
    }
    return result;
  }

  /* Returns the largest index y such that if x were inserted into A (which
   * should already be sorted) at y then A would remain sorted.
   */
  protected static int upperBound(double[] A, double x) {
    int lo = 0;
    int hi = A.length;
    while(lo < hi) {
      int mid = lo + (hi - lo) / 2;
      if(A[mid] <= x) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }
}

