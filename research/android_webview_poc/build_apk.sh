#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=== Building NotiSpoti WebView POC APK ==="

rm -rf bin obj gen
mkdir -p bin obj gen

ANDROID_JAR="$DIR/../build_tools/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
    echo "Error: android.jar not found at $ANDROID_JAR"
    exit 1
fi

echo "[1/6] Compiling resources with aapt2..."
aapt2 compile --dir res -o bin/compiled_res.zip

echo "[2/6] Linking resources and generating R.java..."
aapt2 link -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -o bin/base.apk \
    --java gen \
    bin/compiled_res.zip

echo "[3/6] Compiling Java code with javac..."
javac -d obj \
    -cp "$ANDROID_JAR:gen" \
    src/com/notispoti/webview/*.java gen/com/notispoti/webview/*.java

echo "[4/6] Converting Java bytecode to DEX with d8..."
d8 --lib "$ANDROID_JAR" \
    --output bin \
    obj/com/notispoti/webview/*.class

echo "[5/6] Adding classes.dex to APK..."
cd bin
jar -uf base.apk classes.dex
cd "$DIR"

echo "[6/6] Signing APK with debug key..."
if [ ! -f debug.keystore ]; then
    echo "Generating debug keystore..."
    keytool -genkeypair -v -keystore debug.keystore \
        -storepass android -alias androiddebugkey \
        -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

apksigner sign --ks debug.keystore \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --out ../NotiSpoti-WebView-POC.apk \
    bin/base.apk

echo "=== SUCCESS! APK built at ../NotiSpoti-WebView-POC.apk ==="
ls -lh ../NotiSpoti-WebView-POC.apk
