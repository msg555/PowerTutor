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

package edu.umich.PowerTutor.widget;

import edu.umich.PowerTutor.R;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.ListView;

import java.util.ArrayList;

public class DataSourceConfigure extends Activity {
  private DataSource dataSource;
  private int level;

  private String[] shortOptions;
  private String[] longOptions;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setResult(RESULT_CANCELED);

    Intent intent = getIntent();
    Bundle extras = intent.getExtras();
    if(extras != null) {
      dataSource = (DataSource)extras.getSerializable("data_source");
      level = extras.getInt("level");
    }
    if(dataSource == null) {
      dataSource = new DataSource();
      level = 0;
    }
    setTitle(dataSource.getTitle(level));
    shortOptions = dataSource.getShortOptions(level);
    longOptions = dataSource.getLongOptions(level);

    final ListView listView = new ListView(this);
    ArrayAdapter adapter = new ArrayAdapter(this, 0) {
      public View getView(int position, View convertView, ViewGroup parent) {
        View itemView = getLayoutInflater()
            .inflate(R.layout.widget_item_layout, listView, false);
        TextView title = (TextView)itemView.findViewById(R.id.title);
        TextView summary = (TextView)itemView.findViewById(R.id.summary);
        Item item = (Item)getItem(position);
        item.setupView(title, summary);
        return itemView;
      }
    };

    int pos = 0;
    final Item[] items = new Item[shortOptions.length];
    for(int i = 0; i < shortOptions.length; i++) {
      if(dataSource.hasOption(level, i)) {
        items[pos] = new Item(i);
        adapter.add(items[pos++]);
      }
    }
    listView.setAdapter(adapter);
    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView parent, View view,
                              int position, long id) {
        items[position].onClick();
      }
    });
    setContentView(listView);
  }

  @Override
  protected void onActivityResult(int reqCode, int resCode, Intent data) {
    if(resCode == RESULT_OK) {
      Intent resultValue = new Intent();
      resultValue.putExtras(data);
      setResult(RESULT_OK, resultValue);
      finish();
    }
  }

  private class Item {
    private int id;

    public Item(int id) {
      this.id = id;
    }
  
    public void setupView(TextView title, TextView summary) {
      title.setText(shortOptions[id]);
      summary.setText(longOptions[id]);
    }

    public void onClick() {
      if(dataSource.setParam(level, id)) {
        Intent resultValue = new Intent();
        resultValue.putExtra("data_source", dataSource);
        setResult(RESULT_OK, resultValue);
        finish();
      } else {
        Intent startIntent = new Intent(DataSourceConfigure.this,
                                        DataSourceConfigure.class);
        startIntent.putExtra("data_source", dataSource);
        startIntent.putExtra("level", level + 1);
        startActivityForResult(startIntent, 0);
      }
    }
  }
}
