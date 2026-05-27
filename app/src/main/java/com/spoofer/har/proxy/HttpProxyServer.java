package com.spoofer.har.proxy;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpProxyServer {
    private static final String TAG = "HttpProxyServer";
    private final Context context;
    private final HarRecorder harRecorder;
    private final int port;
    private final RewriteEngine rewriteEngine;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public HttpProxyServer(Context context, int port) {
        this.context = context;
        this.port = port;
        this.rewriteEngine = new RewriteEngine(context);
        this.harRecorder = new HarRecorder(context);
    }

    public void start() {
        try {
            this.serverSocket = new ServerSocket(this.port);
            this.serverSocket.setReuseAddress(true);
            this.running = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    acceptLoop();
                }
            }, "Proxy-Accept").start();
            Log.i(TAG, "Proxy server started on port " + this.port);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start proxy server", e);
        }
    }

    private void acceptLoop() {
        while (this.running) {
            try {
                final Socket clientSocket = this.serverSocket.accept();
                this.threadPool.submit(new Runnable() {
                    @Override
                    public void run() {
                        handleClient(clientSocket);
                    }
                });
            } catch (Exception e) {
                if (this.running) {
                    Log.e(TAG, "Accept error", e);
                }
            }
        }
    }

    public void stop() {
        this.running = false;
        this.threadPool.shutdownNow();
        try {
            if (this.serverSocket != null && !this.serverSocket.isClosed()) {
                this.serverSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping proxy", e);
        }
        this.harRecorder.flush();
    }

    private void handleClient(Socket clientSocket) {
        try {
            clientSocket.setSoTimeout(30000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream clientOut = clientSocket.getOutputStream();
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                try { clientSocket.close(); } catch (Exception e) {}
                return;
            }
            if (requestLine.startsWith("CONNECT")) {
                handleConnect(requestLine, reader, clientOut, clientSocket);
            } else {
                handleHttpRequest(requestLine, reader, clientOut, clientSocket);
            }
        } catch (Exception e) {
            Log.e(TAG, "Client handling error", e);
        } finally {
            try { clientSocket.close(); } catch (Exception e) {}
        }
    }

    private void handleConnect(String requestLine, BufferedReader reader, OutputStream clientOut, Socket clientSocket) {
        try {
            String[] parts = requestLine.split(" ");
            String hostPort = parts[1];
            String[] hostPortParts = hostPort.split(":");
            String host = hostPortParts[0];
            int targetPort = 443;
            if (hostPortParts.length > 1) {
                try {
                    targetPort = Integer.parseInt(hostPortParts[1]);
                } catch (NumberFormatException e) {
                    // use default 443
                }
            }
            // Read remaining headers
            String line;
            do {
                line = reader.readLine();
            } while (line != null && !line.isEmpty());
            // Send 200 Connection Established
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
            clientOut.flush();
            // For HTTPS, we just tunnel the data - record it as a CONNECT request
            this.harRecorder.recordRequest("CONNECT", "https://" + host + ":" + targetPort + "/", "", "", "HTTP/1.1 200", "", "");
        } catch (Exception e) {
            Log.e(TAG, "CONNECT handling error", e);
        }
    }

    private void handleHttpRequest(String requestLine, BufferedReader reader, OutputStream clientOut, Socket clientSocket) {
        try {
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String url = parts[1];
            StringBuilder headersBuilder = new StringBuilder();
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                headersBuilder.append(line).append("\r\n");
                if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.split(":")[1].trim());
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
            String body = "";
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = reader.read(bodyChars, totalRead, contentLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                body = new String(bodyChars, 0, totalRead);
            }
            // Extract host
            String host = "";
            String[] headerLines = headersBuilder.toString().split("\r\n");
            for (String h : headerLines) {
                if (h.toLowerCase().startsWith("host:")) {
                    host = h.split(":")[1].trim();
                    break;
                }
            }
            // Connect to target
            Socket targetSocket = new Socket(host, 80);
            targetSocket.setSoTimeout(30000);
            OutputStream targetOut = targetSocket.getOutputStream();
            BufferedReader targetReader = new BufferedReader(new InputStreamReader(targetSocket.getInputStream()));
            // Forward request
            targetOut.write((requestLine + "\r\n").getBytes());
            targetOut.write(headersBuilder.toString().getBytes());
            targetOut.write("\r\n".getBytes());
            if (!body.isEmpty()) {
                targetOut.write(body.getBytes());
            }
            targetOut.flush();
            // Read response
            StringBuilder responseBuilder = new StringBuilder();
            String statusLine = targetReader.readLine();
            responseBuilder.append(statusLine).append("\r\n");
            int responseContentLength = 0;
            StringBuilder responseHeaders = new StringBuilder();
            String line2;
            while ((line2 = targetReader.readLine()) != null && !line2.isEmpty()) {
                responseHeaders.append(line2).append("\r\n");
                if (line2.toLowerCase().startsWith("content-length:")) {
                    try {
                        responseContentLength = Integer.parseInt(line2.split(":")[1].trim());
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
            responseBuilder.append(responseHeaders).append("\r\n");
            String responseBody = "";
            if (responseContentLength > 0) {
                char[] respBodyChars = new char[responseContentLength];
                int totalRead2 = 0;
                while (totalRead2 < responseContentLength) {
                    int read2 = targetReader.read(respBodyChars, totalRead2, responseContentLength - totalRead2);
                    if (read2 == -1) break;
                    totalRead2 += read2;
                }
                responseBody = new String(respBodyChars, 0, totalRead2);
            }
            targetSocket.close();
            // Apply rewrite
            String responseBody2 = this.rewriteEngine.applyRewrite(url, responseBody);
            // Record
            this.harRecorder.recordRequest(method, url, headersBuilder.toString(), body, statusLine, responseHeaders.toString(), responseBody2);
            // Send response to client
            clientOut.write((statusLine + "\r\n").getBytes());
            clientOut.write(responseHeaders.toString().getBytes());
            clientOut.write("\r\n".getBytes());
            if (!responseBody2.isEmpty()) {
                clientOut.write(responseBody2.getBytes());
            }
            clientOut.flush();
        } catch (Exception e) {
            Log.e(TAG, "HTTP request handling error", e);
            try {
                clientOut.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".getBytes());
                clientOut.flush();
            } catch (Exception e2) {
                // ignore
            }
        }
    }

    public RewriteEngine getRewriteEngine() {
        return this.rewriteEngine;
    }

    public HarRecorder getHarRecorder() {
        return this.harRecorder;
    }
}
