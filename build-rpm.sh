#!/bin/bash
VERSION=$1
PROJECT_ROOT=`pwd`
PROJECT_NAME=sat5-insights
SPEC_FILE_NAME=sat5-insights.spec

if [ -z "$VERSION" ]; then
  echo "Usage: build.sh <version>";
  echo "Example: build.sh 0.1.0";
  exit 1
else
  echo "Version: ${VERSION}"
fi

if [ -e !"./$SPEC_FILE_NAME" ]; then
  echo "Script must be executed from sat5-telemetry project root."
  exit 1
fi

echo "Removing existing RPMS directory..."
rm -rf ${PROJECT_ROOT}/RPMS
echo "Done."

echo "Making tmp directory..."
RPM_WORK_DIR=.rpm-work/$PROJECT_NAME-$VERSION
mkdir -p $RPM_WORK_DIR
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
cp rh-insights-sat5.conf ../$RPM_WORK_DIR
cp gui/dist/scripts/insights.js ../$RPM_WORK_DIR
cp gui/dist/styles/insights.css ../$RPM_WORK_DIR
cp proxy/smartproxy/target/redhat_access.war ../$RPM_WORK_DIR
cp -r jsp ../$RPM_WORK_DIR
cd $PROJECT_ROOT/.rpm-work
tar -cf ~/rpmbuild/SOURCES/$PROJECT_NAME.tar.gz $PROJECT_NAME-$VERSION
echo "Done."

echo "Copying spec file to ~/rpmbuild..."
cd $PROJECT_ROOT
cp $SPEC_FILE_NAME ~/rpmbuild/SPECS/$SPEC_FILE_NAME
rm -rf .rpm-work
echo "Done."

echo "Running rpmbuild..."
cd ~/rpmbuild/BUILD
rpmbuild --clean --rmsource --rmspec -ba ../SPECS/$SPEC_FILE_NAME
echo "Done."

echo "Copying RPMs..."
cp -r ../RPMS ${PROJECT_ROOT}/.tmp/RPMS
cp -r ../SRPMS ${PROJECT_ROOT}/.tmp/SRPMS
echo "Done."

#sign the rpm
