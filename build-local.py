#!/usr/bin/env python3
"""Offline SDK build; no Gradle downloads. Uses an isolated debug signing key."""
import os, pathlib, subprocess, zipfile, shutil
root = pathlib.Path(__file__).resolve().parent
sdk = pathlib.Path(os.environ.get('ANDROID_HOME', str(pathlib.Path.home()/'Library/Android/sdk')))
jdk = pathlib.Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
bt = sdk/'build-tools/36.1.0'
android = sdk/'platforms/android-34/android.jar'
out = root/'build-local'
out.mkdir(exist_ok=True)
shutil.rmtree(out/'classes',ignore_errors=True)
(out/'classes').mkdir(exist_ok=True)
(out/'generated').mkdir(exist_ok=True)
env = dict(os.environ, JAVA_HOME=str(jdk), PATH=str(jdk/'bin')+':'+os.environ.get('PATH',''))
def run(*args): subprocess.run([str(x) for x in args],check=True,env=env,cwd=root)
run(bt/'aapt2','compile','--dir',root/'app/src/main/res','-o',out/'resources.zip')
manifest = out/'AndroidManifest.xml'
manifest.write_text((root/'app/src/main/AndroidManifest.xml').read_text().replace('<manifest ', '<manifest package="dev.yide.powerbridge" ', 1))
run(bt/'aapt2','link','-o',out/'base.apk','-I',android,'--manifest',manifest,'--java',out/'generated',out/'resources.zip')
sources = list((root/'app/src/main/java').rglob('*.java'))+list((out/'generated').rglob('*.java'))
run(jdk/'bin/javac','--release','8','-encoding','UTF-8','-classpath',android,'-d',out/'classes',*sources)
classes=list((out/'classes').rglob('*.class'))
run(bt/'d8','--lib',android,'--min-api','26','--output',out,*classes)
with zipfile.ZipFile(out/'base.apk','a',zipfile.ZIP_DEFLATED) as z: z.write(out/'classes.dex','classes.dex')
run(bt/'zipalign','-f','4',out/'base.apk',out/'aligned.apk')
key=out/'probe-debug.keystore'
if not key.exists(): run(jdk/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Power Bridge Debug','-keyalg','RSA','-keysize','2048','-validity','3650')
run(bt/'apksigner','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',out/'power-bridge-probe.apk',out/'aligned.apk')
run(bt/'apksigner','verify','--verbose',out/'power-bridge-probe.apk')
print('APK:',out/'power-bridge-probe.apk')
