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

import android.content.Context;

/* This class is responsible for implementing all policy decisions on when to
 * send log information back to an external server.  Upon a successful call to
 * shouldUpload() PowerEstimator will then call upload(file).  After the call to
 * upload the file with the passed path may be overwritten.
 *
 * This is a stub implementation not supporting log uploading.
 */
public class LogUploader {
  public LogUploader(Context context) {
  }

  /* Returns true if this module supports uploading logs. */
  public static boolean uploadSupported() {
    return false;
  }

  /* Returns true if the log should be uploaded now.  This may depend on log
   * file size, network conditions, etc. */
  // TODO: This should probably give the file name of the log
  public boolean shouldUpload() {
    return false;
  }

  /* Called when the device is plugged in or unplugged.  The intended use of
   * this is to improve upload policy decisions. */
  public void plug(boolean plugged) {
  }

  /* Initiate the upload of the file with the passed location. */
  public void upload(String origFile) {
  }

  /* Returns true if a file is currently being uploaded. */
  public boolean isUploading() {
    return false;
  }

  /* Interrupt any threads doing upload work. */
  public void interrupt() {
  }

  /* Join any threads that may be performing log upload work. */
  public void join() throws InterruptedException {
  }
}
