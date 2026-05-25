package distributed.battleship.common.controller;

import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.helper.ProtocolMessageJsonHelper;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public abstract class NodeTCPController {

    // Mappa per gestire più socket contemporaneamente (IP:Porta -> Socket)
    protected final ConcurrentHashMap<String, Socket> activeConnections = new ConcurrentHashMap<>();

    // Un BufferedReader per socket: evita che chiamate successive creino buffer separati
    // e perdano dati già letti dall'InputStream sottostante.
    private final ConcurrentHashMap<Socket, BufferedReader> socketReaders = new ConcurrentHashMap<>();

    // Pool di thread per non bloccare l'applicazione principale
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 1. START CONNECTION (Client side o Peer-to-Peer initiation)
    public Socket startConnection(String ip, int port) throws IOException {
        Socket socket = new Socket(ip, port);
        String key = ip + ":" + port;
        activeConnections.put(key, socket);
        socketReaders.put(socket, new BufferedReader(new InputStreamReader(socket.getInputStream())));
        return socket;
    }

    // 2. START ACCEPTING (Server side)
    // Questo è il metodo extra che serve al ServerController per mettersi in ascolto
    public void startListening(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        executor.execute(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    activeConnections.put(clientSocket.getRemoteSocketAddress().toString(), clientSocket);

                    // Ogni volta che un client si connette, creiamo un thread per lui
                    socketReaders.put(clientSocket, new BufferedReader(new InputStreamReader(clientSocket.getInputStream())));
                    startReceiverThread(clientSocket);

                } catch (IOException e) {
                    System.err.println("Accept error: " + e.getMessage());
                }
            }
        });
    }

    // 3. SEND MESSAGE
    public void sendMessage(Socket socket, MessageConstants.MessageTuple message) {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            String json = serializeMessage(message);
            out.println(json);
        } catch (IOException e) {
            System.err.println("Send error: " + e.getMessage());
        }
    }

    // 4. CLOSE CONNECTION
    public void closeConnection(Socket socket) throws IOException {
        if (socket != null) {
            activeConnections.values().remove(socket);
            BufferedReader reader = socketReaders.remove(socket);
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
            socket.close();
        }
    }

    // 5. RECEIVE MESSAGE (bloccante)
    // Aspetta il prossimo messaggio sul socket dato, lo deserializza e lo ritorna.
    // Restituisce null se la connessione viene chiusa o si verifica un errore.
    public MessageConstants.MessageTuple receiveMessage(Socket socket) {
        if (socket == null || socket.isClosed()) return null;
        try {
            BufferedReader reader = socketReaders.get(socket);
            if (reader == null) return null;
            String line = reader.readLine();
            if (line == null) return null;
            return deserializeMessage(line);
        } catch (IOException e) {
            System.err.println("Receive error: " + e.getMessage());
            return null;
        }
    }

    // METODO PRIVATO PER I THREAD DI RICEZIONE
    // Questo risolve il tuo dubbio: ogni socket ha un thread che aspetta i messaggi
    private void startReceiverThread(Socket socket) {
        executor.execute(() -> {
            BufferedReader in = socketReaders.get(socket);
            if (in == null) return;
            try {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    // Deserializza il messaggio
                    MessageConstants.MessageTuple msg = deserializeMessage(inputLine);

                    // Notifica il controller (Server o Client) che è arrivato un messaggio
                    onMessageReceived(socket, msg);
                }
            } catch (IOException e) {
                System.err.println("Connection closed with " + socket.getRemoteSocketAddress());
            }
        });
    }

    // Metodo astratto che ClientController e ServerController implementeranno
    // per decidere COSA FARE quando arriva un messaggio
    protected abstract void onMessageReceived(Socket sender, MessageConstants.MessageTuple msg);

    protected String serializeMessage(MessageConstants.MessageTuple msg) {
        return ProtocolMessageJsonHelper.serialize(msg);
    }

    protected MessageConstants.MessageTuple deserializeMessage(String json) {
        return ProtocolMessageJsonHelper.deserialize(json);
    }
}
