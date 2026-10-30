@echo off
rem ============================================================
rem  Sobe a arquitetura inteira NESTE computador, cada parte
rem  numa janela (processos separados):
rem
rem      Loja ---> ESB (8080) ---> estoque (8081)
rem                            |-> pagamento-pix (8082)
rem                            |-> pagamento-cartao (8083)
rem                            |-> faturamento LEGADO/XML (8084)
rem                            '-> notificacao (8085)
rem
rem  E abre o painel do ESB no navegador.
rem ============================================================
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
javac --release 17 -encoding UTF-8 -d out -sourcepath src\main\java src\main\java\br\senai\fatesg\soa\Main.java
if errorlevel 1 (
  echo Erro de compilacao. Confira se o JDK 17+ esta instalado: javac -version
  pause
  exit /b 1
)

set JAVA=java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -cp out br.senai.fatesg.soa.Main

start "ESB (8080)" cmd /k "chcp 65001 >nul & %JAVA% esb"
timeout /t 3 /nobreak >nul
start "Estoque (8081)"          cmd /k "chcp 65001 >nul & %JAVA% estoque"
start "Pagamento PIX (8082)"    cmd /k "chcp 65001 >nul & %JAVA% pagamento-pix"
start "Pagamento Cartao (8083)" cmd /k "chcp 65001 >nul & %JAVA% pagamento-cartao"
start "Faturamento LEGADO (8084)" cmd /k "chcp 65001 >nul & %JAVA% faturamento"
start "Notificacao (8085)"      cmd /k "chcp 65001 >nul & %JAVA% notificacao"
timeout /t 3 /nobreak >nul
start "Loja (consumidor)"       cmd /k "chcp 65001 >nul & %JAVA% loja"
start "" http://localhost:8080

echo.
echo Tudo no ar. Painel: http://localhost:8080
echo Na janela da Loja, experimente:
echo   comprar teclado 2 pix
echo   comprar notebook 2 cartao     (recusado: compensacao no estoque)
echo Feche a janela da Notificacao, faca um pedido e abra de novo com:
echo   run.bat notificacao
