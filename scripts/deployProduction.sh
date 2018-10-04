#!/usr/bin/env bash

ssh deploy@biscicol3.acis.ufl.edu <<'ENDSSH'

    cd code/prod/biscicol-fims

    git fetch
    git checkout master
    git pull

    ./gradlew clean &&
    JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 ./gradlew -PforceJars=true -Penvironment=production fatWar &&
    sudo cp /home/deploy/code/prod/biscicol-fims/dist/biscicol-fims-fat.war /opt/web/prod/webapps/biscicol-fims.war &&
    sudo /bin/touch /opt/web/prod/webapps/biscicol-fims.xml

ENDSSH