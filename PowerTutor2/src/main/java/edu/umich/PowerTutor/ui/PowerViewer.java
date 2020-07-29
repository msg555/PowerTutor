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
import org.achartengine.chart.CubicLineChart;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import edu.umich.PowerTutor.service.ICounterService;
import edu.umich.PowerTutor.service.PowerEstimator;
import edu.umich.PowerTutor.service.UMLoggerService;
import edu.umich.PowerTutor.util.SystemInfo;

public class PowerViewer extends Activity {
  private static final String TAG = "PowerViewer";

  private SharedPreferences prefs;
  private int uid;

  private int components;
  private String[] componentNames;
  private int[] componentsMaxPower;
  private int noUidMask;
  private boolean collecting;

  private ValueCollector[] collectors;

  private Intent serviceIntent;
  private CounterServiceConnection conn;
  private ICounterService counterService;

  private Handler handler;
  private LinearLayout chartLayout;

  public void refreshView() {
    if(counterService == null) {
      TextView loadingText = new TextView(this);
      loadingText.setText("Waiting for profiler service...");
      loadingText.setGravity(Gravity.CENTER);
      setContentView(loadingText);
      return;
    }

    chartLayout = new LinearLayout(this);
    chartLayout.setOrientation(LinearLayout.VERTICAL);

    if(uid == SystemInfo.AID_ALL) {
      /* If we are reporting global power usage then just set noUidMask to 0 so
       * that all components get displayed.
       */
      noUidMask = 0;
    }
    components = 0;
    for(int i = 0; i < componentNames.length; i++) {
      if((noUidMask & 1 << i) == 0) {
        components++;
      }
    }
    boolean showTotal = prefs.getBoolean("showTotalPower", false);
    collectors = new ValueCollector[(showTotal ? 1 : 0) + components];

    int pos = 0;
    for(int i = showTotal ? -1 : 0; i < componentNames.length; i++) {
      if(i != -1 && (noUidMask & 1 << i) != 0) {
        continue;
      }
      String name = i == -1 ? "Total" : componentNames[i];
      double mxPower = (i == -1 ? 2100.0 : componentsMaxPower[i]) * 1.05;

      XYSeries series = new XYSeries(name);
      XYMultipleSeriesDataset mseries = new XYMultipleSeriesDataset();
      mseries.addSeries(series);
      
      XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
      XYSeriesRenderer srenderer = new XYSeriesRenderer();
      renderer.setYAxisMin(0.0);
      renderer.setYAxisMax(mxPower);
      renderer.setYTitle(name + "(mW)");
      
      int clr = PowerPie.COLORS[(PowerPie.COLORS.length + i) %
                                 PowerPie.COLORS.length];
      srenderer.setColor(clr);
      srenderer.setFillBelowLine(true);
      srenderer.setFillBelowLineColor(((clr >> 1) & 0x7F7F7F) |
                                       (clr & 0xFF000000));
      renderer.addSeriesRenderer(srenderer);
      
      View chartView = new GraphicalView(this,
    		  new CubicLineChart(mseries, renderer, 0.5f));
      chartView.setMinimumHeight(100);
      chartLayout.addView(chartView);

      collectors[pos] = new ValueCollector(series, renderer, chartView, i);
      if(handler != null) {
        handler.post(collectors[pos]);
      }
      pos++;
    }

    /* We're giving 100 pixels per graph of vertical space for the chart view.
       If we don't specify a minimum height the chart view ends up having a
       height of 0 so this is important. */
    chartLayout.setMinimumHeight(100 * components);

    ScrollView scrollView = new ScrollView(this) {
      public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
        return true;
      }
    };

