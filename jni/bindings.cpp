#include <jni.h>
#include <stdio.h>
#include <linux/fb.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <stdlib.h>

extern "C" {
JNIEXPORT jdouble JNICALL
Java_edu_umich_PowerTutor_components_OLED_getScreenPixPower(
    JNIEnv * env, jobject thiz, jdouble rcoef, jdouble gcoef,
    jdouble bcoef, jdouble modul_coef);
}

#define NUMBER_OF_SAMPLES 500

static int fd = -2;
static struct fb_fix_screeninfo screeninfo;
static unsigned char* buf;

static unsigned int samples[NUMBER_OF_SAMPLES];

JNIEXPORT jdouble JNICALL
Java_edu_umich_PowerTutor_components_OLED_getScreenPixPower(
    JNIEnv * env, jobject thiz, jdouble rcoef, jdouble gcoef,
    jdouble bcoef, jdouble modul_coef) {
  if(fd == -1) {
    return (jdouble)-1.0;
  } else if(fd == -2) {
    fd = open("/dev/graphics/fb0", O_RDWR);
    if(fd == -1) {
      fd = open("/dev/fb0", O_RDWR);
    }
    if(fd == -1) {
      return (jdouble)-1.0;
    }
    if(ioctl(fd, FBIOGET_FSCREENINFO, &screeninfo)) {
      close(fd);
      fd = -1;
      return (jdouble)-1.0;
    }
    buf = (unsigned char*)mmap(NULL, screeninfo.smem_len, PROT_READ,
                               MAP_SHARED, fd, 0);
    if(buf == (unsigned char*)-1) {
      close(fd);
      fd = -1;
      return (jdouble)-1.0;
    }
    int range = screeninfo.smem_len / 12;
    srand(555);
    for(int i = 0; i < NUMBER_OF_SAMPLES; i++) {
      int a = range * i / NUMBER_OF_SAMPLES;
      int b = range * (i + 1) / NUMBER_OF_SAMPLES;
      if(b <= a + 1) {
        samples[i] = a;
      } else  {
        samples[i] = a + rand() % (b - a);
      }
    }
  }
  jdouble pixPower = 0.0;
  for(int i = 0; i < NUMBER_OF_SAMPLES; i++) {
    int x = 4 * samples[i];
    int r = buf[x];
    int g = buf[x + 1];
    int b = buf[x + 2];

    /* Calculate the power usage of this one pixel if it were at full
     * brightness.  Linearly scale by brightness to get true power
     * consumption.  To calculate whole screen compute average of sampled
     * region and multiply by number of pixels.
     */
    int modul_val = r + g + b;
    pixPower += rcoef * (r * r) + gcoef * (g * g) + bcoef * (b * b) -
                modul_coef * (modul_val * modul_val);
  }
  return pixPower;
}
