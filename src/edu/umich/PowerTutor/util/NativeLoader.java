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

import android.util.Log;

public class NativeLoader {
  private static final String TAG = "NativeLoader";

  private static boolean loadOk = false;

  static {
    try {
      System.loadLibrary("bindings");
      loadOk = true;
    } catch(SecurityException e) {
      Log.w(TAG, "Failed to load jni dll, will fall back on pure java");
      loadOk = false;
    } catch(UnsatisfiedLinkError e) {
      Log.w(TAG, "Failed to load jni dll, will fall back on pure java");
      loadOk = false;
    }
  }

  public static boolean jniLoaded() {
    return loadOk;
  }
}
