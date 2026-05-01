Project: Computer Networks Coursework 1

Author: Elisha Vincent-Mushi
Student ID: 210029902
----------------------------------------
How to run:

1. Compile:
   javac Node.java

2. Run:
   Node needs to be created and configured through a test class
   eg:AzureLabTest
   
Example setup:

   Node node = new Node()
   node.setNodeName("N:your.email@city.ac.uk)
   node.openPort(20110);
   
----------------------------------------
How it Works:

- Keys are hashed using SHA-256 to determine which node is responsible
- The node sends NEAREST (N) requests to discover closer nodes
- Messages are encoded using a space-count encoding scheme
- Responses are matched using a 2-character transaction ID
- UDP is used for all communication

--------------------------------------------------

Known Limitations:

- External CRN test nodes may not always be available [have to compensate
  through making boostrapnodes that attempt to find an available node]
  
- Write operations may fail if the contacted node is not responsible
- Network behaviour depends on availability of active peers

--------------------------------------------------

Additional Notes:

- Bootstrap nodes are discovered automatically by scanning known IP ranges
- The node maintains an address book of known peers
- Encoding/decoding ensures correct parsing of strings with spaces

--------------------------------------------------
