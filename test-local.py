#!/usr/bin/env python3
import pathlib, subprocess, os
root=pathlib.Path(__file__).resolve().parent
jdk=pathlib.Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
out=root/'build-local/test-classes'
out.mkdir(parents=True,exist_ok=True)
names=['XdsMeasurement','BridgeData','StartupWindow','SensorMetadata','NotificationConfig','P715Sample']
sources=[root/f'app/src/main/java/dev/yide/powerbridge/{n}.java' for n in names]
tests=[root/f'tests/{n}Test.java' for n in names]
subprocess.run([str(jdk/'bin/javac'),'--release','8','-encoding','UTF-8','-d',str(out),*map(str,sources+tests)],check=True)
for n in names: subprocess.run([str(jdk/'bin/java'),'-cp',str(out),n+'Test'],check=True)
