import java.io.*;
import java.net.*;
import java.nio.file.*;

/**
 * Client – conecta ao servidor de chat e permite enviar/receber mensagens e arquivos.
 *
 * Comandos disponíveis:
 *   /send message <destinatario> <mensagem>   – envia mensagem de texto
 *   /send file <destinatario> <caminho>       – envia arquivo
 *   /users                                    – lista usuários conectados
 *   /sair                                     – encerra a conexão
 *   /help                                     – exibe ajuda
 */
public class Client {

    private static final String HOST = "127.0.0.1";
    private static final int    PORT = 9090;

    private final String           username;
    private       Socket           socket;
    private       DataInputStream  in;
    private       DataOutputStream out;
    private volatile boolean        running = true;

    Client(String username) {
        this.username = username;
    }

    // ------------------------------------------------------------------ //
    //  Conexão
    // ------------------------------------------------------------------ //

    void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        // Registra o usuário no servidor
        out.writeUTF("REGISTER:" + username);
        out.flush();
    }

    // ------------------------------------------------------------------ //
    //  Inicialização das threads
    // ------------------------------------------------------------------ //

    void start() throws IOException {
        connect();

        // Thread de leitura (recebe mensagens do servidor)
        Thread reader = new Thread(this::readLoop, "reader-" + username);
        reader.setDaemon(true);
        reader.start();

        // Thread principal processa entrada do teclado
        writeLoop();

        // Aguarda a thread de leitura encerrar
        try { reader.join(2000); } catch (InterruptedException ignored) {}
        close();
    }

    // ------------------------------------------------------------------ //
    //  Loop de leitura – roda em thread separada
    // ------------------------------------------------------------------ //

    private void readLoop() {
        try {
            while (running) {
                String line;
                try {
                    line = in.readUTF();
                } catch (EOFException | SocketException e) {
                    break;
                }

                if (line.startsWith("TEXT:")) {
                    // TEXT:<remetente>:<mensagem>
                    String[] p = line.split(":", 3);
                    System.out.println("\n[" + p[1] + "]: " + p[2]);

                } else if (line.startsWith("FILE:")) {
                    // FILE:<remetente>:<nomeArquivo>:<tamanho>
                    String[] p = line.split(":", 4);
                    String from     = p[1];
                    String fileName = p[2];
                    int    size     = Integer.parseInt(p[3]);

                    byte[] data = new byte[size];
                    in.readFully(data);

                    saveFile(fileName, data);
                    System.out.println("\n[" + from + "] enviou o arquivo '" + fileName
                            + "' (" + size + " bytes) → salvo no diretório corrente.");

                } else if (line.startsWith("SYS:")) {
                    System.out.println("\n" + line.substring(4));

                } else {
                    System.out.println("\n" + line);
                }

                System.out.print("> ");
            }
        } catch (IOException e) {
            if (running) System.err.println("[cliente] Conexão encerrada pelo servidor.");
        } finally {
            running = false;
        }
    }

    // ------------------------------------------------------------------ //
    //  Loop de escrita – roda na thread principal
    // ------------------------------------------------------------------ //

    private void writeLoop() {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
        printHelp();
        System.out.print("> ");

        try {
            String input;
            while (running && (input = keyboard.readLine()) != null) {
                input = input.trim();
                if (input.isEmpty()) { System.out.print("> "); continue; }

                if (input.equalsIgnoreCase("/sair")) {
                    send("QUIT");
                    running = false;

                } else if (input.equalsIgnoreCase("/users")) {
                    send("USERS");

                } else if (input.equalsIgnoreCase("/help")) {
                    printHelp();

                } else if (input.startsWith("/send message ")) {
                    // /send message <destinatario> <mensagem>
                    String rest = input.substring("/send message ".length()).trim();
                    int    sp   = rest.indexOf(' ');
                    if (sp < 0) {
                        System.out.println("[erro] Uso: /send message <destinatario> <mensagem>");
                    } else {
                        String to  = rest.substring(0, sp);
                        String msg = rest.substring(sp + 1);
                        send("MSG:" + to + ":" + msg);
                    }

                } else if (input.startsWith("/send file ")) {
                    // /send file <destinatario> <caminho>
                    String rest = input.substring("/send file ".length()).trim();
                    int    sp   = rest.indexOf(' ');
                    if (sp < 0) {
                        System.out.println("[erro] Uso: /send file <destinatario> <caminho>");
                    } else {
                        String to   = rest.substring(0, sp);
                        String path = rest.substring(sp + 1).trim();
                        sendFile(to, path);
                    }

                } else {
                    System.out.println("[erro] Comando desconhecido. Digite /help para ajuda.");
                }

                if (running) System.out.print("> ");
            }
        } catch (IOException e) {
            System.err.println("[cliente] Erro de leitura: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Envio de arquivo
    // ------------------------------------------------------------------ //

    private void sendFile(String to, String filePath) throws IOException {
        Path p = Paths.get(filePath);
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            System.out.println("[erro] Arquivo não encontrado: " + filePath);
            return;
        }
        byte[] data     = Files.readAllBytes(p);
        String fileName = p.getFileName().toString();

        // Cabeçalho + bytes
        synchronized (out) {
            out.writeUTF("FILE:" + to + ":" + fileName + ":" + data.length);
            out.write(data);
            out.flush();
        }
    }

    // ------------------------------------------------------------------ //
    //  Recepção / gravação de arquivo
    // ------------------------------------------------------------------ //

    private void saveFile(String fileName, byte[] data) {
        // Garante nome seguro (sem path traversal)
        String safeName = Paths.get(fileName).getFileName().toString();
        Path dest = Paths.get(System.getProperty("user.dir"), safeName);

        // Evita sobrescrever arquivo existente adicionando sufixo
        int i = 1;
        while (Files.exists(dest)) {
            int dot = safeName.lastIndexOf('.');
            String base = dot > 0 ? safeName.substring(0, dot) : safeName;
            String ext  = dot > 0 ? safeName.substring(dot)    : "";
            dest = Paths.get(System.getProperty("user.dir"), base + "_" + i + ext);
            i++;
        }

        try {
            Files.write(dest, data);
        } catch (IOException e) {
            System.err.println("[erro] Falha ao salvar arquivo: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Utilitários
    // ------------------------------------------------------------------ //

    private void send(String msg) throws IOException {
        synchronized (out) {
            out.writeUTF(msg);
            out.flush();
        }
    }

    private void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    private static void printHelp() {
        System.out.println("                 ╔══════════════════════════════════════════════════════╗\r\n" + //
                        "                ║              Comandos disponíveis                    ║\r\n" + //
                        "                ╠══════════════════════════════════════════════════════╣\r\n" + //
                        "                ║ /send message <destinatario> <mensagem>              ║\r\n" + //
                        "                ║     Envia uma mensagem de texto.                     ║\r\n" + //
                        "                ║ /send file <destinatario> <caminho>                  ║\r\n" + //
                        "                ║     Envia um arquivo.                                ║\r\n" + //
                        "                ║ /users                                               ║\r\n" + //
                        "                ║     Lista todos os usuários conectados.              ║\r\n" + //
                        "                ║ /sair                                                ║\r\n" + //
                        "                ║     Encerra a conexão.                               ║\r\n" + //
                        "                ║ /help                                                ║\r\n" + //
                        "                ║     Exibe esta ajuda.                                ║\r\n" + //
                        "                ╚══════════════════════════════════════════════════════╝");
    }

    // ------------------------------------------------------------------ //
    //  Main
    // ------------------------------------------------------------------ //

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Uso: java Client <username>");
            System.exit(1);
        }
        new Client(args[0]).start();
    }
}
