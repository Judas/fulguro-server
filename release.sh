#!/bin/bash

# Applying prod config before building
cd modules/common/src/main/resources
  rm config.properties
  cp prod.config.properties config.properties
cd -

# Fetching build number
version=$(grep 'fulgurogo.version.name' gradle.properties | sed 's/fulgurogo.version.name=\(.*\)/\1/')

# Build jar
./gradlew clean :app:shadowJar

# Move to export folder
mv app/build/libs/app-${version}-all.jar export/fulgurobot-${version}.jar

# Rolling back to dev config
cd modules/common/src/main/resources
  rm config.properties
  cp dev.config.properties config.properties
cd -
