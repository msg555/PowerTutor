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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.ActivityManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

public class SystemInfo {
  private static final String TAG = "SystemInfo";
  private static SystemInfo instance = new SystemInfo();

  public static SystemInfo getInstance() {
  	return instance;
  }

  /* Uids as listed in android_filesystem_config.h */
  public static final int AID_ALL         =   -1; /* A special constant we will
                                                   * use to indicate a request
                                                   * for global information. */
  public static final int AID_ROOT        =    0; /* traditional unix root user
                                                   */
  public static final int AID_SYSTEM      = 1000; /* system server */
  public static final int AID_RADIO       = 1001; /* telephony subsystem, RIL */
  public static final int AID_BLUETOOTH   = 1002; /* bluetooth subsystem */
  public static final int AID_GRAPHICS    = 1003; /* graphics devices */
  public static final int AID_INPUT       = 1004; /* input devices */
  public static final int AID_AUDIO       = 1005; /* audio devices */
  public static final int AID_CAMERA      = 1006; /* camera devices */
  public static final int AID_LOG         = 1007; /* log devices */
  public static final int AID_COMPASS     = 1008; /* compass device */
  public static final int AID_MOUNT       = 1009; /* mountd socket */
  public static final int AID_WIFI        = 1010; /* wifi subsystem */
  public static final int AID_ADB         = 1011; /* android debug bridge
                                                     (adbd) */
  public static final int AID_INSTALL     = 1012; /* group for installing
                                                     packages */
  public static final int AID_MEDIA       = 1013; /* mediaserver process */
  public static final int AID_DHCP        = 1014; /* dhcp client */
  public static final int AID_SHELL       = 2000; /* adb and debug shell user */
  public static final int AID_CACHE       = 2001; /* cache access */
  public static final int AID_DIAG        = 2002; /* access to diagnostic
                                                     resources */
  /* The 3000 series are intended for use as supplemental group id's only.
   * They indicate special Android capabilities that the kernel is aware of. */
  public static final int AID_NET_BT_ADMIN= 3001; /* bluetooth: create any
                                                     socket */
  public static final int AID_NET_BT      = 3002; /* bluetooth: create sco,
                                                     rfcomm or l2cap sockets */
  public static final int AID_INET        = 3003; /* can create AF_INET and
                                                     AF_INET6 sockets */
  public static final int AID_NET_RAW     = 3004; /* can create raw INET sockets
                                                   */
  public static final int AID_MISC        = 9998; /* access to misc storage */
  public static final int AID_NOBODY      = 9999;
  public static final int AID_APP         =10000; /* first app user */

  /* These are stolen from Process.java which hides these constants. */
  public static final int PROC_SPACE_TERM = (int)' ';
  public static final int PROC_TAB_TERM = (int)'\t';
  public static final int PROC_LINE_TERM = (int)'\n';
  public static final int PROC_COMBINE = 0x100;
  public static final int PROC_OUT_LONG = 0x2000;
  private static final int[] READ_LONG_FORMAT = new int[] {
    PROC_SPACE_TERM|PROC_OUT_LONG
  };
  private static final int[] PROCESS_STATS_FORMAT = new int[] {
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM,
    PROC_SPACE_TERM|PROC_OUT_LONG,                  // 13: utime
    PROC_SPACE_TERM|PROC_OUT_LONG                   // 14: stime
  };
  private static final int[] PROCESS_TOTAL_STATS_FORMAT = new int[] {
    PROC_SPACE_TERM,
    PROC_SPACE_TERM|PROC_OUT_LONG,
    PROC_SPACE_TERM|PROC_OUT_LONG,
    PROC_SPACE_TERM|PROC_OUT_LONG,
    PROC_SPACE_TERM|PROC_OUT_LONG,
    PROC_SPACE_TERM|PROC_OUT_LONG,
    PROC_SPACE_TERM|PROC_OUT_LONG,
    PROC_SPACE_TERM|PROC_OUT_LONG,
  };
  private static final int[] PROC_MEMINFO_FORMAT = new int[] {
    PROC_SPACE_TERM|PROC_COMBINE, PROC_SPACE_TERM|PROC_OUT_LONG, PROC_LINE_TERM,
    PROC_SPACE_TERM|PROC_COMBINE, PROC_SPACE_TERM|PROC_OUT_LONG, PROC_LINE_TERM,
    PROC_SPACE_TERM|PROC_COMBINE, PROC_SPACE_TERM|PROC_OUT_LONG, PROC_LINE_TERM,
    PROC_SPACE_TERM|PROC_COMBINE, PROC_SPACE_TERM|PROC_OUT_LONG, PROC_LINE_TERM,
  };

