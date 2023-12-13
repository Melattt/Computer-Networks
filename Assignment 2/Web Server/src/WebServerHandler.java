import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

/**
 * The WebServerHandler class is responsible for handling client requests in the web server. 
 * It reads the client's request, processes it, and sends back the appropriate response
 */
public class WebServerHandler implements Runnable {

    private final Socket socket;
    private final File Dir;

    public WebServerHandler(Socket socket, File Dir) {
        this.socket = socket;
        this.Dir = Dir;
    }

    /**
     * This method is called when a new thread is started to handle a client's request. 
     * It reads the request from the client, processes it, and then either sends the 
     * requested file as a response (HTTP 200) or handles various error responses 
     * (HTTP 404, HTTP 500, and HTTP 302).
     */
    public void run() {
        try {
            // Read.
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String request = input.readLine();

            // Parse.
            String[] requests = request.split(" ");
            String requested = requests[1];

            if (requested.endsWith("/") || requested.endsWith("\\")) {
                requested += "index.html";
            } else {

                String[] pathParts = requested.split("/");
                String fileName = pathParts[pathParts.length - 1];
                if (!fileName.contains(".")) {
                    requested += "/index.html";
                }
            }

            //  302.
            if (requested.equals("/redirect.html")) {
                Handler302();
                input.close();
                socket.close();
                return;
            }

            File file = new File(Dir, requested);

            try {
                String canonicalPath = file.getCanonicalPath();
                // Check if the canonical path starts with the expected base directory
                if (!canonicalPath.startsWith(Dir.getCanonicalPath())) {
                    Handler404(request);
                } else if (file.exists() && file.isFile()) {
                    Handler200(request, requested, file);
                } else {
                    Handler404(request);
                }
            } catch (SecurityException e) {
                Handler404(request);
            }

            input.close();
            socket.close();
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            try {
                Handler500();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }


    /**
     * This method is called when the requested file is not found in the server's public 
     * directory. It generates an HTTP 404 "Not Found" response and sends it back to the client.
     *
     * @param request .
     * @throws IOException .
     */
    private void Handler404(String request) throws IOException {
        String error = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>404 Page Not Found</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: Arial, sans-serif;\n" +
                "            background-color: #f2f2f2;\n" +
                "        }\n" +
                "        .container {\n" +
                "            margin: 50px auto;\n" +
                "            padding: 20px;\n" +
                "            max-width: 600px;\n" +
                "            background-color: #fff;\n" +
                "            border-radius: 5px;\n" +
                "            box-shadow: 0px 2px 5px rgba(0, 0, 0, 0.3);\n" +
                "        }\n" +
                "        h1 {\n" +
                "            font-size: 36px;\n" +
                "            color: #666;\n" +
                "        }\n" +
                "        p {\n" +
                "            font-size: 20px;\n" +
                "            color: #666;\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <h1>404 Page Not Found</h1>\n" +
                "        <p>The requested page could not be found. Please check the URL and try again.</p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        String serverName = InetAddress.getLocalHost().getHostName();

        String response = "HTTP/1.0 404 Not Found\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + error.length() + "\r\n" +
                "Date: " + new Date().toString() + "\r\n" +
                "Server: " + serverName + "\r\n\r\n" +
                error;

        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(response.getBytes());
        outputStream.flush();
        outputStream.close();

        System.out.println("Client: " + socket.getInetAddress() + ":" + socket.getPort()
                + ", Version: " + request.split(" ")[2] + ", Response: 404 Not Found"
                + ", Date: " + new Date().toString() + ", Server: " + serverName
                + ", Content-Length: " + error.length()
                + ", Connection: close, Content-Type: text/html");
    }

    /** 
     * Uncomment the lines 155, 169 and 170 in order to test 500 response.
     */

    // used to trigger 500 response
    //private static final long MAX_SIZE = 1024 * 1024; // 1 MB = 1048576 bytes

    /**
     * This method is called when the requested file is found in the server's public 
     * directory. It generates an HTTP 200 "OK" response and sends the file content 
     * as the response body to the client.
     *
     * @param request
     * @param requestedPath
     * @param file
     * @throws IOException
     */
    private void Handler200(String request, String requestedPath, File file) throws IOException {

    //if (file.length() > MAX_SIZE) {
        //throw new IOException("File size exceeds maximum allowed size");}
        OutputStream outputStream = socket.getOutputStream();
        String contentType = getType(requestedPath);
        byte[] fileContent = Files.readAllBytes(Paths.get(file.getPath()));

        PrintWriter writer = new PrintWriter(outputStream);

        writer.println("HTTP/1.1 200 OK");
        writer.println("Content-Type: " + contentType);
        writer.println("Content-Length: " + fileContent.length);
        writer.println("Date: " + new Date().toString());

        String serverName = InetAddress.getLocalHost().getHostName();
        writer.println("Server: " + serverName);

        writer.println("");
        writer.flush();

        outputStream.write(fileContent);
        outputStream.flush();

        writer.close();

        System.out.println("Server request file exists!");
        if (file.isDirectory()) {
            System.out.println("Requested item is a directory!");
        }
        System.out.println("Client: " + socket.getInetAddress() + ":" + socket.getPort()
                + ", Version: " + request.split(" ")[2] + ", Response: 200 OK"
                + ", Date: " + new Date().toString() + ", Server: " + serverName
                + ", Content-Length: " + fileContent.length + ", Connection: close"
                + ", Content-Type: " + contentType);
    }

    /**
     * This method is called when an internal server error occurs during processing.
     * It generates an HTTP 500 "Internal Server Error" response and sends it back 
     * to the client.
     *
     * @throws IOException .
     */
    private void Handler500() throws IOException {

        String error = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>500 Internal Server Error</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: Arial, sans-serif;\n" +
                "            background-color: #f2f2f2;\n" +
                "        }\n" +
                "        .container {\n" +
                "            margin: 50px auto;\n" +
                "            padding: 20px;\n" +
                "            max-width: 600px;\n" +
                "            background-color: #fff;\n" +
                "            border-radius: 5px;\n" +
                "            box-shadow: 0px 2px 5px rgba(0, 0, 0, 0.3);\n" +
                "        }\n" +
                "        h1 {\n" +
                "            font-size: 36px;\n" +
                "            color: #666;\n" +
                "        }\n" +
                "        p {\n" +
                "            font-size: 20px;\n" +
                "            color: #666;\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <h1>500 Internal Server Error</h1>\n" +
                "        <p>An internal server error occurred. Please try again later.</p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        String serverName = InetAddress.getLocalHost().getHostName();
        String response = "HTTP/1.0 500 Internal Server Error\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + error.length() + "\r\n" +
                "Server: " + serverName + "\r\n\r\n" +
                error;

        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(response.getBytes());


        outputStream.flush();
        outputStream.close();

        System.out.println("Client: " + socket.getInetAddress() + ":" + socket.getPort()
                + ", Version: HTTP/1.1" + ", Response: 500 Internal Server Error"
                + ", Date: " + new Date().toString() + ", Server: " + serverName
                + ", Content-Length: " + error.length()
                + ", Connection: close, Content-Type: text/html");
    }

    /**
     * This method is called when the client requests a specific URL that needs redirection. 
     * It generates an HTTP 302 "Found" response with the Location header set to the redirect 
     * URL and sends it back to the client.
     *
     * @throws IOException .
     */
    private void Handler302() throws IOException {
        // Hardcode a specific URL to redirect to
        String example = "https://www.example.com";
        String serverName = InetAddress.getLocalHost().getHostName();

        PrintWriter writer = new PrintWriter(socket.getOutputStream());
        writer.println("HTTP/1.1 302 Found");
        writer.println("Location: " + example);
        writer.println("Date: " + new Date().toString());
        writer.println("Server: " + serverName);
        writer.println("");
        writer.flush();
        writer.close();

        System.out.println("Client: " + socket.getInetAddress() + ":" + socket.getPort()
                + ", Version: HTTP/1.1, Response: 302 Found"
                + ", Date: " + new Date().toString() + ", Server: " + serverName
                + ", Content-Length: 0, Connection: close, Content-Type:");
    }

    /**
     * This method is called when the client requests a specific URL that needs redirection.
     * It generates an HTTP 302 "Found" response with the Location header set to the redirect
     * URL and sends it back to the client.
     *
     * @param filePath .
     * @return .
     */
    private String getType(String filePath) {
        if (filePath.endsWith(".html") || filePath.endsWith(".htm")) {
            return "text/html";
        } else if (filePath.endsWith(".png")) {
            return "image/png";
        }
        return null;
    }
}

/**
 * Note that the code contains some commented lines related to testing the server's response
 * to large files (HTTP 500). This can be done by uncommenting those lines and using a file 
 * size larger than the defined MAX_SIZE. However, since these lines are commented, the server
 *  will not trigger the 500 response due to file size issues.
 */
