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

import java.io.OutputStreamWriter;
import java.io.IOException;
import java.util.Vector;

public abstract class PowerData {
  private int cachedPower;

  public PowerData() {
  }

  public void setCachedPower(int power) {
    cachedPower = power;
  }

  public int getCachedPower() {
    return cachedPower;
  }

  /* To be called when the PowerData object is no longer in use so that it can
   * be used again in the next iteration if it chooses to be.
   */
  public void recycle() {}

  /* Simply writes out log information to the passed stream. */
  public abstract void writeLogDataInfo(OutputStreamWriter out)
      throws IOException;
}