  public static final int INDEX_USER_TIME = 0;
  public static final int INDEX_SYS_TIME = 1;
  public static final int INDEX_TOTAL_TIME = 2;

  public static final int INDEX_MEM_TOTAL = 0;
  public static final int INDEX_MEM_FREE = 1;
  public static final int INDEX_MEM_BUFFERS = 2;
  public static final int INDEX_MEM_CACHED = 3;

  /* We are going to take advantage of the hidden API within Process.java that
   * makes use of JNI so that we can perform the top task efficiently.
   */
  private Field fieldUid;
  private Method methodGetUidForPid;
  private Method methodGetPids;
  private Method methodReadProcFile;
  private Method methodGetProperty;

  private long[] readBuf;

  @SuppressWarnings("unchecked")
  private SystemInfo() {
    try {
      fieldUid = ActivityManager.RunningAppProcessInfo.class.getField("uid");
    } catch(NoSuchFieldException e) {
      /* API level 3 doesn't have this field unfortunately. */
    }
    try {
      methodGetUidForPid = Process.class.getMethod("getUidForPid", int.class);
    } catch(NoSuchMethodException e) {
      Log.w(TAG, "Could not access getUidForPid method");
    }
    try {
      methodGetPids = Process.class.getMethod("getPids", String.class,
                                              int[].class);
    } catch(NoSuchMethodException e) {
      Log.w(TAG, "Could not access getPids method");
    }
    try {
      methodReadProcFile = Process.class.getMethod("readProcFile", String.class,
          int[].class, String[].class, long[].class, float[].class);
    } catch(NoSuchMethodException e) {
      Log.w(TAG, "Could not access readProcFile method");
    }
    try {
      Class classSystemProperties = Class.forName("android.os.SystemProperties");
      methodGetProperty = classSystemProperties.getMethod("get", String.class);
    } catch(NoSuchMethodException e) {
      Log.w(TAG, "Could not access SystemProperties.get");
    } catch(ClassNotFoundException e) {
      Log.w(TAG, "Could not find class android.os.SystemProperties");
    }
    readBuf = new long[1];
  }
  
  public int getUidForPid(int pid) {
    if(methodGetUidForPid != null) try {
      return (Integer)methodGetUidForPid.invoke(null, pid);
    } catch(InvocationTargetException e) {
      Log.w(TAG, "Call to getUidForPid failed");
    } catch(IllegalAccessException e) {
      Log.w(TAG, "Call to getUidForPid failed");
    } else try {
      BufferedReader rdr = new BufferedReader(new InputStreamReader(
                        new FileInputStream("/proc/" + pid + "/status")), 256);
      for(String line = rdr.readLine(); line != null; line = rdr.readLine()) {
        if(line.startsWith("Uid:")) {
          String tokens[] = line.substring(4).split("[ \t]+"); 
          String realUidToken = tokens[tokens[0].length() == 0 ? 1 : 0];
          try {
            return Integer.parseInt(realUidToken);
          } catch(NumberFormatException e) {
            return -1;
          }
        }
      }
    } catch(IOException e) {
      Log.w(TAG, "Failed to manually read in process uid");
    }
    return -1;
  }

