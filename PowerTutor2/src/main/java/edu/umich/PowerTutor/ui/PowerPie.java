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

import org.achartengine.GraphicalView;
import org.achartengine.chart.PieChart;
import org.achartengine.model.CategorySeries;
import org.achartengine.renderer.DefaultRenderer;
import org.achartengine.renderer.SimpleSeriesRenderer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;
import edu.umich.PowerTutor.service.ICounterService;
import edu.umich.PowerTutor.service.PowerEstimator;
import edu.umich.PowerTutor.service.UMLoggerService;
import edu.umich.PowerTutor.util.Counter;
import edu.umich.PowerTutor.util.SystemInfo;

public class PowerPie extends Activity {
  private static final String TAG = "PowerPie";

  private SharedPreferences prefs;
  private int uid;

  private String[] componentNames;
  private int noUidMask;

  private Runnable collector;

  private Intent serviceIntent;
  private CounterServiceConnection conn;
  private ICounterService counterService;
  private Handler handler;

  private TextView displayText;

  public static final int[] COLORS = new int[] {
      Color.BLUE, Color.GREEN, Color.MAGENTA, Color.YELLOW,
      Color.RED, Color.LTGRAY, Color.DKGRAY, Color.CYAN
  };

  public void refreshView() {
    if(counterService == null) {
      TextView loadingText = new TextView(this);
      loadingText.setText("Waiting for profiler service...");
      loadingText.setGravity(Gravity.CENTER);
      setContentView(loadingText);
      return;
    }

    if(uid == SystemInfo.AID_ALL) {
      /* If we are reporting global power usage then just set noUidMask to 0 so
       * that all components get displayed.
       */
      noUidMask = 0;
    }

    displayText = new TextView(this);
    displayText.setGravity(Gravity.CENTER);
    updateDisplayText();

    final CategorySeries series = new CategorySeries("");
    final DefaultRenderer renderer = new DefaultRenderer();
    renderer.setLabelsTextSize(15);
    renderer.setLegendTextSize(15);
    renderer.setMargins(new int[] { 5, 50, 5, 50 });

    PieChart pieChart = new PieChart(series, renderer);
    final GraphicalView chartView = new GraphicalView(this, pieChart);

    /* The collector is responsible for periodically updating the screen with
     * new energy usage information for the current uid.
     */
    collector = new Runnable() {
      public void run() {
        try {
          long[] totals = counterService.getTotals(uid,
              prefs.getInt("pieWindowType", 0));
          long sumTotal = 0;
          for(int i = 0; i < totals.length; i++) {
            totals[i] = totals[i] * PowerEstimator.ITERATION_INTERVAL / 1000;
            sumTotal += totals[i];
          }
          int index = 0;
          if(sumTotal < 1e-7) {
            series.set(0, "No data", 1);
          } else for(int i = 0; i < totals.length; i++) {
            if((noUidMask & 1 << i) != 0) {
              continue;
            }
            String prefix;
            double val = totals[i];
            if(val > 1e12) {
              prefix = "G";
              val /= 1e12;
            } else if(val > 1e9) {
              prefix = "M";
              val /= 1e9;
            } else if(val > 1e6) {
              prefix = "k";
              val /= 1e6;
            } else if(val > 1e3) {
              prefix = "";
              val /= 1e3;
            } else {
              prefix = "m";
            }

            String label = String.format("%1$s %2$.1f %3$sJ",
                    componentNames[i], val, prefix);
            if(series.getItemCount() == index) {
              SimpleSeriesRenderer r = new SimpleSeriesRenderer();
              r.setColor(COLORS[i]);
              renderer.addSeriesRenderer(r);
                
              series.add(label, totals[i]);
            } else {
              series.set(index, label, totals[i]);
            }
            index++;
          }
          chartView.invalidate();
        } catch(RemoteException e) {
          Log.w(TAG, "Failed to contact power tutor profiling service");
        }
        if(handler != null) {
          handler.postDelayed(this, 2 * PowerEstimator.ITERATION_INTERVAL);
        }
      }
    };
    if(handler != null) {
      handler.post(collector);
    }

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.addView(displayText);
    layout.addView(chartView);
    setContentView(layout);
  }

  public void updateDisplayText() {
    displayText.setText("Displaying energy usage over " +
        Counter.WINDOW_DESCS[prefs.getInt("pieWindowType", 0)] + " for " +
        (uid == SystemInfo.AID_ALL ? " the entire phone." :
        SystemInfo.getInstance().getUidName(uid, getPackageManager()) + "."));
  }

  class CounterServiceConnection implements ServiceConnection {
    public void onServiceConnected(ComponentName className, 
                                   IBinder boundService ) {
      counterService = ICounterService.Stub.asInterface((IBinder)boundService);
      try {
        componentNames = counterService.getComponents();
        noUidMask = counterService.getNoUidMask();
        refreshView();
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

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefs = PreferenceManager.getDefaultSharedPreferences(this);
    uid = getIntent().getIntExtra("uid", SystemInfo.AID_ALL);

    if(savedInstanceState != null) {
      componentNames = savedInstanceState.getStringArray("componentNames");
      noUidMask = savedInstanceState.getInt("noUidMask");
    }

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
      handler = null;
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putStringArray("componentNames", componentNames);
    outState.putInt("noUidMask", noUidMask);
  }

  private static final int MENU_WINDOW = 0;
  private static final int DIALOG_WINDOW = 0;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
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
      case DIALOG_WINDOW:
        builder.setTitle("Select window type");
        builder.setItems(Counter.WINDOW_NAMES,
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
              prefs.edit().putInt("pieWindowType", item).commit();
              updateDisplayText();
            }
        });
        return builder.create();
    }
    return null;
  }
}

