#!/bin/bash
# компиляция мода в облаке: main + client -> /home/claude/w/out
set -e
CP=$(ls /mnt/user-data/uploads/vadim_na_trone/build/cpdump/*.jar | tr '\n' ':')
P=/home/claude/w/proj/src
rm -rf /home/claude/w/out && mkdir -p /home/claude/w/out
find $P/main/java $P/client/java -name '*.java' > /home/claude/w/files.txt
javac -nowarn -encoding UTF-8 --release 21 -proc:none -cp "$CP" -d /home/claude/w/out @/home/claude/w/files.txt 2>&1 | grep -v "^Note:" | head -60
echo "classes: $(find /home/claude/w/out -name '*.class' | wc -l)"
