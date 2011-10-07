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

import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TabHost;

public class PowerTabs extends TabActivity {
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.power_tabs);

    Resources res = getResources();
    TabHost tabHost = getTabHost();
    TabHost.TabSpec spec;

    // TODO: We could put in some icons on each of these two tabs.  Not sure if
    // we care enough or if it would look much better.
    Intent intent = new Intent(this, PowerViewer.class);
    intent.putExtras(getIntent());
    spec = tabHost.newTabSpec("Charts").setIndicator("Chart View")
                  .setContent(intent);
    tabHost.addTab(spec);

    // Do the same for the other tabs
    intent = new Intent(this, PowerPie.class);
    intent.putExtras(getIntent());
    spec = tabHost.newTabSpec("Pie").setIndicator("Pie View")
                  .setContent(intent);
    tabHost.addTab(spec);

    intent = new Intent(this, MiscView.class);
    intent.putExtras(getIntent());
    spec = tabHost.newTabSpec("Stat").setIndicator("Stat View")
                  .setContent(intent);
    tabHost.addTab(spec);

    // Show the PowerViewer activity by default.
    tabHost.setCurrentTab(0);
  }
}
