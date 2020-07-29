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

import edu.umich.PowerTutor.service.IterationData;

import android.os.SystemClock;
import android.util.Log;

public abstract class PowerComponent extends Thread {
	private final String TAG = "PowerComponent";

  /* Extending classes need to override the calculateIteration function.  It
   * should calculate the data point for the given component in a timely
   * manner (under 1 second, longer times will cause data to be missed).
   * The iteration parameter can be ignored in most cases.
   */
  protected abstract IterationData calculateIteration(long iteration);

  /* Extending classes should provide a recognizable name for this component
   * to be used when writing to logs.
   */
  public abstract String getComponentName();

  /* Returns true if this component collects usage information per uid.
   */
  public boolean hasUidInformation() {
    return false;
  }

  /* Called when the thread running this interface is asked to exit.
   */
  protected void onExit() {
  }

  /* In general we should only need to buffer two data elements.
   */
  private IterationData data1;
  private IterationData data2;
  private long iteration1;
  private long iteration2;

  protected long beginTime;
  protected long iterationInterval;

  public PowerComponent() {
    setDaemon(true);
  }

  /* This is called once at the begginning of the daemon loop.
   */
  public void init(long beginTime, long iterationInterval) {
    this.beginTime = beginTime;
    this.iterationInterval = iterationInterval;
    data1 = data2 = null;
    iteration1 = iteration2 = -1;
  }

  /* Runs the daemon loop that collects data for this component. */
  public void run() {
    android.os.Process.setThreadPriority(
        android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);
    for(long iter = 0; !Thread.interrupted(); ) {
      /* Hand off to the client class to actually calculate the information
       * we want for this component.
       */
      IterationData data = calculateIteration(iter);
      if(data != null) {
        synchronized(this) {
          if(iteration1 < iteration2) {
            iteration1 = iter;
            data1 = data;
          } else {
            iteration2 = iter;
            data2 = data;
          }
        }
      }
      if(interrupted()) {
        break;
      }

      long curTime = SystemClock.elapsedRealtime();
      /* Compute the next iteration that we can make the start of. */
      long oldIter = iter;
      iter = (long)Math.max(iter + 1,
                            1 + (curTime - beginTime) / iterationInterval);
      if(oldIter + 1 != iter) {
        Log.w(TAG, "[" + getComponentName() + "] Had to skip from iteration " +
                   oldIter + " to " + iter);
      }
      /* Sleep until the next iteration completes. */
      try {
			  sleep(beginTime + iter * iterationInterval - curTime);
      } catch(InterruptedException e) {
        break;
      }
    }
    onExit();
  }

  /* Returns the data point for the given iteration.  This method will be called
     with a strictly increasing iteration parameter.
   */
  public IterationData getData(long iteration) {
    synchronized(this) {
      IterationData ret = null;
      if(iteration == iteration1) ret = data1;
      if(iteration == iteration2) ret = data2;
      if(iteration1 <= iteration) {
        data1 = null;
        iteration1 = -1;
      }
      if(iteration2 <= iteration) {
        data2 = null;
        iteration2 = -1;
      }
      if(ret == null) {
        Log.w(TAG, "[" + getComponentName() + "] Could not find data for " +
                   "requested iteration");
      }
      return ret;
    }
  }
}
