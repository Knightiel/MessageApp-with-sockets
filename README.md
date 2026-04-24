# 💬 JavaChat — Aplicativo de Mensagens com Sockets e Multithreading

Aplicativo de chat peer-to-peer desenvolvido em **Java puro**, sem dependências externas, com comunicação bidirecional entre clientes via servidor central. Suporta envio de **mensagens de texto** e **transferência de arquivos** em tempo real.

---

## 📋 Índice

- [Sobre o Projeto](#sobre-o-projeto)
- [Arquitetura](#arquitetura)
- [Funcionalidades](#funcionalidades)
- [Pré-requisitos](#pré-requisitos)
- [Instalação e Compilação](#instalação-e-compilação)
- [Como Usar](#como-usar)
- [Protocolo de Comunicação](#protocolo-de-comunicação)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Concorrência e Thread Safety](#concorrência-e-thread-safety)

---

## Sobre o Projeto

JavaChat é um sistema de mensagens cliente-servidor implementado com **sockets TCP** e **multithreading**, desenvolvido como exercício prático de programação concorrente e comunicação em rede. Toda a comunicação entre clientes passa pelo servidor, que atua exclusivamente como roteador — sem armazenar mensagens.

---

## Arquitetura

```
┌─────────────┐        ┌──────────────────┐        ┌─────────────┐
│   Cliente   │◄──────►│     Servidor     │◄──────►│   Cliente   │
│   (Alice)   │        │  (roteador TCP)  │        │    (Bob)    │
└─────────────┘        └──────────────────┘        └─────────────┘
  Thread leitora            Thread por             Thread leitora
  Thread principal          cliente                Thread principal
```

- Cada cliente conectado recebe uma **thread dedicada** no servidor (`ClientHandler`)
- O cliente usa **duas threads**: uma para ler mensagens recebidas e outra para processar a entrada do teclado
- O servidor **não armazena** mensagens — apenas roteia entre os clientes

---

## Funcionalidades

- ✅ Envio de mensagens de texto entre clientes específicos
- ✅ Transferência de arquivos binários (qualquer tipo/tamanho)
- ✅ Listagem de usuários conectados
- ✅ Log de conexões em arquivo (`server.log`) com IP e data/hora
- ✅ Múltiplos clientes simultâneos
- ✅ Detecção de desconexão e notificação aos demais usuários
- ✅ Proteção contra *path traversal* no recebimento de arquivos

---

## Pré-requisitos

| Requisito | Versão mínima |
|---|---|
| Java Development Kit (JDK) | 17+ |

Nenhuma biblioteca externa é necessária. O projeto utiliza apenas a **Java Standard Library**.

### Instalação do JDK

**Ubuntu/Debian:**
```bash
sudo apt update && sudo apt install default-jdk
```

**Fedora/RHEL:**
```bash
sudo dnf install java-21-openjdk-devel
```

**macOS:**
```bash
brew install openjdk@21
```

**Windows:**
```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```
Ou baixe o instalador `.msi` em [adoptium.net](https://adoptium.net).

---

## Instalação e Compilação

```bash
# Clone o repositório
git clone https://github.com/seu-usuario/javachat.git
cd javachat

# Crie a pasta de saída
mkdir -p bin

# Compile todos os fontes
javac -d bin src/Server.java src/ClientHandler.java src/Client.java
```

---

## Como Usar

### 1. Inicie o servidor

```bash
java -cp bin Server
```

```
=== Servidor de Chat iniciado na porta 9090 ===
```

### 2. Conecte os clientes (um terminal por cliente)

```bash
# Terminal 2
java -cp bin Client Alice

# Terminal 3
java -cp bin Client Bob
```

### 3. Comandos disponíveis

| Comando | Descrição |
|---|---|
| `/send message <destinatario> <mensagem>` | Envia mensagem de texto |
| `/send file <destinatario> <caminho>` | Envia um arquivo |
| `/users` | Lista usuários conectados |
| `/sair` | Encerra a conexão |
| `/help` | Exibe a ajuda |

### Exemplos

```
# Alice envia mensagem para Bob
> /send message Bob Olá, tudo bem?

# Bob envia um arquivo para Alice
> /send file Alice /home/bob/relatorio.pdf

# Listar usuários online
> /users
[servidor] Usuários conectados: Alice, Bob, Carlos
```

### Log do servidor

O servidor grava automaticamente um log em `server.log`:

```
2025-04-24 10:32:11 | Alice | /127.0.0.1:54321
2025-04-24 10:32:45 | Bob   | /127.0.0.1:54322
```

---

## Protocolo de Comunicação

A comunicação usa `DataOutputStream.writeUTF` / `DataInputStream.readUTF` para framing automático (prefixo de comprimento), mais leitura bruta de bytes para o conteúdo dos arquivos.

### Cliente → Servidor

| Mensagem | Formato |
|---|---|
| Registro | `REGISTER:<username>` |
| Texto | `MSG:<destinatario>:<mensagem>` |
| Arquivo | `FILE:<destinatario>:<nome>:<tamanho>` + bytes |
| Listar usuários | `USERS` |
| Sair | `QUIT` |

### Servidor → Cliente

| Mensagem | Formato |
|---|---|
| Texto recebido | `TEXT:<remetente>:<mensagem>` |
| Arquivo recebido | `FILE:<remetente>:<nome>:<tamanho>` + bytes |
| Sistema / Erros | `SYS:<texto>` |

---

## Estrutura do Projeto

```
javachat/
├── src/
│   ├── Server.java          # Servidor principal — aceita conexões e roteia dados
│   ├── ClientHandler.java   # Thread dedicada por cliente no servidor
│   └── Client.java          # Aplicação cliente (2 threads: leitura + escrita)
├── bin/                     # Bytecode compilado (.class)
├── server.log               # Log de conexões (gerado em runtime)
└── README.md
```

---

## Concorrência e Thread Safety

| Mecanismo | Onde é usado |
|---|---|
| `ConcurrentHashMap` | Mapa de clientes conectados — leitura/escrita sem locks manuais |
| `synchronized` nos métodos de envio | Evita entrelaçamento de bytes de múltiplas threads escrevendo no mesmo socket |
| Thread daemon para leitura | Encerra automaticamente quando a JVM finaliza |
| `readFully()` para arquivos | Garante que todos os bytes do arquivo sejam lidos antes de prosseguir |

---

## Licença

Distribuído sob a licença MIT. Veja o arquivo `LICENSE` para mais detalhes.
