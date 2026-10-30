@echo off
rem Compila e roda UM componente. Exemplos:
rem   run.bat esb
rem   run.bat estoque
rem   run.bat loja
rem   run.bat estoque --esb http://192.168.0.10:8080 --host 192.168.0.11
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
javac --release 17 -encoding UTF-8 -d out -sourcepath src\main\java src\main\java\br\senai\fatesg\soa\Main.java
if errorlevel 1 (
  echo Erro de compilacao. Confira se o JDK 17+ esta instalado: javac -version
  pause
  exit /b 1
)
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -cp out br.senai.fatesg.soa.Main %*
