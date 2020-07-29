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

package edu.umich.PowerTutor.components;

import edu.umich.PowerTutor.PowerNotifications;
import edu.umich.PowerTutor.service.IterationData;
import edu.umich.PowerTutor.service.PowerData;
import edu.umich.PowerTutor.util.NotificationService;
import edu.umich.PowerTutor.util.Recycler;

import android.content.Context;
import android.media.AudioManager;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.TreeSet;

/**This class aims to log the audio device status once per log interval*/
public class Audio extends PowerComponent {
  /**This class is the logger data file corresponding to Audio*/
  public static class AudioData extends PowerData {
    private static Recycler<AudioData> recycler = new Recycler<AudioData>();
    
    public static AudioData obtain() {
      AudioData result = recycler.obtain();
      if(result != null) return result;
      return new AudioData();
    }

    @Override
    public void recycle() {
      recycler.recycle(this);
    }

    public boolean musicOn;
  
    private AudioData() {
    }

    public void init(boolean musicOn) {
      this.musicOn = musicOn;
    }
  
    public void writeLogDataInfo(OutputStreamWriter out) throws IOException {
      out.write("Audio-on " + musicOn + "\n");
    }
  }

  private static class MediaData implements Comparable {
    private static Recycler<MediaData> recycler = new Recycler<MediaData>();
    
    public static MediaData obtain() {
      MediaData result = recycler.obtain();
      if(result != null) return result;
      return new MediaData();
    }

    public void recycle() {
      recycler.recycle(this);
    }

    public int uid;
    public int id;
    public int assignUid;

    public int compareTo(Object obj) {
      MediaData x = (MediaData)obj;
      if(uid < x.uid) return -1;
      if(uid > x.uid) return 1;
      if(id < x.id) return -1;
      if(id > x.id) return 1;
      return 0;
    }

    public boolean equals(Object obj) {
      MediaData x = (MediaData)obj;
      return uid == x.uid && id == x.id;
    }
  }

  private AudioManager audioManager;
  private PowerNotifications audioNotif;
  private TreeSet<MediaData> uidData;

  public Audio(Context context) {
    if(NotificationService.available()) {
      uidData = new TreeSet<MediaData>();
      audioNotif = new NotificationService.DefaultReceiver() {
        private int sysUid = -1;

        @Override
        public void noteSystemMediaCall(int uid) {
          sysUid = uid;
        }

        @Override
        public void noteStartMedia(int uid, int id) {
          MediaData data = MediaData.obtain();
          data.uid = uid;
          data.id = id;
          if(uid == 1000 && sysUid != -1) {
            data.assignUid = sysUid;
            sysUid = -1;
          } else {
            data.assignUid = uid;
          }
          synchronized(uidData) {
            if(!uidData.add(data)) {
              data.recycle();
            }
          }
        }

        @Override
        public void noteStopMedia(int uid, int id) {
          MediaData data = MediaData.obtain();
          data.uid = uid;
          data.id = id;
          synchronized(uidData) {
            uidData.remove(data);
          }
          data.recycle();
        }
      };
      NotificationService.addHook(audioNotif);
    }

    audioManager = (AudioManager)context.getSystemService(
                                             Context.AUDIO_SERVICE);
  }

  @Override
  protected void onExit() {
    if(audioNotif != null) {
      NotificationService.removeHook(audioNotif);
    }
  }

  @Override
  public IterationData calculateIteration(long iteration) {
    IterationData result = IterationData.obtain();
    AudioData data = AudioData.obtain();
    data.init(uidData != null && !uidData.isEmpty() ||
              audioManager.isMusicActive());
    result.setPowerData(data);

    if(uidData != null) synchronized(uidData) {
      int last_uid = -1;
      for(MediaData dat : uidData) {
        if(dat.uid != last_uid) {
          AudioData audioPower = AudioData.obtain();
          audioPower.init(true);
          result.addUidPowerData(dat.assignUid, audioPower);
        }
        last_uid = dat.uid;
      }
    }

    return result;
  }

  @Override
  public boolean hasUidInformation() {
    return audioNotif != null;
  }

  @Override
  public String getComponentName() {
    return "Audio";
  }
}
