#!/usr/bin/env bash
# Build the pinned Android PRoot runtime inside the Ubuntu 20.04 build container.
set -euo pipefail
cd /work/proot-builder

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y python2.7 gawk make gcc wget tar gzip ca-certificates
ln -sf /usr/bin/python2.7 /usr/local/bin/python
ln -sf /usr/bin/gawk /usr/local/bin/awk

sed -i 's|^ARCHS=.*|ARCHS="aarch64"|' config
sed -i 's|^NDK=.*|NDK="'"${ANDROID_HOME}"'/ndk/23.2.8568313"|' config

# The upstream talloc host occasionally returns transient 5xx responses.
# Keep the retry logic in a standalone script so the CI YAML cannot corrupt
# its here-document indentation.
cat > get-talloc.sh <<'TALLOC'
#!/usr/bin/env bash
set -euo pipefail
. ./config
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"
archive="talloc-$TALLOC_V.tar.gz"
url="https://download.samba.org/pub/talloc/$archive"
for attempt in $(seq 1 8); do
  if wget --timeout=30 --tries=1 -O "$archive.tmp" "$url"; then
    mv "$archive.tmp" "$archive"
    tar -xzf "$archive"
    exit 0
  fi
  rm -f "$archive.tmp"
  sleep 5
done
echo "Unable to fetch pinned talloc source after 8 attempts" >&2
exit 1
TALLOC
chmod +x get-talloc.sh
./build.sh

mkdir -p /tmp/proot-package
tar -xzf packages/proot-android-aarch64.tar.gz -C /tmp/proot-package
mkdir -p /work/android/app/src/main/assets/proot/root
cp -a /tmp/proot-package/root/. /work/android/app/src/main/assets/proot/root/
mkdir -p /work/android/app/src/main/assets/licenses
cp LICENSE /work/android/app/src/main/assets/licenses/PRoot-Android-builder-MIT.txt
wget --timeout=20 --tries=2 -qO /work/android/app/src/main/assets/licenses/LGPL-3.0-or-later.txt https://raw.githubusercontent.com/spdx/license-list-data/v3.29.0/text/LGPL-3.0-or-later.txt
wget --timeout=20 --tries=2 -qO /work/android/app/src/main/assets/licenses/GPL-3.0-or-later.txt https://raw.githubusercontent.com/spdx/license-list-data/v3.29.0/text/GPL-3.0-or-later.txt
find build -type f \( -name 'COPYING*' -o -iname 'LICENSE*' \) -exec cp --parents {} /work/android/app/src/main/assets/licenses/ \;
tar -czf /work/android/app/src/main/assets/licenses/PRoot-0.15-source.tar.gz -C build proot-0.15_release
tar -czf /work/android/app/src/main/assets/licenses/talloc-2.1.14-source.tar.gz -C build talloc-2.1.14
tar -czf /work/android/app/src/main/assets/licenses/build-proot-android-source.tar.gz --exclude=build --exclude=packages --exclude=.git -C /work/proot-builder .
test -x /work/android/app/src/main/assets/proot/root/bin/proot
test -d /work/android/app/src/main/assets/proot/root/libexec/proot
chmod 755 /work/android/app/src/main/assets/proot/root/bin/proot
find /work/android/app/src/main/assets/proot/root/libexec/proot -type f -exec chmod 755 {} \;
