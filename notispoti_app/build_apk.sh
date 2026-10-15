#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=== Building NotiSpoti Prototype APK (with Spotify App Remote SDK) ==="

rm -rf bin obj gen
mkdir -p bin obj gen

ANDROID_JAR="$DIR/../build_tools/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
    echo "Error: android.jar not found at $ANDROID_JAR"
    exit 1
fi

echo "[1/6] Compiling resources with aapt2..."
aapt2 compile --dir res -o bin/compiled_res.zip

echo "[2/6] Linking resources and generating R.java (minSdk=26, targetSdk=34)..."
aapt2 link -I "$ANDROID_JAR" \
    --min-sdk-version 26 \
    --target-sdk-version 34 \
    --manifest AndroidManifest.xml \
    -o bin/base.apk \
    --java gen \
    bin/compiled_res.zip

echo "[3/6] Compiling Java code with javac (Java 8 target)..."
javac -d obj \
    -source 8 -target 8 \
    -cp "$ANDROID_JAR:gen:spotify-app-remote.jar:gson-2.10.1.jar" \
    src/com/notispoti/app/*.java gen/com/notispoti/app/*.java

echo "[4/6] Converting Java bytecode & SDK to DEX with d8 (minApi=26)..."
d8 --lib "$ANDROID_JAR" \
    --min-api 26 \
    --output bin \
    obj/com/notispoti/app/*.class \
    spotify-app-remote.jar \
    gson-2.10.1.jar

echo "[5/6] Adding classes.dex to APK..."
cd bin
jar -u -0 -f base.apk classes*.dex
cd "$DIR"

echo "[6/6] Signing APK with debug key..."
if [ -f "$DIR/../android_webview_poc/debug.keystore" ]; then
    cp "$DIR/../android_webview_poc/debug.keystore" debug.keystore
elif [ ! -f debug.keystore ]; then
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
    --out ../NotiSpoti.apk \
    bin/base.apk

cp ../NotiSpoti.apk /storage/emulated/0/Download/NotiSpoti.apk 2>/dev/null || true

echo "=== SUCCESS! APK built at ../NotiSpoti.apk and /storage/emulated/0/Download/NotiSpoti.apk ==="
ls -lh ../NotiSpoti.apk /storage/emulated/0/Download/NotiSpoti.apk
