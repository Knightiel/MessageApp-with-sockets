import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servidor de Chat - responsável por rotear mensagens e arquivos entre clientes.
 * Mantém log de conexões em arquivo.
 */
public class Server {

    private static final int PORT = 9090;
    private static final String LOG_FILE = "server.log";

    // Mapa thread-safe: username -> handler do cliente
    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Object logLock = new Object();

    public static void main(String[] args) throws IOException {
        System.out.println("=== Servidor de Chat iniciado na porta " + PORT + " ===");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Cada cliente roda em sua própria thread
                Thread t = new Thread(new ClientHandler(clientSocket));
                t.setDaemon(true);
                t.start();
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Registro / remoção de clientes
    // ------------------------------------------------------------------ //

    static boolean register(String username, ClientHandler handler) {
        if (clients.containsKey(username)) return false;
        clients.put(username, handler);
        logConnection(username, handler.getRemoteAddress());
        broadcastSystemMessage(username + " entrou no chat.");
        return true;
    }

    static void unregister(String username) {
        clients.remove(username);
        broadcastSystemMessage(username + " saiu do chat.");
    }

    // ------------------------------------------------------------------ //
    //  Roteamento de mensagens de texto
    // ------------------------------------------------------------------ //

    /**
     * Roteia uma mensagem de texto de um remetente para um destinatário.
     * @return true se o destinatário existe, false caso contrário.
     */
    static boolean routeMessage(String from, String to, String message) {
        ClientHandler dest = clients.get(to);
        if (dest == null) return false;
        dest.sendTextMessage(from, message);
        return true;
    }

    // ------------------------------------------------------------------ //
    //  Roteamento de arquivos
    // ------------------------------------------------------------------ //

    /**
     * Roteia um arquivo de um remetente para um destinatário.
     * @return true se o destinatário existe, false caso contrário.
     */
    static boolean routeFile(String from, String to, String fileName, byte[] data) {
        ClientHandler dest = clients.get(to);
        if (dest == null) return false;
        dest.sendFile(from, fileName, data);
        return true;
    }

    // ------------------------------------------------------------------ //
    //  Lista de usuários conectados
    // ------------------------------------------------------------------ //

    static String getUserList() {
        if (clients.isEmpty()) return "[servidor] Nenhum usuário conectado.";
        return "[servidor] Usuários conectados: " + String.join(", ", clients.keySet());
    }

    // ------------------------------------------------------------------ //
    //  Mensagem de sistema para todos
    // ------------------------------------------------------------------ //

    private static void broadcastSystemMessage(String text) {
        String msg = "[servidor] " + text;
        for (ClientHandler h : clients.values()) {
            h.sendRaw(msg);
        }
    }

    // ------------------------------------------------------------------ //
    //  Log em arquivo
    // ------------------------------------------------------------------ //

    private static void logConnection(String username, String ip) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String line = ts + " | " + username + " | " + ip;
        synchronized (logLock) {
            try (FileWriter fw = new FileWriter(LOG_FILE, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(line);
            } catch (IOException e) {
                System.err.println("[log] Erro ao gravar log: " + e.getMessage());
            }
        }
        System.out.println("[log] " + line);
    }
}