    scrollView.addView(chartLayout);
    scrollView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    setContentView(scrollView);
  }

  private class CounterServiceConnection implements ServiceConnection {
    public void onServiceConnected(ComponentName className, 
                                   IBinder boundService) {
      counterService = ICounterService.Stub.asInterface((IBinder)boundService);
      try {
        componentNames = counterService.getComponents();
        componentsMaxPower = counterService.getComponentsMaxPower();
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

    collecting = true;
    if(savedInstanceState != null) {
      collecting = savedInstanceState.getBoolean("collecting", true);
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
    if(collectors != null) for(int i = 0; i < components; i++) {
      handler.removeCallbacks(collectors[i]);
    }
    counterService = null;
    handler = null;
    collecting = true;
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean("collecting", collecting);
    outState.putStringArray("componentNames", componentNames);
    outState.putInt("noUidMask", noUidMask);
  }

  /* Let all of the UI graphs lay themselves out again. */
  private void stateChanged() {
    for(int i = 0; i < components; i++) {
      collectors[i].layout();
    }
  }

  private static final int MENU_OPTIONS = 0;
  private static final int MENU_TOGGLE_COLLECTING = 1;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, MENU_OPTIONS, 0, "Options");
    menu.add(0, MENU_TOGGLE_COLLECTING, 0, "");
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.findItem(MENU_TOGGLE_COLLECTING).setTitle(
        collecting ? "Pause" : "Resume");
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch(item.getItemId()) {
      case MENU_OPTIONS:
        startActivity(new Intent(this, ViewerPreferences.class));
        return true;
      case MENU_TOGGLE_COLLECTING:
        collecting = !collecting;
        if(handler != null) {
          if(collecting) for(int i = 0; i < components; i++) {
            collectors[i].reset();
            handler.post(collectors[i]);
          } else for(int i = 0; i < components; i++) {
            handler.removeCallbacks(collectors[i]);
          }
        }
        break;
    }
    return false;
  }

  public class ValueCollector implements Runnable {
    private XYSeries series;
    private XYMultipleSeriesRenderer renderer;
    private View chartView;
    
    private int componentId;
    private long lastTime;

    int[] values;

    private boolean readHistory;

    public ValueCollector(XYSeries series, XYMultipleSeriesRenderer renderer,
                          View chartView, int componentId) {
      this.series = series;
      this.renderer = renderer;
      this.chartView = chartView;
      this.componentId = componentId;
      lastTime = SystemClock.elapsedRealtime();
      layout();
    }

    public void layout() {
      int numVals = Integer.parseInt(prefs.getString("viewNumValues_s", "60"));
      values = new int[numVals];
      
      renderer.clearXTextLabels();
      renderer.setXAxisMin(0);
      renderer.setXAxisMax(numVals - 1);
      renderer.addXTextLabel(numVals - 1, "" + numVals);
      renderer.setXLabels(0);
      for(int j = 0; j < 10; j++) {
        renderer.addXTextLabel(numVals * j / 10, "" + (1 + numVals * j / 10));
      }

      reset();
    }

    /** Restart points collecting from zero. */
    public void reset() {
      series.clear();
      readHistory = true;
    }

    public void run() {
      int numVals = Integer.parseInt(prefs.getString("viewNumValues_s", "60"));
      if(counterService != null) try {
        if(readHistory) {
          values = counterService.getComponentHistory(numVals,
                                                      componentId, uid);
          readHistory = false;
        } else {
          for(int i = numVals - 1; i > 0; i--) {
            values[i] = values[i - 1];
          }
          values[0] = counterService.getComponentHistory(1, componentId,
                                                         uid)[0];
        }
      } catch(RemoteException e) {
        Log.w(TAG, "Failed to get data from service");
        for(int i = 0; i < numVals; i++) {
          values[i] = 0;
        }
      }

      series.clear();
      for(int i = 0; i < numVals; i++) {
        series.add(i, values[i]);
      }
      
      long curTime = SystemClock.elapsedRealtime();
      long tryTime = lastTime + PowerEstimator.ITERATION_INTERVAL *
                     (long)Math.max(1, 1 + (curTime - lastTime) /
                                    PowerEstimator.ITERATION_INTERVAL);
      if(handler != null) {
        handler.postDelayed(this, tryTime - curTime);
      }
      
      chartView.invalidate();
    }
  };
}
