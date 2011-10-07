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
import edu.umich.PowerTutor.service.UMLoggerService;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.InflaterInputStream;
import java.io.BufferedOutputStream;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** The main view activity for PowerTutor*/
public class UMLogger extends Activity  {
  private static final String TAG = "UMLogger";

  public static final String CURRENT_VERSION = "1.2"; // Don't change this...

  public static final String SERVER_IP = "spidermonkey.eecs.umich.edu";
  public static final int SERVER_PORT = 5204;

  private SharedPreferences prefs;
  private Intent serviceIntent;
  private ICounterService counterService;
  private CounterServiceConnection conn;

  private Button serviceStartButton;
  private Button appViewerButton;
  private Button sysViewerButton;
  private Button helpButton;
  private TextView scaleText;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState); 
    prefs = PreferenceManager.getDefaultSharedPreferences(this);
    serviceIntent = new Intent(this, UMLoggerService.class);
    conn = new CounterServiceConnection();

    setContentView(R.layout.main);
    ArrayAdapter<?> adapterxaxis = ArrayAdapter.createFromResource(
          this, R.array.xaxis, android.R.layout.simple_spinner_item);
    adapterxaxis.setDropDownViewResource(
          android.R.layout.simple_spinner_dropdown_item);

    serviceStartButton = (Button)findViewById(R.id.servicestartbutton);
    appViewerButton = (Button)findViewById(R.id.appviewerbutton);
    sysViewerButton = (Button)findViewById(R.id.sysviewerbutton);
    helpButton= (Button)findViewById(R.id.helpbutton);

    serviceStartButton.setOnClickListener(serviceStartButtonListener);
    sysViewerButton.setOnClickListener(sysViewerButtonListener);
    appViewerButton.setOnClickListener(appViewerButtonListener);
    helpButton.setOnClickListener(helpButtonListener);
         
    if(counterService != null) {
      serviceStartButton.setText("Stop Profiler");  
      appViewerButton.setEnabled(true);
      sysViewerButton.setEnabled(true);
    } else {
      serviceStartButton.setText("Start Profiler");
      appViewerButton.setEnabled(false);
      sysViewerButton.setEnabled(false);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    getApplicationContext().bindService(serviceIntent, conn, 0);
    if(prefs.getBoolean("firstRun", true)) {
      if(PhoneSelector.getPhoneType() == PhoneSelector.PHONE_UNKNOWN) {
        showDialog(DIALOG_UNKNOWN_PHONE);
      } else {
        showDialog(DIALOG_TOS);
      }
    }
    Intent startingIntent = getIntent();
    if(startingIntent.getBooleanExtra("isFromIcon", false)) {
      Intent copyIntent = (Intent)getIntent().clone();
      copyIntent.putExtra("isFromIcon", false);
      setIntent(copyIntent);
      Intent intent = new Intent(this, PowerTabs.class);
      startActivity(intent);
    }
  }
   
  @Override
  public void onPause() {
    super.onPause();
    getApplicationContext().unbindService(conn);
  }

  private static final int MENU_PREFERENCES = 0;
  private static final int MENU_SAVE_LOG = 1;
  private static final int DIALOG_START_SENDING = 0;
  private static final int DIALOG_STOP_SENDING = 1;
  private static final int DIALOG_TOS = 2;
  private static final int DIALOG_RUNNING_ON_STARTUP = 3;
  private static final int DIALOG_NOT_RUNNING_ON_STARTUP = 4;
  private static final int DIALOG_UNKNOWN_PHONE = 5;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, MENU_PREFERENCES, 0, "Options");
    menu.add(0, MENU_SAVE_LOG, 0, "Save log");
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch(item.getItemId()) {
      case MENU_PREFERENCES:
        startActivity(new Intent(this, EditPreferences.class));
        return true;
      case MENU_SAVE_LOG:
        new Thread() {
          public void start() {
            File writeFile = new File(
                Environment.getExternalStorageDirectory(), "PowerTrace" + 
                    System.currentTimeMillis() + ".log");
            try {
              InflaterInputStream logIn = new InflaterInputStream(
                  openFileInput("PowerTrace.log"));
              BufferedOutputStream logOut = new BufferedOutputStream(
                  new FileOutputStream(writeFile));

              byte[] buffer = new byte[20480];
              for(int ln = logIn.read(buffer); ln != -1;
                      ln = logIn.read(buffer)) {
                logOut.write(buffer, 0, ln);
              }
              logIn.close();
              logOut.close();
              Toast.makeText(UMLogger.this, "Wrote log to " +
                             writeFile.getAbsolutePath(),
                             Toast.LENGTH_SHORT).show();
              return;
            } catch(java.io.EOFException e) {
              Toast.makeText(UMLogger.this, "Wrote log to " +
                             writeFile.getAbsolutePath(),
                             Toast.LENGTH_SHORT).show();
              return;
            } catch(IOException e) {
            }
            Toast.makeText(UMLogger.this, "Failed to write log to sdcard",
                           Toast.LENGTH_SHORT).show();
          }
        }.start();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  /**This function includes all the dialog constructor*/
  @Override
  protected Dialog onCreateDialog(int id) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    switch(id) {
      case DIALOG_TOS:
        builder.setMessage(R.string.term)
          .setCancelable(false)
          .setPositiveButton("Agree", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              prefs.edit().putBoolean("firstRun", false)
                          .putBoolean("runOnStartup", true)
                          .putBoolean("sendPermission", true).commit();
              dialog.dismiss();
            }
          })
          .setNegativeButton("Do not agree",
             new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int id) {
                 prefs.edit().putBoolean("firstRun", true).commit();
                 finish();
               }
          });
        return builder.create();
      case DIALOG_STOP_SENDING:
        builder.setMessage(R.string.stop_sending_text)
          .setCancelable(true)
          .setPositiveButton("Stop", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              prefs.edit().putBoolean("sendPermission", false).commit();
              dialog.dismiss();
            }
          })
          .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
            }
          });
        return builder.create();
      case DIALOG_START_SENDING:
        builder.setMessage(R.string.start_sending_text)
          .setCancelable(true)
          .setPositiveButton("Start", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              prefs.edit().putBoolean("sendPermission", true).commit();
              dialog.dismiss();
            }
          })
          .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
            }
          });
        return builder.create();
      case DIALOG_RUNNING_ON_STARTUP:
        builder.setMessage(R.string.running_on_startup)
          .setCancelable(true)
          .setNeutralButton("Ok", null);
        return builder.create();
      case DIALOG_NOT_RUNNING_ON_STARTUP:
        builder.setMessage(R.string.not_running_on_startup)
          .setCancelable(true)
          .setNeutralButton("Ok", null);
        return builder.create();
      case DIALOG_UNKNOWN_PHONE:
        builder.setMessage(R.string.unknown_phone)
          .setCancelable(false)
          .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.dismiss();
              showDialog(DIALOG_TOS);
            }
          });
        return builder.create();
        
    }        
    return null;
  }

    
  private Button.OnClickListener appViewerButtonListener =
    new Button.OnClickListener() {
      public void onClick(View v) {
        Intent intent = new Intent(v.getContext(), PowerTop.class);
        startActivityForResult(intent, 0);
      }
  };
    
  private Button.OnClickListener sysViewerButtonListener =
    new Button.OnClickListener()  {
      public void onClick(View v) {
        Intent intent = new Intent(v.getContext(), PowerTabs.class);
        startActivityForResult(intent, 0);
      }
  };
    
  private Button.OnClickListener serviceStartButtonListener =
    new Button.OnClickListener() {
      public void onClick(View v) {
        serviceStartButton.setEnabled(false);
        if(counterService != null) {
          stopService(serviceIntent);
        } else {
          if(conn == null) {
            Toast.makeText(UMLogger.this, "Profiler failed to start",
                           Toast.LENGTH_SHORT).show();
          } else {
            startService(serviceIntent);
          }
        }
      }
  };

  private class CounterServiceConnection implements ServiceConnection {
    public void onServiceConnected(ComponentName className, 
                                   IBinder boundService) {
      counterService = ICounterService.Stub.asInterface((IBinder)boundService);
      serviceStartButton.setText("Stop Profiler");
      serviceStartButton.setEnabled(true);
      appViewerButton.setEnabled(true);
      sysViewerButton.setEnabled(true);
    }

    public void onServiceDisconnected(ComponentName className) {
      counterService = null;
      getApplicationContext().unbindService(conn);
      getApplicationContext().bindService(serviceIntent, conn, 0);

      Toast.makeText(UMLogger.this, "Profiler stopped",
                     Toast.LENGTH_SHORT).show();
      serviceStartButton.setText("Start Profiler");
      serviceStartButton.setEnabled(true);
      appViewerButton.setEnabled(false);
      sysViewerButton.setEnabled(false);
    }
  }
   
  private Button.OnClickListener helpButtonListener =
    new Button.OnClickListener() {
      public void onClick(View v) {
        Intent myIntent = new Intent(v.getContext(), Help.class);
        startActivityForResult(myIntent, 0);
      }
  };
}
