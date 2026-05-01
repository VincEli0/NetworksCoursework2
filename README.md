Project: Computer Networks Coursework 1

Author: Elisha Vincent-Mushi
Student ID: 210029902
----------------------------------------
How to run:

1. Compile:
   javac Node.java

2. Run:
   java Node <nodeName> <port>
   
Example:
   java Node N:test@city.ac.uk 20110
----------------------------------------
Notes:

- The node uses UDP for communication.
- Bootstrap nodes are automatically discovered.
- Supports read, write, exists, and CAS operations.
- Wireshark was used to verify protocol behaviour.

Some known issues:
- External CRN nodes may not always be available.
- Write operations may fail if the node is not responsible for the key.
---------------------------------------------------------------------------
