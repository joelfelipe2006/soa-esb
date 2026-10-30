#!/usr/bin/env sh
# Compila e roda UM componente. Ex.: ./run.sh esb | ./run.sh estoque | ./run.sh loja
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac --release 17 -encoding UTF-8 -d out -sourcepath src/main/java \
  src/main/java/br/senai/fatesg/soa/Main.java || {
  echo "Erro de compilação. Confira se o JDK 17+ está instalado: javac -version"
  exit 1
}
exec java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out br.senai.fatesg.soa.Main "$@"
