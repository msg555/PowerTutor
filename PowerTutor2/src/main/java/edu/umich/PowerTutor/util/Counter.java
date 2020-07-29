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

import android.os.SystemClock;

public class Counter {
  public static final int WINDOW_MINUTE = 0;
  public static final int WINDOW_HOUR = 1;
  public static final int WINDOW_DAY = 2;
  public static final int WINDOW_TOTAL = 3;
  public static final CharSequence[] WINDOW_NAMES = { "Last minute",
      "Last Hour", "Last Day", "Total"};
  // To be used for constructions like "Showing X over ..."
  public static final CharSequence[] WINDOW_DESCS = { "the last minute",
      "the last hour", "the last day", "all time"};
  private static final long WINDOW_DURATIONS[] = { 60 * 1000, 60 * 60 * 1000,
                                                   24 * 60 * 60 * 1000};

  private long startTime;
  private long total;
  private SingleCounter[] counters;

  public Counter() {
    total = 0;
    startTime = SystemClock.elapsedRealtime();
    counters = new SingleCounter[WINDOW_DURATIONS.length];
    for(int i = 0; i < counters.length; i++) {
      counters[i] = new SingleCounter();
    }
  }

  public void add(long x) {
    total += x;
    long now = SystemClock.elapsedRealtime() - startTime;
    for(int i = 0; i < counters.length; i++) {
      counters[i].add(x, now * SingleCounter.BUCKETS / WINDOW_DURATIONS[i]);
    }
  }

  public long get(int window) {
    if(window == WINDOW_TOTAL) {
      return total;
    }
    long now = SystemClock.elapsedRealtime() - startTime;
    return counters[window].get(
        now * SingleCounter.BUCKETS / WINDOW_DURATIONS[window],
        (1.0 * now * SingleCounter.BUCKETS % WINDOW_DURATIONS[window]) /
                                             WINDOW_DURATIONS[window]);
  }
  
  private static class SingleCounter {
    public static final int BUCKETS = 60;

    private long base;
    private int baseIdx;
    private long droppingBucket;
    private long[] bucketSum;
    private long total;

    public SingleCounter() {
      bucketSum = new long[BUCKETS];
    }

    private void wind(long now) {
      if(base + 2 * BUCKETS <= now) {
        /* Completly clear the data structure. */
        droppingBucket = 0;
        for(int i = 0; i < BUCKETS; i++) {
          bucketSum[i] = 0;
        }
        total = 0;
        base = now;
        baseIdx = 0;
      } else while(base + BUCKETS <= now) {
        droppingBucket = bucketSum[baseIdx];
        total -= droppingBucket;
        bucketSum[baseIdx] = 0;
        base++;
        baseIdx = baseIdx + 1 == BUCKETS ? 0 : baseIdx + 1;
      }
    }

    public void add(long x, long now) {
      wind(now);
      total += x;
      int idx = (int)(baseIdx + now - base);
      bucketSum[idx < BUCKETS ? idx : idx - BUCKETS] += x;
    }

    /* now gives the time slice that we want information for.
     * prog, between 0 and 1, gives the progress through the current time slice
     * with 0 indicating that it just started and 1 indicating that it is about
     * to end.
     */
    public long get(long now, double prog) {
      wind(now);
      return total + (long)((1.0 - prog) * droppingBucket);
    }
  }
}