  public int getUidForProcessInfo(
      ActivityManager.RunningAppProcessInfo app) {
    /* Try to access the uid field first if it is avaialble. Otherwise just
     * convert the pid to a uid.
     */
    if(fieldUid != null) try {
      return (Integer)fieldUid.get(app);
    } catch(IllegalAccessException e) {
    }
    return getUidForPid(app.pid);
  }

  /* lastPids can be null.  It is just used to avoid memory reallocation if
   * at all possible. Returns null on failure. If lastPids can hold the new
   * pid list the extra entries will be filled with -1 at the end.
   */
  public int[] getPids(int[] lastPids) {
    if(methodGetPids == null) return manualGetInts("/proc", lastPids);
    try {
      return (int[])methodGetPids.invoke(null, "/proc", lastPids);
    } catch(IllegalAccessException e) {
      Log.w(TAG, "Failed to get process cpu usage");
    } catch(InvocationTargetException e) {
      Log.w(TAG, "Exception thrown while getting cpu usage");
    }
    return null;
  }
  
  /* Gets a property on Android accessible through getprop. */
  public String getProperty(String property) {
  	if(methodGetProperty == null) return null;
  	try {
  	  return (String)methodGetProperty.invoke(null, property);
  	} catch(IllegalAccessException e) {
  	  Log.w(TAG, "Failed to get property");
  	} catch(InvocationTargetException e) {
  	  Log.w(TAG, "Exception thrown while getting property");
  	}
  	return null;
  }

  /* lastUids can be null.  It is just used to avoid memory reallocation if
   * at all possible. Returns null on failure. If lastUids can hold the new
   * uid list the extra entries will be filled with -1 at the end.
   */
  public int[] getUids(int[] lastUids) {
    if(methodGetPids == null) return manualGetInts("/proc/uid_stat", lastUids);
    try {
      return (int[])methodGetPids.invoke(null, "/proc/uid_stat", lastUids);
    } catch(IllegalAccessException e) {
      Log.w(TAG, "Failed to get process cpu usage");
    } catch(InvocationTargetException e) {
      Log.w(TAG, "Exception thrown while getting cpu usage");
    }
    return null;
  }

  private int[] manualGetInts(String dir, int[] lastInts) {
    File[] files = new File(dir).listFiles();
    int sz = files == null ? 0 : files.length;
    if(lastInts == null || lastInts.length < sz) {
      lastInts = new int[sz];
    } else if(2 * sz < lastInts.length) {
      lastInts = new int[sz];
    }
    int pos = 0;
    for(int i = 0; i < sz; i++) {
      try {
        int v = Integer.parseInt(files[i].getName());
        lastInts[pos++] = v;
      } catch(NumberFormatException e) {
      }
    }
    while(pos < lastInts.length) lastInts[pos++] = -1;
    return lastInts;
  }

  /* times should contain two elements.  times[INDEX_USER_TIME] will be filled
   * with the user time for this pid and times[INDEX_SYS_TIME] will be filled
   * with the sys time for this pid.  Returns true on sucess.
   */
  public boolean getPidUsrSysTime(int pid, long[] times) {
    if(methodReadProcFile == null) return false;
    try {
      return (Boolean)methodReadProcFile.invoke(
          null, "/proc/" + pid + "/stat",
          PROCESS_STATS_FORMAT, null, times, null);
    } catch(IllegalAccessException e) {
      Log.w(TAG, "Failed to get pid cpu usage");
    } catch(InvocationTargetException e) {
      Log.w(TAG, "Exception thrown while getting pid cpu usage");
    }
    return false;
  }

