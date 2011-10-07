all: package

ANDROID_LIB=android-9.jar
CLASSPATH=$(ANDROID_LIB):libs/achartengine-0.7.0.jar:libs/com.artfulbits.aiCharts.jar

genres:
	mkdir -p gen bin
	aapt package -m -J gen -M AndroidManifest.xml -S res -I $(ANDROID_LIB)

aidl:
	find src/ -type f                                                        | \
      grep '\.aidl$$'                                                      | \
      xargs -n 1 aidl -Isrc -I$(ANDROID_LIB) -ogen

gen: genres aidl

compile: gen
	mkdir -p bin
	find src/ gen/ -type f                                                   | \
      grep '\.java$$'                                                      | \
      xargs javac -cp $(CLASSPATH) -d bin
	ndk-build

dex: compile
	dx --dex --output=bin/classes.dex bin/ libs/

package: dex
	aapt package -M AndroidManifest.xml -A assets -S res \
       -F bin/PowerTutor.apk -I $(ANDROID_LIB)
	cd bin; zip PowerTutor.apk classes.dex
	zip bin/PowerTutor.apk -r libs -i \*.so
	jarsigner -storepass android -keystore debug.keystore \
      bin/PowerTutor.apk androiddebugkey

install: package
	adb install bin/PowerTutor.apk

clean:
	rm -rf bin/ gen/
