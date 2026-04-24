import java.io.*;
import java.net.*;

/**
 * ClientHandler – roda em uma thread dedicada para cada cliente conectado.
 *
 * Protocolo de comunicação (linha de texto + bloco binário para arquivos):
 *
 *   TEXT:<remetente>:<mensagem>               → mensagem de texto
 *   FILE:<remetente>:<nomeArquivo>:<tamanho>  → cabeçalho de arquivo (seguido pelos bytes)
 *   SYS:<texto>                               → mensagem de sistema
 *
 * Comandos recebidos do cliente:
 *   REGISTER:<username>
 *   MSG:<destinatario>:<mensagem>
 *   FILE:<destinatario>:<nomeArquivo>:<tamanho>  (seguido pelos bytes do arquivo)
 *   USERS
 *   QUIT
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private DataInputStream  in;
    private DataOutputStream out;
    private String username;

    ClientHandler(Socket socket) {
        this.socket = socket;
    }

    String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    // ------------------------------------------------------------------ //
    //  Thread principal
    // ------------------------------------------------------------------ //

    @Override
    public void run() {
        try {
            in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // Primeira mensagem deve ser o registro
            String regLine = in.readUTF();
            if (!regLine.startsWith("REGISTER:")) {
                socket.close();
                return;
            }
            username = regLine.substring("REGISTER:".length()).trim();

            if (!Server.register(username, this)) {
                sendRaw("[erro] Nome de usuário '" + username + "' já está em uso.");
                socket.close();
                return;
            }
            sendRaw("[servidor] Bem-vindo, " + username + "! Use /help para ver os comandos.");

            // Loop de leitura de comandos
            while (!socket.isClosed()) {
                String line;
                try {
                    line = in.readUTF();
                } catch (EOFException | SocketException e) {
                    break; // cliente desconectou
                }

                if (line.startsWith("QUIT")) {
                    break;

                } else if (line.startsWith("USERS")) {
                    sendRaw(Server.getUserList());

                } else if (line.startsWith("MSG:")) {
                    handleTextMessage(line);

                } else if (line.startsWith("FILE:")) {
                    handleFileTransfer(line);

                } else {
                    sendRaw("[erro] Comando desconhecido: " + line);
                }
            }

        } catch (IOException e) {
            System.err.println("[servidor] Erro com cliente " + username + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ------------------------------------------------------------------ //
    //  Handlers de comandos
    // ------------------------------------------------------------------ //

    /** Formato: MSG:<destinatario>:<mensagem> */
    private void handleTextMessage(String line) throws IOException {
        // Divide em no máximo 3 partes para preservar ':' na mensagem
        String[] parts = line.split(":", 3);
        if (parts.length < 3) {
            sendRaw("[erro] Formato inválido. Use: /send message <destinatario> <mensagem>");
            return;
        }
        String to      = parts[1];
        String message = parts[2];

        if (to.equals(username)) {
            sendRaw("[erro] Você não pode enviar mensagem para si mesmo.");
            return;
        }
        if (!Server.routeMessage(username, to, message)) {
            sendRaw("[erro] Usuário '" + to + "' não encontrado.");
        } else {
            sendRaw("[ok] Mensagem enviada para " + to + ".");
        }
    }

    /** Formato: FILE:<destinatario>:<nomeArquivo>:<tamanho> + bytes */
    private void handleFileTransfer(String line) throws IOException {
        String[] parts = line.split(":", 4);
        if (parts.length < 4) {
            sendRaw("[erro] Formato inválido. Use: /send file <destinatario> <caminho>");
            return;
        }
        String to       = parts[1];
        String fileName = parts[2];
        long   fileSize;
        try {
            fileSize = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            sendRaw("[erro] Tamanho de arquivo inválido.");
            return;
        }

        if (to.equals(username)) {
            sendRaw("[erro] Você não pode enviar arquivo para si mesmo.");
            return;
        }

        // Lê os bytes do arquivo enviados pelo cliente
        byte[] data = new byte[(int) fileSize];
        in.readFully(data);

        if (!Server.routeFile(username, to, fileName, data)) {
            sendRaw("[erro] Usuário '" + to + "' não encontrado.");
        } else {
            sendRaw("[ok] Arquivo '" + fileName + "' enviado para " + to + ".");
        }
    }

    // ------------------------------------------------------------------ //
    //  Métodos de envio (chamados pelo Server para rotear dados)
    // ------------------------------------------------------------------ //

    /** Envia uma mensagem de texto de outro cliente para este. */
    synchronized void sendTextMessage(String from, String message) {
        try {
            out.writeUTF("TEXT:" + from + ":" + message);
            out.flush();
        } catch (IOException e) {
            System.err.println("[servidor] Falha ao enviar mensagem para " + username);
        }
    }

    /** Envia um arquivo de outro cliente para este. */
    synchronized void sendFile(String from, String fileName, byte[] data) {
        try {
            out.writeUTF("FILE:" + from + ":" + fileName + ":" + data.length);
            out.write(data);
            out.flush();
        } catch (IOException e) {
            System.err.println("[servidor] Falha ao enviar arquivo para " + username);
        }
    }

    /** Envia uma linha de texto simples (mensagem de sistema, erro, ok). */
    synchronized void sendRaw(String text) {
        try {
            out.writeUTF("SYS:" + text);
            out.flush();
        } catch (IOException e) {
            System.err.println("[servidor] Falha ao enviar mensagem de sistema para " + username);
        }
    }

    // ------------------------------------------------------------------ //
    //  Limpeza
    // ------------------------------------------------------------------ //

    private void cleanup() {
        if (username != null) Server.unregister(username);
        try { socket.close(); } catch (IOException ignored) {}
    }
}
