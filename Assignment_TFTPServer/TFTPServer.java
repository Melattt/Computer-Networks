import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class TFTPServer {
  public static final int TFTPPORT = 4970;
  public static final int BUFSIZE = 516;
  public static final String READDIR = "/read/";
  public static final String WRITEDIR = "/write/";
  // OP codes
  public static final int OP_RRQ = 1;
  public static final int OP_WRQ = 2;
  public static final int OP_DAT = 3;
  public static final int OP_ACK = 4;
  public static final int OP_ERR = 5;

  // ERROR codes|
  public static final int ERR_NOT_DEFINED = 0;
  public static final int ERR_FILE_NOT_FOUND = 1;
  public static final int ERR_ACCESS_VIOLATION = 2;
  public static final int ERR_FILE_ALREADY_EXISTS = 6;

  public static void main(String[] args) {
    if (args.length > 0) {
      System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
      System.exit(1);
    }
    // Starting the server
    try {
      TFTPServer server = new TFTPServer();
      server.start();
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  private void start() throws SocketException {
    byte[] buf = new byte[BUFSIZE];

    // Create socket
    DatagramSocket socket = new DatagramSocket(null);

    // Create local bind point
    SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
    socket.bind(localBindPoint);

    System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

    // Loop to handle client requests
    while (true) {

      final InetSocketAddress clientAddress = receiveFrom(socket, buf);

      // If clientAddress is null, an error occurred in receiveFrom()
      if (clientAddress == null)
        continue;

      final StringBuffer requestedFile = new StringBuffer();
      final int reqtype = ParseRQ(buf, requestedFile);

      new Thread() {
        public void run() {
          try {
            DatagramSocket sendSocket = new DatagramSocket(0);

            // Connect to client
            sendSocket.connect(clientAddress);

            System.out.printf("%s request for %s from %s using port %d\n",
                (reqtype == OP_RRQ) ? "Read" : "Write", requestedFile.toString(),
                clientAddress.getHostName(), clientAddress.getPort());

            // Read request
            if (reqtype == OP_RRQ) {
              requestedFile.insert(0, READDIR);
              HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
            }
            // Write request
            else {
              requestedFile.insert(0, WRITEDIR);
              HandleRQ(sendSocket, requestedFile.toString(), OP_WRQ);
            }
            sendSocket.close();
          } catch (SocketException e) {
            e.printStackTrace();
          }
        }
      }.start();
    }
  }

  /**
   * Reads the first block of data, i.e., the request for an action (read or
   * write).
   * 
   * @param socket (socket to read from)
   * @param buf    (where to store the read data)
   * @return socketAddress (the socket address of the client)
   */
  private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
    // Create datagram packet
    DatagramPacket parket = new DatagramPacket(buf, buf.length);
    // Receive packet

    // Get client address and port from the packet
    try {
      socket.receive(parket);
      checkForErrorPacket(buf);
    } catch (Exception e) {
      e.printStackTrace();
    }
    InetSocketAddress socketAddress = new InetSocketAddress(parket.getAddress(), parket.getPort());

    return socketAddress;
  }

  /**
   * Parses the request in buf to retrieve the type of request and requestedFile
   * 
   * @param buf           (received request)
   * @param requestedFile (name of file to read/write)
   * @return opcode (request type: RRQ or WRQ)
   */
  private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
    // See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents
    int n = 0;
    int counter= 0;
    String mode = "";
    byte[] buffr = new byte[BUFSIZE];
    ByteBuffer buffer = ByteBuffer.wrap(buf);
    short opcode = buffer.getShort();
    for (int i = 1; i < buf.length; i++) {
      if (buf[i] == 0) {
        n++;
        if (n == 1) {
          String fileName = new String(buffr).trim();
          requestedFile.append(fileName);
          buffr = new byte[BUFSIZE];
        } else if (n == 2) {
          mode = new String(buffr).trim();
          if (!mode.equals("octet")) {
            System.out.println("Transfer mode not octet!");
            System.exit(0);
          }
          break;
        }
      }
      if ((buf[i] != 0)) {
        buffr[counter] = buf[i];
        counter++;
      }
    }
    return opcode;
  }

  /**
   * Handles RRQ and WRQ requests
   * 
   * @param sendSocket    socket used to send/receive packets.
   * @param requestedFile to read/write.
   * @param opcode        RRQ or WRQ
   */
  private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) {
    if (opcode == OP_RRQ) {
      // See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
      boolean result = send_DATA_receive_ACK(sendSocket, requestedFile);
      if (!result) {
        System.out.println("File has been sent!");
      } else {
        send_ERR(sendSocket, ERR_NOT_DEFINED, "Not defined, see error message (if any).");
      }
    } else if (opcode == OP_WRQ) {
      boolean result = receive_DATA_send_ACK(sendSocket, requestedFile);
      if (result) {
        System.out.println(requestedFile + " has been successfully uploaded to the server.");
      } else {
        send_ERR(sendSocket, ERR_NOT_DEFINED, "Not defined, see error message (if any).");
      }

    } else {
      System.err.println("Invalid request. Sending an error packet.");
      // See "TFTP Formats" in TFTP specification for the ERROR packet contents
      send_ERR(sendSocket, ERR_NOT_DEFINED, "Illegal Operation or Invalid request");
      return;
    }
  }

  /**
   * To be implemented
   */
  private void checkForErrorPacket(byte[] buf) {
    ByteBuffer bufwrap = ByteBuffer.wrap(buf);
    if (bufwrap.getShort() == 5) {
      System.out.println("Client is dead, server exiting ....");
      System.exit(0);
    }
  }

  private DatagramPacket errorPacket(int opcode, int errorCode, String message) {
    return responsePacket(opcode, errorCode, message.getBytes());
  }

  private DatagramPacket dataAckPacket(int block) {
    byte[] buf = new byte[BUFSIZE];
    ByteBuffer bufwrap = ByteBuffer.wrap(buf);
    bufwrap.putShort((short) OP_ACK);
    bufwrap.putShort((short) block);
    return new DatagramPacket(buf, buf.length);
  }

  private DatagramPacket responsePacket(int opcode, int block, byte[] data) {
    byte[] buffr = new byte[BUFSIZE];
    ByteBuffer bufwrap = ByteBuffer.wrap(buffr);
    bufwrap.putShort((short) opcode);
    bufwrap.putShort((short) block);
    bufwrap.put(data);
    return new DatagramPacket(buffr, buffr.length);

  }

  private byte[] removeHeadersFromByteArray(byte[] dataWithHeader) {
    byte[] buf = Arrays.copyOfRange(dataWithHeader, 4, dataWithHeader.length);
    return buf;
  }

  private byte[] pushData(byte[] data, DatagramSocket socket, int block) throws Exception {
    socket.send(responsePacket(OP_DAT, block, data));
    byte[] buf = new byte[512];
    DatagramPacket ack = new DatagramPacket(buf, buf.length);
    Thread.sleep(1000);
    socket.receive(ack);
    checkForErrorPacket(buf);
    return buf;
  }

  private boolean send_DATA_receive_ACK(DatagramSocket socket, String requestedFile) {
    boolean isDataSentAndAcked = false;
    byte[] data = new byte[512];
    byte[] ack = new byte[BUFSIZE];
    int block = 1;
    int count = 0;
    File file = new File(".");
    try {
      file = new File(file.getCanonicalPath() + requestedFile);
      if (file.exists()) {
        byte[] buffer = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
        for (int i = 0; i < buffer.length; i++) {
          data[count] = buffer[i];
          count++;
          if (count == 511) {
            ack = pushData(data, socket, block);
            checkForErrorPacket(ack);
            count = 0;
            block++;
            data = new byte[512];
          }
        }

        if (count < 512) {
          ack = pushData(data, socket, block);
          checkForErrorPacket(ack);
          isDataSentAndAcked = OP_ACK == ack[1] ? true : false;
          System.out.println(isDataSentAndAcked ? " data has been sent" : "data has not been sent");
        }
      } else {
        send_ERR(socket, ERR_FILE_NOT_FOUND, "File not Found");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return isDataSentAndAcked;

  }

  private boolean receive_DATA_send_ACK(DatagramSocket socket, String requestedFile) {
    boolean isFileRecievedFully = false;
    int block = 0;
    File file = new File(".");
    file = new File(file.getAbsolutePath() + requestedFile);
    if (!file.exists()) {
      byte[] buf = new byte[BUFSIZE];
      try {
        FileOutputStream out = new FileOutputStream(file.getAbsolutePath());
        socket.send(dataAckPacket(block));
        while (true) {
          DatagramPacket packet = new DatagramPacket(buf, buf.length);
          socket.receive(packet);
          checkForErrorPacket(buf);
          socket.send(dataAckPacket(++block));
          Thread.sleep(1000);
          out.write(removeHeadersFromByteArray(buf));
          out.flush();
          if (packet.getLength() < 512) {
            out.close();
            socket.close();
            socket.disconnect();
            return isFileRecievedFully = true;
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      send_ERR(socket, ERR_FILE_ALREADY_EXISTS, "File already exists!");
    }

    return isFileRecievedFully;
  }

  private void send_ERR(DatagramSocket socket, int errorCode, String message) {
    try {
      socket.send(errorPacket(OP_ERR, errorCode, message));
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

}
