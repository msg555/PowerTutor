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

package edu.umich.PowerTutor.ui;

import edu.umich.PowerTutor.R;
import edu.umich.PowerTutor.service.ICounterService;
import edu.umich.PowerTutor.service.PowerEstimator;
import edu.umich.PowerTutor.service.UMLoggerService;
import edu.umich.PowerTutor.service.UidInfo;
import edu.umich.PowerTutor.util.Counter;
import edu.umich.PowerTutor.util.Recycler;
import edu.umich.PowerTutor.util.SystemInfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;

public class PowerTop extends Activity implements Runnable {
  private static final String TAG = "PowerTop";
  private static final double HIDE_UID_THRESHOLD = 0.1;

  public static final int KEY_CURRENT_POWER = 0;
  public static final int KEY_AVERAGE_POWER = 1;
  public static final int KEY_TOTAL_ENERGY = 2;
  private static final CharSequence[] KEY_NAMES = { "Current power",
      "Average power", "Energy usage"};

  private SharedPreferences prefs;
  private int noUidMask;
  private String[] componentNames;

  private Intent serviceIntent;
  private CounterServiceConnection conn;
  private ICounterService counterService;
  private Handler handler;

  private LinearLayout topGroup;
  private LinearLayout filterGroup;
  private LinearLayout mainView;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefs = PreferenceManager.getDefaultSharedPreferences(this);
    serviceIntent = new Intent(this, UMLoggerService.class);
    conn = new CounterServiceConnection();
    if(savedInstanceState != null) {
      componentNames = savedInstanceState.getStringArray("componentNames");
      noUidMask = savedInstanceState.getInt("noUidMask");
    }

