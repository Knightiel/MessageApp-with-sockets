#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# build.sh  –  Compila e demonstra como executar o chat Java
# ─────────────────────────────────────────────────────────────

SRC=src
BIN=bin

echo "=== Compilando fontes ==="
mkdir -p "$BIN"
javac -d "$BIN" "$SRC"/Server.java "$SRC"/ClientHandler.java "$SRC"/Client.java

if [ $? -ne 0 ]; then
  echo "ERRO: compilação falhou."
  exit 1
fi

echo "=== Compilação concluída. Arquivos em: $BIN ==="
echo ""
echo "Como executar:"
echo ""
echo "  # Terminal 1 – inicia o servidor"
echo "  java -cp $BIN Server"
echo ""
echo "  # Terminal 2 – Alice"
echo "  java -cp $BIN Client Alice"
echo ""
echo "  # Terminal 3 – Bob"
echo "  java -cp $BIN Client Bob"
echo ""
echo "Exemplos de comandos no cliente:"
echo "  /send message Bob Olá, Bob!"
echo "  /send file Bob /tmp/documento.pdf"
echo "  /users"
echo "  /sair"
