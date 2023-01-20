mkdir "$ANDROID_HOME/licenses" || true;
echo "8933bad161af4178b1185d1a37fbf41ea5269c55" > "$ANDROID_HOME/licenses/android-sdk-license";
echo "d56f5187479451eabf01fb78af6dfcb131a6481e" > "$ANDROID_HOME/licenses/android-sdk-license";
echo "d56f5187479451eabf01fb78af6dfcb131a6481e" > "$ANDROID_HOME/licenses/android-sdk-license";
echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$ANDROID_HOME/licenses/android-sdk-license";
sdkmanager 'ndk;21.4.7075529'
sdkmanager tools;
yes | sdkmanager --licenses;
echo "Fetching ndk-bundle. Suppressing output to avoid travis 4MG size limit";
sdkmanager "ndk-bundle" >/dev/null;
echo "Fetching ndk-bundle complete";
sdkmanager "system-images;android-22;default;armeabi-v7a";
echo "Configuring pre-commit hooks"
git config core.hooksPath .githooks