    topGroup = new LinearLayout(this);
    topGroup.setOrientation(LinearLayout.VERTICAL);
    ScrollView scrollView = new ScrollView(this);
    scrollView.addView(topGroup);
    filterGroup = new LinearLayout(this);
    filterGroup.setOrientation(LinearLayout.HORIZONTAL);
    filterGroup.setMinimumHeight(50);
    mainView = new LinearLayout(this);
    mainView.setOrientation(LinearLayout.VERTICAL);
    mainView.addView(filterGroup);
    mainView.addView(scrollView);
  }

  @Override
  protected void onResume() {
    super.onResume();
    handler = new Handler();
    handler.postDelayed(this, 100);
    getApplicationContext().bindService(serviceIntent, conn, 0);

    refreshView();
  }

  @Override
  protected void onPause() {
    super.onPause();
    getApplicationContext().unbindService(conn);
    handler.removeCallbacks(this);
    handler = null;
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putStringArray("componentNames", componentNames);
    outState.putInt("noUidMask", noUidMask);
  }

  private static final int MENU_KEY = 0;
  private static final int MENU_WINDOW = 1;
  private static final int DIALOG_KEY = 0;
  private static final int DIALOG_WINDOW = 1;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, MENU_KEY, 0, "Display Type");
    menu.add(0, MENU_WINDOW, 0, "Time Span");
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    /* We need to make sure that the user can't cause any of the dialogs to be
     * created before we have contacted the Power Tutor service to get the
     * component names and such.
     */
    for(int i = 0; i < menu.size(); i++) {
      menu.getItem(i).setEnabled(counterService != null);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch(item.getItemId()) {
      case MENU_KEY:
        showDialog(DIALOG_KEY);
        return true;
      case MENU_WINDOW:
        showDialog(DIALOG_WINDOW);
        return true;
    }
    return false;
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    switch(id) {
      case DIALOG_KEY:
        builder.setTitle("Select sort key");
        builder.setItems(KEY_NAMES, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
              prefs.edit().putInt("topKeyId", item).commit();
            }
        });
        return builder.create();
      case DIALOG_WINDOW:
        builder.setTitle("Select window type");
        builder.setItems(Counter.WINDOW_NAMES,
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
              prefs.edit().putInt("topWindowType", item).commit();
            }
        });
        return builder.create();
    }
    return null;
  }

  private void refreshView() {
    if(counterService == null) {
      TextView loadingText = new TextView(this);
      loadingText.setText("Waiting for profiler service...");
      loadingText.setGravity(Gravity.CENTER);
      setContentView(loadingText);
      return;
    }

    int keyId = prefs.getInt("topKeyId", KEY_TOTAL_ENERGY);
    try {
      byte[] rawUidInfo = counterService.getUidInfo(
          prefs.getInt("topWindowType", Counter.WINDOW_TOTAL),
          noUidMask | prefs.getInt("topIgnoreMask", 0));
      if(rawUidInfo != null) {
        UidInfo[] uidInfos = (UidInfo[])new ObjectInputStream(
            new ByteArrayInputStream(rawUidInfo)).readObject();
        double total = 0;
        for(UidInfo uidInfo : uidInfos) {
          if(uidInfo.uid == SystemInfo.AID_ALL) continue;
          switch(keyId) {
            case KEY_CURRENT_POWER:
              uidInfo.key = uidInfo.currentPower;
              uidInfo.unit = "W";
              break;
            case KEY_AVERAGE_POWER:
              uidInfo.key = uidInfo.totalEnergy /
                  (uidInfo.runtime == 0 ? 1 : uidInfo.runtime);
              uidInfo.unit = "W";
              break;
            case KEY_TOTAL_ENERGY:
              uidInfo.key = uidInfo.totalEnergy;
              uidInfo.unit = "J";
              break;
            default:
              uidInfo.key = uidInfo.currentPower;
              uidInfo.unit = "W";
          }
          total += uidInfo.key;
        }
        if(total == 0) total = 1;
        for(UidInfo uidInfo : uidInfos) {
          uidInfo.percentage = 100.0 * uidInfo.key / total;
        }
        Arrays.sort(uidInfos);

        int sz = 0;
        for(int i = 0; i < uidInfos.length; i++) {
          if(uidInfos[i].uid == SystemInfo.AID_ALL ||
             uidInfos[i].percentage < HIDE_UID_THRESHOLD) {
            continue;
          }
          UidPowerView powerView;
          if(sz < topGroup.getChildCount()) {
            powerView = (UidPowerView)topGroup.getChildAt(sz);
          } else {
            powerView = UidPowerView.obtain(this, getIntent());
            topGroup.addView(powerView);
          }
          powerView.setBackgroundDrawable(null);
          powerView.setBackgroundColor((sz & 1) == 0 ? 0xFF000000 :
                                       0xFF222222);
          powerView.init(uidInfos[i], keyId);
          sz++;
        }
        for(int i = sz; i < topGroup.getChildCount(); i++) {
          UidPowerView powerView = (UidPowerView)topGroup.getChildAt(i);
          powerView.recycle();
        }
        topGroup.removeViews(sz, topGroup.getChildCount() - sz);
      }
    } catch(IOException e) {
    } catch(RemoteException e) {
    } catch(ClassNotFoundException e) {
    } catch(ClassCastException e) {
    }
    setContentView(mainView);
    if(keyId == KEY_CURRENT_POWER) {
      setTitle(KEY_NAMES[keyId]);
    } else {
      setTitle(KEY_NAMES[keyId] + " over " +
               Counter.WINDOW_DESCS[prefs.getInt("topWindowType",
                                                 Counter.WINDOW_TOTAL)]);
    }
  }

  public void run() {
    refreshView();
    if(handler != null) {
      handler.postDelayed(this, 2 * PowerEstimator.ITERATION_INTERVAL);
    }
  }

  private static class UidPowerView extends LinearLayout {
    private static Recycler<UidPowerView> recycler =
        new Recycler<UidPowerView>();
    private static DecimalFormat formatter = new DecimalFormat("0.0");

    public static UidPowerView obtain(Activity activity, Intent startIntent) {
      UidPowerView result = recycler.obtain();
      if(result == null) return new UidPowerView(activity, startIntent);
      return result;
    }

    public void recycle() {
      recycler.recycle(this);
    }

    private UidInfo uidInfo;
    private String name;
    private Drawable icon;

    private ImageView imageView;
    private TextView textView;

    private UidPowerView(final Activity activity, final Intent startIntent) {
      super(activity);
      setMinimumHeight(50);
      setOrientation(LinearLayout.HORIZONTAL);
      imageView = new ImageView(activity);
      imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
      imageView.setAdjustViewBounds(true);
      imageView.setMaxHeight(40);
      imageView.setMaxWidth(40);
      imageView.setMinimumWidth(50);
      imageView.setLayoutParams(new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.FILL_PARENT));
      textView = new TextView(activity);
      textView.setGravity(Gravity.CENTER_VERTICAL);
      textView.setLayoutParams(new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.FILL_PARENT,
          ViewGroup.LayoutParams.FILL_PARENT));
      addView(imageView);
      addView(textView);
      setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          Intent viewIntent = new Intent(v.getContext(), PowerTabs.class);
          viewIntent.putExtras(startIntent);
          viewIntent.putExtra("uid", uidInfo.uid);
          activity.startActivityForResult(viewIntent, 0);
        }
      });
      setFocusable(true);
    }

    public void init(UidInfo uidInfo, int keyType) {
      SystemInfo sysInfo = SystemInfo.getInstance();
      this.uidInfo = uidInfo;
      PackageManager pm = getContext().getPackageManager();
      name = sysInfo.getUidName(uidInfo.uid, pm);
      icon = sysInfo.getUidIcon(uidInfo.uid, pm);
      imageView.setImageDrawable(icon);
      String prefix;
      if(uidInfo.key > 1e12) {
        prefix = "G";
        uidInfo.key /= 1e12;
      } else if(uidInfo.key > 1e9) {
        prefix = "M";
        uidInfo.key /= 1e9;
      } else if(uidInfo.key > 1e6) {
        prefix = "k";
        uidInfo.key /= 1e6;
      } else if(uidInfo.key > 1e3) {
        prefix = "";
        uidInfo.key /= 1e3;
      } else {
        prefix = "m";
      }
      long secs = (long)Math.round(uidInfo.runtime);
      
      textView.setText(String.format("%1$.1f%% [%3$d:%4$02d:%5$02d] %2$s\n" +
          "%6$.1f %7$s%8$s",
          uidInfo.percentage, name, secs / 60 / 60, (secs / 60) % 60,
          secs % 60, uidInfo.key, prefix, uidInfo.unit));
    }
  }

  private class CounterServiceConnection implements ServiceConnection {
    public void onServiceConnected(ComponentName className,
                                   IBinder boundService ) {
      counterService = ICounterService.Stub.asInterface((IBinder)boundService);
      try {
        componentNames = counterService.getComponents();
        noUidMask = counterService.getNoUidMask();
        filterGroup.removeAllViews();
        for(int i = 0; i < componentNames.length; i++) {
          int ignMask = prefs.getInt("topIgnoreMask", 0);
          if((noUidMask & 1 << i) != 0) continue;
          final TextView filterToggle = new TextView(PowerTop.this);
          final int index = i;
          filterToggle.setText(componentNames[i]);
          filterToggle.setGravity(Gravity.CENTER);
          filterToggle.setTextColor((ignMask & 1 << index) == 0 ?
                                    0xFFFFFFFF : 0xFF888888);
          filterToggle.setBackgroundColor(
              filterGroup.getChildCount() % 2 == 0 ? 0xFF444444 : 0xFF555555);
          filterToggle.setFocusable(true);
          filterToggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
              int ignMask = prefs.getInt("topIgnoreMask", 0);
              if((ignMask & 1 << index) == 0) {
                prefs.edit().putInt("topIgnoreMask", ignMask | 1 << index)
                            .commit();
                filterToggle.setTextColor(0xFF888888);
              } else {
                prefs.edit().putInt("topIgnoreMask", ignMask & ~(1 << index))
                            .commit();
                filterToggle.setTextColor(0xFFFFFFFF);
              }
            }
          });
          filterGroup.addView(filterToggle,
              new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                      ViewGroup.LayoutParams.FILL_PARENT, 1f));
        }
      } catch(RemoteException e) {
        counterService = null;
      }
    }

    public void onServiceDisconnected(ComponentName className) {
      counterService = null;
      getApplicationContext().unbindService(conn);
      getApplicationContext().bindService(serviceIntent, conn, 0);
      Log.w(TAG, "Unexpectedly lost connection to service");
    }
  }
}

