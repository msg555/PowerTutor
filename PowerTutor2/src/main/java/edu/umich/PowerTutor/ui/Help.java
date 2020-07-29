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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**This function implements the UI for help view*/
public class Help extends Activity {
  private static final String powerTutorUrl = "http://powertutor.org";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState); 
    setContentView(R.layout.help);
    TextView s2 = (TextView)this.findViewById(R.id.S2);

    s2.setOnClickListener(new TextView.OnClickListener() {
      public void onClick(View v) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW,
                                     Uri.parse(powerTutorUrl));
        startActivity(myIntent);
      }
    });
  }
}