  /* times should contain seven elements.  times[INDEX_USER_TIME] will be filled
   * with the total user time, times[INDEX_SYS_TIME] will be filled
   * with the total sys time, and times[INDEX_TOTAL_TIME] will have the total
   * time (including idle cycles).  Returns true on success.
   */
  public boolean getUsrSysTotalTime(long[] times) {
    if(methodReadProcFile == null) return false;
    try {
      if((Boolean)methodReadProcFile.invoke(
          null, "/proc/stat",
          PROCESS_TOTAL_STATS_FORMAT, null, times, null)) {
        long usr = times[0] + times[1];
        long sys = times[2] + times[5] + times[6];
        long total = usr + sys + times[3] + times[4];
        times[INDEX_USER_TIME] = usr;
        times[INDEX_SYS_TIME] = sys;
        times[INDEX_TOTAL_TIME] = total;
        return true;
      }
    } catch(IllegalAccessException e) {
      Log.w(TAG, "Failed to get total cpu usage");
    } catch(InvocationTargetException e) {
      Log.w(TAG, "Exception thrown while getting total cpu usage");
    }
    return false;
  }

  /* mem should contain 4 elements.  mem[INDEX_MEM_TOTAL] will contain total
   * memory available in kb, mem[INDEX_MEM_FREE] will give the amount of free
   * memory in kb, mem[INDEX_MEM_BUFFERS] will give the size of kernel buffers
   * in kb, and mem[INDEX_MEM_CACHED] will give the size of kernel caches in kb.
   * Returns true on success.
   */
  public boolean getMemInfo(long[] mem) {
    if(methodReadProcFile == null) return false;
    try {
      if((Boolean)methodReadProcFile.invoke(
          null, "/proc/meminfo",
          PROC_MEMINFO_FORMAT, null, mem, null)) {
        return true;
      }
    } catch(IllegalAccessException e) {
      Log.w(TAG, "Failed to get mem info");
    } catch(InvocationTargetException e) {
      Log.w(TAG, "Exception thrown while getting mem info");
    }
    return false;
  }

  /* Returns -1 on failure. */
  public long readLongFromFile(String file) {
    if(methodReadProcFile == null) return -1;
    try {
      if((Boolean)methodReadProcFile.invoke(
          null, file, READ_LONG_FORMAT, null, readBuf, null)) {
        return readBuf[0];
      }
    } catch(IllegalAccessException e) {
      Log.w(TAG, "Failed to get pid cpu usage");
    } catch(InvocationTargetException e) {
      Log.w(TAG, "Exception thrown while getting pid cpu usage");
    }
    return -1L;
  }

  SparseArray<UidCacheEntry> uidCache = new SparseArray<UidCacheEntry>();

  public synchronized String getAppId(int uid, PackageManager pm) {
    UidCacheEntry cacheEntry = uidCache.get(uid);
    if(cacheEntry == null) {
      cacheEntry = new UidCacheEntry();
      uidCache.put(uid, cacheEntry);
    }
    cacheEntry.clearIfExpired();
    if(cacheEntry.getAppId() != null) {
      return cacheEntry.getAppId();
    }
    String result = getAppIdNoCache(uid, pm);
    cacheEntry.setAppId(result);
    return result;
  }

  private String getAppIdNoCache(int uid, PackageManager pm) {
    if(uid < SystemInfo.AID_APP) {
      Log.e(TAG, "Only pass application uids to getAppId");
      return null;
    }
    int versionCode = -1;
    String[] packages = pm.getPackagesForUid(uid);
    if(packages != null) for(String packageName : packages) {
      try {
        PackageInfo info = pm.getPackageInfo(packageName, 0);
        versionCode = info.versionCode;
      } catch(PackageManager.NameNotFoundException e) {
      }
    }
    String name = pm.getNameForUid(uid);
    name = name == null ? "none" : name;
    return pm.getNameForUid(uid) + "@" + versionCode;
  }

  public synchronized String getUidName(int uid, PackageManager pm) {
    UidCacheEntry cacheEntry = uidCache.get(uid);
    if(cacheEntry == null) {
      cacheEntry = new UidCacheEntry();
      uidCache.put(uid, cacheEntry);
    }
    cacheEntry.clearIfExpired();
    if(cacheEntry.getName() != null) {
      return cacheEntry.getName();
    }
    String result = getUidNameNoCache(uid, pm);
    cacheEntry.setName(result);
    return result;
  }

