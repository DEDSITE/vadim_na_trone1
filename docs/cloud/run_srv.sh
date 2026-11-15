#!/bin/bash
# сервер в облаке: KnotServer + наш мод (классы + ресурсы) + Macaw's
set -e
W=/home/claude/w
rm -rf $W/mod && mkdir -p $W/mod && cp -r $W/out/. $W/mod/ && cp -r $W/proj/src/main/resources/. $W/mod/ && cp -r $W/proj/src/client/resources/. $W/mod/
CP=$(ls /mnt/user-data/uploads/vadim_na_trone/build/cpdump/*.jar | grep -v clientOnly | tr '\n' ':')$(ls /mnt/user-data/uploads/vadim_na_trone/build/mcw/*.jar | tr '\n' ':')
cd $W/srv
echo "eula=true" > eula.txt
cat > server.properties <<P
online-mode=false
enable-rcon=true
rcon.password=vadim
rcon.port=25575
server-port=25565
level-name=world
spawn-protection=0
P
exec java -Xmx3G -Dfabric.development=true -Dfabric.gameMappingNamespace=named -Dfabric.runtimeMappingNamespace=named \
  -Dfabric.addMods=$W/mod -cp "$CP" net.fabricmc.loader.impl.launch.knot.KnotServer nogui
