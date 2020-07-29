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
import edu.umich.PowerTutor.phone.PhoneSelector;
import edu.umich.PowerTutor.service.ICounterService;
import edu.umich.PowerTutor.service.PowerEstimator;
import edu.umich.PowerTutor.service.UMLoggerService;
import edu.umich.PowerTutor.util.Counter;
import edu.umich.PowerTutor.util.BatteryStats;
import edu.umich.PowerTutor.util.SystemInfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ListView;

import java.util.ArrayList;
import java.io.File;

public class MiscView extends Activity {
  private static final String TAG = "MiscView";

  private SharedPreferences prefs;
  private int uid;

  private Runnable collector;

  private Intent serviceIntent;
  private CounterServiceConnection conn;
  private ICounterService counterService;
  private Handler handler;

  private BatteryStats batteryStats;

  private String[] componentNames;

  public void refreshView() {
    final ListView listView = new ListView(this);

    ArrayAdapter adapter = new ArrayAdapter(this, 0) {
      public View getView(int position, View convertView, ViewGroup parent) {
        View itemView = getLayoutInflater()
            .inflate(R.layout.misc_item_layout, listView, false);
        TextView title = (TextView)itemView.findViewById(R.id.title);
        TextView summary = (TextView)itemView.findViewById(R.id.summary);
        LinearLayout widgetGroup =
            (LinearLayout)itemView.findViewById(R.id.widget_frame);
        InfoItem item = (InfoItem)getItem(position);
        item.initViews(title, summary, widgetGroup);
        item.setupView();
        return itemView;
      }
    };

    final ArrayList<InfoItem> allItems = new ArrayList<InfoItem>();
    allItems.add(new UidItem());
    allItems.add(new PackageItem());
    allItems.add(new OLEDItem());
    allItems.add(new InstantPowerItem());
    allItems.add(new AveragePowerItem());
    allItems.add(new CurrentItem());
    allItems.add(new ChargeItem());
    allItems.add(new VoltageItem());
    allItems.add(new TempItem());

    for(InfoItem inf : allItems) {
      if(inf.available()) {
        adapter.add(inf);
      }
    }

    listView.setAdapter(adapter);
    setContentView(listView);

    collector = new Runnable() {
      public void run() {
        for(InfoItem inf : allItems) {
          if(inf.available()) {
            inf.setupView();
          }
        }
        if(handler != null) {
          handler.postDelayed(this, 2 * PowerEstimator.ITERATION_INTERVAL);
        }
      }
    };
    if(handler != null) {
      handler.post(collector);
    }
  }

  class CounterServiceConnection implements ServiceConnection {
    public void onServiceConnected(ComponentName className, 
                                   IBinder boundService ) {
      counterService = ICounterService.Stub.asInterface((IBinder)boundService);
      try {
        componentNames = counterService.getComponents();
      } catch(RemoteException e) {
        componentNames = new String[0];
      }
      refreshView();
    }

    public void onServiceDisconnected(ComponentName className) {
      counterService = null;
      getApplicationContext().unbindService(conn);
      getApplicationContext().bindService(serviceIntent, conn, 0);
      Log.w(TAG, "Unexpectedly lost connection to service");
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefs = PreferenceManager.getDefaultSharedPreferences(this);
    uid = getIntent().getIntExtra("uid", SystemInfo.AID_ALL);
    if(savedInstanceState != null) {
      componentNames = savedInstanceState.getStringArray("componentNames");
    }
    batteryStats = BatteryStats.getInstance();
    serviceIntent = new Intent(this, UMLoggerService.class);
    conn = new CounterServiceConnection();
  }

  @Override
  protected void onResume() {
    super.onResume();
    handler = new Handler();
    getApplicationContext().bindService(serviceIntent, conn, 0);
    refreshView();
  }

