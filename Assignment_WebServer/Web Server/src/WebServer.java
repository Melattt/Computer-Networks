import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WebServer {

  /**
   * The main method is the main part of the program. It checks the command
   * line arguments, parses the port number, and validates the public directory
   * path.
   *
   * @param args .
   */
  public static void main(String[] args) {
    if (checkArg(args))
      return;
    int port;
    try {
      port = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      System.out.println("Invalid port number: " + args[0]);
      return;
    }

    // Check if relative.
    Path dir_Path = getPath(args);
    if (dir_Path == null)
      return;

    File dir = dir_Path.toFile().getAbsoluteFile();

    // Check if exists
    if (publicDir(dir))
      return;
    listen(port, dir);
  }

  /**
   * The listen method is responsible for starting the server on the specified
   * port.
   * It creates a ServerSocket and enters a loop to accept client connections. For
   * each client connection, it spawns a new thread (WebServerHandler) to handle
   * the
   * client's request.
   *
   * @param port      port.
   * @param directory directory.
   */
  private static void listen(int port, File directory) {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      System.out.println(">>> Server started on port " + port);
      System.out.println(">>> Terminate the server by ctrl-c");

      while (true) {
        Socket socket = serverSocket.accept();
        System.out.println(">>> Assigned a new client to a separate thread.");
        new Thread(new WebServerHandler(socket, directory)).start();
      }
    } catch (IOException e) {
      System.out.println("Error: " + e.getMessage());
    }
  }

  private static boolean publicDir(File publicDirectory) {
    if (!publicDirectory.exists() || !publicDirectory.isDirectory()) {
      System.out.println("The public directory does not exist or is not a directory.");
      return true;
    }
    return false;
  }

  /**
   * The getPath method retrieves the path to the public directory from the
   * command line
   * arguments. It ensures that the path is relative.
   *
   * @param args .
   * @return publicDirectoryPath.
   */
  private static Path getPath(String[] args) {
    Path publicDirectoryPath = Paths.get(args[1]);
    if (publicDirectoryPath.isAbsolute()) {
      System.out.println("The public directory path must be a relative path.");
      return null;
    }
    return publicDirectoryPath;
  }

  /**
   * The checkArg method checks if the correct number of command line arguments is
   * provided.
   *
   * @param args .
   * @return true/false.
   */
  private static boolean checkArg(String[] args) {
    if (args.length != 2) {
      System.out.println("Not enough arguments provided");
      System.out.println("Usage: java WebServer <port> <public_directory>");
      return true;
    }
    return false;
  }
}
