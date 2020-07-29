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

package edu.umich.PowerTutor;

interface PowerNotifications {
    // These are the notifications that are actually supported.  The rest have
    // potential to be supported but aren't needed at the moment so aren't
    // actually hooked.
    void noteStartWakelock(int uid, String name, int type);
    void noteStopWakelock(int uid, String name, int type);
    void noteStartSensor(int uid, int sensor);
    void noteStopSensor(int uid, int sensor);
    void noteStartGps(int uid);
    void noteStopGps(int uid);
    void noteScreenBrightness(int brightness);
    void noteStartMedia(int uid, int id);
    void noteStopMedia(int uid, int id);
    void noteVideoSize(int uid, int id, int width, int height);
    void noteSystemMediaCall(int uid);

    void noteScreenOn();
    void noteScreenOff();
    void noteInputEvent();
    void noteUserActivity(int uid, int event);
    void notePhoneOn();
    void notePhoneOff();
    void notePhoneDataConnectionState(int dataType, boolean hasData);
    void noteWifiOn(int uid);
    void noteWifiOff(int uid);
    void noteWifiRunning();
    void noteWifiStopped();
    void noteBluetoothOn();
    void noteBluetoothOff();
    void noteFullWifiLockAcquired(int uid);
    void noteFullWifiLockReleased(int uid);
    void noteScanWifiLockAcquired(int uid);
    void noteScanWifiLockReleased(int uid);
    void noteWifiMulticastEnabled(int uid);
    void noteWifiMulticastDisabled(int uid);
    void setOnBattery(boolean onBattery, int level);
    void recordCurrentLevel(int level);
    /* Also got rid of the non-notification calls.
     * byte[] getStatistics();
     * long getAwakeTimeBattery();
     * long getAwakeTimePlugged();
     */

    /* Added functions to the normal IBatteryStats interface. */
    void noteVideoOn(int uid);
    void noteVideoOff(int uid);
    void noteAudioOn(int uid);
    void noteAudioOff(int uid);
}
