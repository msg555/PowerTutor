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

import java.io.Serializable;

public class UidInfo implements Serializable, Comparable {
  private static Recycler<UidInfo> recycler = new Recycler<UidInfo>();

  public static UidInfo obtain() {
    UidInfo result = recycler.obtain();
    if(result != null) return result;
    return new UidInfo();
  }

  public void recycle() {
    recycler.recycle(this);
  }

  public int uid;
  public int currentPower;
  public long totalEnergy;
  public long runtime;

  public transient double key;
  public transient double percentage;
  public transient String unit;

  private UidInfo() {
  }

  public void init(int uid, int currentPower, long totalEnergy,
                   long runtime) {
    this.uid = uid;
    this.currentPower = currentPower;
    this.totalEnergy = totalEnergy;
    this.runtime = runtime;
  }

  public int compareTo(Object o) {
    UidInfo x = (UidInfo)o;
    if(key > x.key) return -1;
    if(key == x.key) return 0;
    return 1;
  }
}
