#!/bin/bash
VERSION=$1
PROJECT_ROOT=`pwd`

if [ -z "$VERSION" ]; then
  echo "Usage: build.sh <version>";
  echo "Example: build.sh 0.1.0";
  exit 1
else
  echo "Version: ${VERSION}"
fi

if [ -e !"./sat5-telemetry.spec" ]; then
  echo "Script must be executed from sat5-telemetry project root."
  exit 1
fi

echo "Removing existing RPMS directory..."
rm -rf ${PROJECT_ROOT}/RPMS
echo "Done."

echo "Making tmp directory..."
mkdir -p .rpm-work/sat5-telemetry-${VERSION}
echo "Done."

echo "Building angular project..."
cd $PROJECT_ROOT/source/gui
npm install
bower install
grunt build
echo "Done."

echo "Building proxy server..."
cd $PROJECT_ROOT/source/proxy/smartproxy
mvn install
echo "Done."

echo "Creating source tar..."
cd $PROJECT_ROOT/source
cp rh-telemetry-proxy-sat5.6.conf ../.rpm-work/sat5-telemetry-${VERSION}
cp gui/dist/scripts/scripts.js ../.rpm-work/sat5-telemetry-${VERSION}
cp proxy/smartproxy/target/insights.war ../.rpm-work/sat5-telemetry-${VERSION}
cd $PROJECT_ROOT/.rpm-work
tar -cf ~/rpmbuild/SOURCES/sat5-telemetry-${VERSION}.tar.gz sat5-telemetry-${VERSION}
echo "Done."

echo "Copying spec file to ~/rpmbuild..."
cd $PROJECT_ROOT
cp sat5-telemetry.spec ~/rpmbuild/SPECS/sat5-telemetry.spec
rm -rf .rpm-work
echo "Done."

echo "Running rpmbuild..."
cd ~/rpmbuild/BUILD
rpmbuild --clean --rmsource --rmspec -ba ../SPECS/sat5-telemetry.spec
echo "Done."

echo "Copying RPMs..."
cp -r ../RPMS ${PROJECT_ROOT}/.tmp/RPMS
cp -r ../SRPMS ${PROJECT_ROOT}/.tmp/SRPMS
echo "Done."

#sign the rpm