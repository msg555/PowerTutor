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

import edu.umich.PowerTutor.util.Recycler;
import edu.umich.PowerTutor.util.SystemInfo;

import android.util.SparseArray;

/* Class that encloses physical hardware power data as well as estimated power
 * data for each uid that contributes a non-negligable amount of power for this
 * component.
 */
public class IterationData {
  private static Recycler<IterationData> recycler =
      new Recycler<IterationData>();

  private SparseArray<PowerData> uidPower;

  public static IterationData obtain() {
    IterationData result = recycler.obtain();
    if(result != null) return result;
    return new IterationData();
  }

  private IterationData() {
    uidPower = new SparseArray<PowerData>();
  }

  /* Initialize the members of this structure.  Remember that this class may not
   * have just been instantiated and may have been used in past iterations.
   */
  public void init() {
    uidPower.clear();
  }

  /* Allow this class to be recycled and to recycle all of the PowerData
   * PowerData elements contained within it.
   */
  public void recycle() {
    for(int i = 0; i < uidPower.size(); i++) {
      uidPower.valueAt(i).recycle();
    }
    uidPower.clear();
    recycler.recycle(this);
  }
  
  public void setPowerData(PowerData power) {
    addUidPowerData(SystemInfo.AID_ALL, power);
  }

  public void addUidPowerData(int uid, PowerData power) {
    uidPower.put(uid, power);
  }

  public SparseArray<PowerData> getUidPowerData() {
    return uidPower;
  }
}
