#!/usr/bin/env bash
# Sobe a arquitetura inteira neste computador (Linux/macOS).
# Com tmux: tela dividida, um painel por processo. Sem tmux: mostra os comandos.
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac --release 17 -encoding UTF-8 -d out -sourcepath src/main/java \
  src/main/java/br/senai/fatesg/soa/Main.java || exit 1

J="java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out br.senai.fatesg.soa.Main"
PARTES=(esb estoque pagamento-pix pagamento-cartao faturamento notificacao loja)

if command -v tmux >/dev/null 2>&1; then
  tmux kill-session -t soa 2>/dev/null
  tmux new-session -d -s soa -c "$PWD" "$J esb"
  sleep 2
  for p in "${PARTES[@]:1}"; do
    tmux split-window -t soa -c "$PWD" "$J $p"
    tmux select-layout -t soa tiled
  done
  echo "Painel: http://localhost:8080"
  tmux attach -t soa
else
  echo "Abra 7 terminais nesta pasta e rode, nesta ordem:"
  for p in "${PARTES[@]}"; do echo "  ./run.sh $p"; done
  echo "Painel: http://localhost:8080"
fi
