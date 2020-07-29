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

import java.util.Vector;

/* The aim of this class is to reduce the amount of objects that need to be
 * created and destroyed every iteration.  If we can avoid having to allocate
 * objects on the heap we can ease the job of the garbage collector and be
 * more efficient.
 */
public class Recycler<T> {
  private Vector<T> list;
  private int avail;

  public Recycler() {
    list = new Vector<T>();
    avail = 0;
  }

  public synchronized T obtain() {
    if(avail == 0) {
      return null;
    }
    return list.get(--avail);
  }

  public synchronized void recycle(T a) {
    if(avail < list.size()) {
      list.set(avail++, a);
    } else {
      list.add(a);
    }
  }
}
