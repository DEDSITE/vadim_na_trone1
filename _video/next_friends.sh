echo START $(date)
cd /Users/dmitrij/prog/vadim_na_trone
# Java 21 для gradle: берём ту, что найдётся (IDEA / системная)
JH=$(/usr/libexec/java_home -v 25+ 2>/dev/null)
echo "java_home list:"; /usr/libexec/java_home -V 2>&1 | head -10; ls "$HOME/Library/Java/JavaVirtualMachines" 2>&1
[ -n "$JH" ] && export JAVA_HOME="$JH"
[ -z "$JAVA_HOME" ] && for d in $(ls -d "$HOME/Library/Java/JavaVirtualMachines"/*/Contents/Home 2>/dev/null | sort -r) "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" "/Applications/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home"; do [ -x "$d/bin/java" ] && export JAVA_HOME="$d" && break; done
echo "JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version 2>&1
./gradlew friendsZip --console=plain 2>&1 | tail -40
echo "== result"
ls -la _для_друзей/vadim_na_trone-1.0.0.jar vadim_mods.zip
echo DONE
