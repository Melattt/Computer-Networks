## Instructions: How to Run

To run the application, follow the steps below:

1. Open the terminal.
2. Change the current directory to "src" using the command: `cd src`.
3. Compile all Java source files using the command: `javac *java`.
4. Start the web server by running the following command: `java WebServerHandler <port> <public_directory>`, where `<port>` is the desired port number and `<public_directory>` is the path to the directory containing the public files.

## Instructions: Running testa2u1.py

To execute the testa2u1.py script, please perform the following steps:

1. Ensure that the server is already running by following the instructions provided in the "How to Run" section.
2. Open another terminal.
3. Change the current directory to "src" using the command: `cd src`.
4. Execute the testa2u1.py script by running the command: `python .\testa2u1.py`.

## Triggering Error Conditions

To trigger specific error conditions, use the following guidelines:

- To trigger a 500 error, uncomment lines 155, 169 and 170 in the WebServerHandler class.
- To trigger a 302 error, request the resource "/redirect.html".
- To trigger a 404 error, request a resource that the server cannot find or access.

Melat Haile: 50%,
Nahomie Haile: 50%.

