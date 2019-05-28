git submodule update --init --recursive
git submodule foreach --recursive git checkout .
git reset --hard origin/master
git pull
sudo ./gradlew clean compileDebugSources