  private String getUidNameNoCache(int uid, PackageManager pm) {
    switch(uid) {
      case AID_ROOT:
        return "Kernel";
      case AID_SYSTEM:
        return "System";
      case AID_RADIO:
        return "Radio Subsystem";
      case AID_BLUETOOTH:
        return "Bluetooth Subsystem";
      case AID_GRAPHICS:
        return "Graphics Devices";
      case AID_INPUT:
        return "Input Devices";
      case AID_AUDIO:
        return "Audio Devices";
      case AID_CAMERA:
        return "Camera Devices"; case AID_LOG:
        return "Log Devices";
      case AID_COMPASS:
        return "Compass Device (e.g. akmd)";
      case AID_MOUNT:
        return "Mount";
      case AID_WIFI:
        return "Wifi Subsystem";
      case AID_ADB:
        return "Android Debug Bridge";
      case AID_INSTALL:
        return "Install";
      case AID_MEDIA:
        return "Media Server";
      case AID_DHCP:
        return "DHCP Client";
      case AID_SHELL:
        return "Debug Shell";
      case AID_CACHE:
        return "Cache Access";
      case AID_DIAG:
        return "Diagnostics";
    }
    if(uid < AID_APP) {
      return "sys_" + uid;
    }

    String[] packages = pm.getPackagesForUid(uid);
    if(packages != null) for(String packageName : packages) {
      try {
        PackageInfo info = pm.getPackageInfo(packageName, 0);
        CharSequence label = info.applicationInfo.loadLabel(pm);
        if(label != null) {
          return label.toString();
        }
      } catch(PackageManager.NameNotFoundException e) {
      }
    }
    String uidName = pm.getNameForUid(uid);
    if(uidName != null) {
      return uidName;
    }
    return "app_" + uid;
  }

  public synchronized Drawable getUidIcon(int uid, PackageManager pm) {
    UidCacheEntry cacheEntry = uidCache.get(uid);
    if(cacheEntry == null) {
      cacheEntry = new UidCacheEntry();
      uidCache.put(uid, cacheEntry);
    }
    cacheEntry.clearIfExpired();
    if(cacheEntry.getIcon() != null) {
      return cacheEntry.getIcon();
    }
    Drawable result = getUidIconNoCache(uid, pm);
    cacheEntry.setIcon(result);
    return result;
  }

  public Drawable getUidIconNoCache(int uid, PackageManager pm) {
    String[] packages = pm.getPackagesForUid(uid);
    if(packages != null) for (int i = 0; i < packages.length; i++) {
      try {
        ApplicationInfo ai = pm.getApplicationInfo(packages[i], 0);
        if(ai.icon != 0) {
          return ai.loadIcon(pm);
        }
      } catch(PackageManager.NameNotFoundException e) {
      }
    }
    return pm.getDefaultActivityIcon();
  }

  public synchronized void voidUidCache(int uid) {
    uidCache.remove(uid);
  }

  private static class UidCacheEntry {
    private static long EXPIRATION_TIME = 1000 * 60 * 10; // 10 minutes

    private String appId;
    private String name;
    private Drawable icon;
    private long updateTime;

    public UidCacheEntry() {
      updateTime = -1;
    }

    public String getAppId() {
      return appId;
    }

    public void setAppId(String appId) {
      this.appId = appId;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
      if(updateTime == -1) {
        updateTime = SystemClock.elapsedRealtime();
      }
    }

    public Drawable getIcon() {
      return icon;
    }

    public void setIcon(Drawable icon) {
      this.icon = icon;
      if(updateTime == -1) {
        updateTime = SystemClock.elapsedRealtime();
      }
    }

    public void clearIfExpired() {
      if(updateTime != -1 &&
         updateTime + EXPIRATION_TIME < SystemClock.elapsedRealtime()) {
        updateTime = -1;
        name = null;
        icon = null;
      }
    }
  }
}