  @Override
  protected void onPause() {
    super.onPause();
    getApplicationContext().unbindService(conn);
    if(collector != null) {
      handler.removeCallbacks(collector);
      collector = null;
      handler = null;
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putStringArray("componentNames", componentNames);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    return false;
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    return null;
  }

  private abstract class InfoItem {
    protected TextView title;
    protected TextView summary;
    protected TextView txt;

    public void initViews(TextView title, TextView summary,
                          LinearLayout widgetGroup) {
      this.title = title;
      this.summary = summary;
      txt = new TextView(MiscView.this);
      widgetGroup.addView(txt);
    }

    public abstract boolean available();

    public abstract void setupView();
  }

  private class CurrentItem extends InfoItem {
    public boolean available() {
      return uid == SystemInfo.AID_ALL && batteryStats.hasCurrent();
    }

    public void setupView() {
      if(txt == null) return;
      double current = batteryStats.getCurrent();
      if(current <= 0) {
        txt.setText(String.format("%1$.1f mA", -current * 1000));
      } else {
        double cp = batteryStats.getCapacity();
        if(0.01 <= cp && cp <= 0.99 && batteryStats.hasCharge()) {
          long time = (long)(batteryStats.getCharge() / cp * (1.0 - cp) /
                             current);
          txt.setText(String.format(
              "%1$.1f mA\n(Charge time %2$d:%3$02d:%4$02d)", current * 1000,
              time / 60 / 60, time / 60 % 60, time % 60));
        }
      }
      txt.setGravity(Gravity.CENTER);

      title.setText("Current");
      summary.setText("Battery current sensor reading");
    }
  }

  private class ChargeItem extends InfoItem {
    public boolean available() {
      return uid == SystemInfo.AID_ALL && batteryStats.hasCharge();
    }

    public void setupView() {
      if(txt == null) return;
      double charge = batteryStats.getCharge() / 60 / 60 * 1e3; //As->mAh
      double perc = batteryStats.getCapacity();
      if(perc < 0) {
        txt.setText(String.format("%1$.1f mAh", charge));
      } else {
        txt.setText(String.format("%1$.1f mAh\n(%2$.0f%%)",
                                  charge, 100 * perc));
      }
      txt.setGravity(Gravity.CENTER);

      title.setText("Charge");
      summary.setText("Battery charge sensor reading");
    }
  }

  private class InstantPowerItem extends InfoItem {
    private static final double POLY_WEIGHT = 0.10;

    public boolean available() {
      return true;
    }

    public void setupView() {
      if(txt == null) return;
      if(counterService != null) try {
        // Compute what we're going to call the temporal power usage.
        int count = 0;
        int[] history = counterService.getComponentHistory(
                            5 * 60, -1, uid);
        double weightedAvgPower = 0;
        for(int i = history.length - 1; i >= 0; i--) {
          if(history[i] != 0) {
            count++;
            weightedAvgPower *= 1.0 - POLY_WEIGHT;
            weightedAvgPower += POLY_WEIGHT * history[i] / 1000.0;
          }
        }
        if(count > 0) {
          double charge = batteryStats.getCharge();
          double volt = batteryStats.getVoltage();
          if(charge > 0 && volt > 0) {
            weightedAvgPower /= 1.0 - Math.pow(1.0 - POLY_WEIGHT, count);
            long time = (long)(charge * volt / weightedAvgPower);
            txt.setText(String.format("%1$.0f mW\n" +
                        "time: %2$d:%3$02d:%4$02d", weightedAvgPower * 1000.0,
                        time / 60 / 60, time / 60 % 60, time % 60));
          } else {
            txt.setText(String.format("%1$.0f mW", weightedAvgPower * 1000.0));
          }
        } else {
          txt.setText("No data");
        }
      } catch(RemoteException e) {
        txt.setText("Error");
      } else {
        txt.setText("No data");
      }

      txt.setGravity(Gravity.CENTER);
      title.setText("Current Power");
      summary.setText("Weighted average of power consumption over the last " +
                      "five minutes");
    }
  }

  private class AveragePowerItem extends InfoItem {
    public boolean available() {
      return true;
    }

    public void setupView() {
      if(txt == null) return;
      if(counterService != null) try {
        // Compute what we're going to call the temporal power usage.
        double power = 0;
        long[] means = counterService.getMeans(uid, Counter.WINDOW_TOTAL);
        if(means != null) for(long p : means) {
          power += p / 1000.0;
        }
        
        if(power > 0) {
          double charge = batteryStats.getCharge();
          double volt = batteryStats.getVoltage();
          if(charge > 0 && volt > 0) {
            long time = (long)(charge * volt / power);
            txt.setText(String.format("%1$.0f mW\n" +
                        "time: %2$d:%3$02d:%4$02d", power * 1000.0,
                        time / 60 / 60, time / 60 % 60, time % 60));
          } else {
            txt.setText(String.format("%1$.0f mW", power * 1000.0));
          }
        } else {
          txt.setText("No data");
        }
      } catch(RemoteException e) {
        txt.setText("Error");
      } else {
        txt.setText("No data");
      }

      txt.setGravity(Gravity.CENTER);
      title.setText("Average Power");
      summary.setText("Average power consumption since profiler started");
    }
  }

  private class VoltageItem extends InfoItem {
    public boolean available() {
      return uid == SystemInfo.AID_ALL && batteryStats.hasVoltage();
    }

    public void setupView() {
      if(txt == null) return;
      double voltage = batteryStats.getVoltage();
      txt.setText(String.format("%1$.2f V", voltage));
      txt.setGravity(Gravity.CENTER);

      title.setText("Voltage");
      summary.setText("Battery voltage sensor reading");
    }
  }

  private class TempItem extends InfoItem {
    public boolean available() {
      return uid == SystemInfo.AID_ALL && batteryStats.hasTemp();
    }

    public void setupView() {
      if(txt == null) return;
      double celcius = batteryStats.getTemp();
      double farenheit = 32 + celcius * 9.0 / 5.0;
      txt.setText(String.format("%1$.1f \u00b0C\n(%2$.1f \u00b0F)",
                                celcius, farenheit));
      txt.setGravity(Gravity.CENTER);

      title.setText("Battery Temperature");
      summary.setText("Battery temperature sensor reading");
    }
  }

  private class UidItem extends InfoItem {
    public boolean available() {
      return uid != SystemInfo.AID_ALL;
    }

    public void setupView() {
      if(txt == null) return;
      txt.setText("" + uid);
      txt.setGravity(Gravity.CENTER);

      title.setText("User ID");
      summary.setText("User ID for " + SystemInfo.getInstance().getUidName(uid,
                      getApplicationContext().getPackageManager()));
                        
    }
  }

  private class PackageItem extends InfoItem {
    public boolean available() {
      return uid >= SystemInfo.AID_APP;
    }

    public void setupView() {
      if(txt == null) return;
      txt.setText("");

      title.setText("Packages");

      PackageManager pm = getApplicationContext().getPackageManager();
      String[] packages = pm.getPackagesForUid(uid);
      if(packages != null) {
        StringBuilder buf = new StringBuilder();
        for(String packageName : packages) {
          if(buf.length() != 0) buf.append("\n");
          buf.append(packageName);
        }
        summary.setText(buf.toString());
      } else {
        summary.setText("(None)");
      }
    }
  }

  private class OLEDItem extends InfoItem {
    public boolean available() {
      if(uid < SystemInfo.AID_APP) return false;
      return PhoneSelector.hasOled();
    }

    public void setupView() {
      if(txt == null) return;

      txt.setText("No data");
      if(counterService != null) {
        try {
          long score = counterService.getUidExtra("OLEDSCORE", uid);
          if(score >= 0) {
            txt.setText("" + (100 - score));
          }
        } catch(RemoteException e) {
          Log.w(TAG, "Failed to request oled score information");
        }
      }

      title.setText("OLED Score");
      summary.setText("100 is highly efficient\n0 is very inefficient\n" +
                      "Independent of brightness");
    }
  }
}

