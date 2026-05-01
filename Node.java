// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  YOUR_NAME_GOES_HERE
//  YOUR_STUDENT_ID_NUMBER_GOES_HERE
//  YOUR_EMAIL_GOES_HERE


// DO NOT EDIT starts
// This gives the interface that your code must implement.
// These descriptions are intended to help you understand how the interface
// will be used. See the RFC for how the protocol works.

import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.net.DatagramSocket;
import java.util.*;

interface NodeInterface {


    /* These methods configure your node.
     * They must both be called once after the node has been created but
     * before it is used. */
    
    // Set the name of the node.
    public void setNodeName(String nodeName) throws Exception;

    // Open a UDP port for sending and receiving messages.
    public void openPort(int portNumber) throws Exception;


    /*
     * These methods query and change how the network is used.
     */

    // Handle all incoming messages.
    // If you wait for more than delay miliseconds and
    // there are no new incoming messages return.
    // If delay is zero then wait for an unlimited amount of time.
    public void handleIncomingMessages(int delay) throws Exception;
    
    // Determines if a node can be contacted and is responding correctly.
    // Handles any messages that have arrived.
    public boolean isActive(String nodeName) throws Exception;

    // You need to keep a stack of nodes that are used to relay messages.
    // The base of the stack is the first node to be used as a relay.
    // The first node must relay to the second node and so on.
    
    // Adds a node name to a stack of nodes used to relay all future messages.
    public void pushRelay(String nodeName) throws Exception;

    // Pops the top entry from the stack of nodes used for relaying.
    // No effect if the stack is empty
    public void popRelay() throws Exception;
    

    /*
     * These methods provide access to the basic functionality of
     * CRN-25 network.
     */

    // Checks if there is an entry in the network with the given key.
    // Handles any messages that have arrived.
    public boolean exists(String key) throws Exception;
    
    // Reads the entry stored in the network for key.
    // If there is a value, return it.
    // If there isn't a value, return null.
    // Handles any messages that have arrived.
    public String read(String key) throws Exception;

    // Sets key to be value.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean write(String key, String value) throws Exception;

    // If key is set to currentValue change it to newValue.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean CAS(String key, String currentValue, String newValue) throws Exception;

}
// DO NOT EDIT ends

// Complete this!

public class Node implements NodeInterface {
    private String nodeName;
    private String nodeHashID;
    private DatagramSocket socket;
    private int port;

    private HashMap<String, String> addressBook = new HashMap<>();
    private Map<String, String> dataStore = new HashMap<>();

    private Map<String, String> responses = new HashMap<>();
    private int txCounter = 0;
    private Stack<String> relayStack = new Stack<>();
    public void setNodeName(String nodeName) throws Exception {
        this.nodeName = nodeName;
        this.nodeHashID = sha256Hex(nodeName);
    }

    private String sha256Hex(String input) throws  Exception{
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();

        for (byte b: encodedHash){
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private BigInteger xorDistance(String h1, String h2){
        BigInteger a = new BigInteger(h1, 16);
        BigInteger b = new BigInteger(h2, 16);
        BigInteger distance = a.xor(b);
        return distance;
    }

    private List<Map.Entry<String, String>> getClosestAddressPairs(String targetHash){
       List<Map.Entry<String, String>> entries = new ArrayList<>(addressBook.entrySet());

       entries.sort((a, b) ->{
          try {
              BigInteger distA = xorDistance(sha256Hex(a.getKey()), targetHash);
              BigInteger distB = xorDistance(sha256Hex(b.getKey()), targetHash);
              return distA.compareTo(distB);
          }
          catch (Exception e){
              return 0;
          }
       });

       List<Map.Entry<String, String>> closest = new ArrayList<>();

       for (int i = 0; i < entries.size() && i < 3; i++){
           closest.add(entries.get(i));
       }

        return closest;
    }

    private boolean isResponsibleForKey(String key) throws Exception{
        String keyHash = sha256Hex(key);

        List<Map.Entry<String, String>> closest = getClosestAddressPairs(keyHash);

        for (Map.Entry<String, String> entry: closest){
            if (entry.getKey().equals(this.nodeName)){
                return true;
            }
        }
        return false;
    }
    public void openPort(int portNumber) throws Exception {
        this.port = portNumber; // rec port range is 20110–20130. just for future ref
        this.socket = new DatagramSocket(portNumber);

        if (nodeName!= null){
            addressBook.put(nodeName, "10.216.34.172:" + portNumber);//this is just for local testing remember to use the actual ipaddress later on
        }

        addressBook.put("N:bootstrap", "10.200.51.18:20111");

        System.out.println("Bootstrap node added");
    }

    private String generateTransactionID() {
        txCounter = (txCounter + 1) % (26 * 26);

        char a = (char) ('a' + (txCounter / 26));
        char b = (char) ('a' + (txCounter % 26));

        return "" + a + b;
    }


    private Map.Entry<String, String> getAnyKnownNode(){
        for (Map.Entry<String, String> entry: addressBook.entrySet())
        {
            String key = entry.getKey();

            if (key == null){
                continue;
            }

            if (!key.startsWith("N:")){
                continue;
            }

            if (key.equals(this.nodeName)){
                continue;
            }
            return entry;
        }
        return null;
    }

    private void sendToAddress(String message, String address) throws Exception{
        String[] parts = address.split(":");
        InetAddress ip = InetAddress.getByName(parts[0]);
        int port = Integer.parseInt(parts[1]);
        sendMessage(message, ip, port);
    }

    private String sendRequest(String txID, String request, String targetNodeName, String targetAddress) throws Exception{

        String sendMessage = request;
        String sendAddress = targetAddress;
        String responseTxID = txID;

        if (!relayStack.empty()){
            String relayNode = relayStack.peek();
            String relayAddress = addressBook.get(relayNode);

            if (relayAddress == null){
                return null;
            }

            String relayTxID = generateTransactionID();

            sendMessage = relayTxID + " V " + encodeString(targetNodeName) + " " + request;
            sendAddress = relayAddress;
            responseTxID = relayTxID;
        }

        for (int attempt = 0; attempt < 3; attempt++){
            System.out.println("Send attempt " + (attempt + 1) + " " + request);
            sendToAddress(sendMessage, sendAddress);

            Thread.sleep(200);

            String response = waitForResponse(responseTxID, 5000);

            if (response!= null){
                return response;
            }
        }
        return null;
    }

    private String waitForResponse(String txID, int timeoutMS) throws Exception{
        long end = System.currentTimeMillis() + timeoutMS;

        while(System.currentTimeMillis() < end){
            handleIncomingMessages(100);

            if (responses.containsKey(txID)){
                return responses.remove(txID);
            }
        }
        return null;
    }

    private int encodedStringLength(String input) throws Exception{
        int firstSpace = input.indexOf(' ');
        //remember to do a check for malformed string

        int spaceCount = Integer.parseInt(input.substring(0, firstSpace));
        int valueStart = firstSpace + 1;

        if (spaceCount == 0){
            int nextSpace = input.indexOf(' ', firstSpace + 1);

            if (nextSpace == -1){
                return input.length();
            }

            return nextSpace;
        }

        int spaceSeen = 0;

        for (int i = valueStart ; i < input.length(); i++){
            if (input.charAt(i) == ' '){
                spaceSeen++;
            }

            if (spaceSeen == spaceCount){
               // return i + 1;
                int nextSpace = input.indexOf(' ', i + 1);

                if(nextSpace == -1){
                    return input.length();
                }

                return nextSpace;
            }
        }
        throw new Exception("incomplete encoded String");
    }

    private void parseNearestResponse(String response) throws Exception {
        String[] parts = response.split(" ", 3);

        if (parts.length < 3 || !parts[1].equals("O")) {
            return;
        }

        String rest = parts[2];
        int index = 0;

        while (index < rest.length()) {
            String remaining = rest.substring(index);

            String nodeName = decodeString(remaining);
            int nodeEnd = encodedStringLength(remaining);
            index += nodeEnd + 1;

            if (index >= rest.length()) {
                break;
            }

            remaining = rest.substring(index);

            String address = decodeString(remaining);
            int addressEnd = encodedStringLength(remaining);
            index += addressEnd + 1;

            if (nodeName.startsWith("N:") && address.contains(":")){
                addressBook.put(nodeName, address);
            }
        }
    }
    public void handleIncomingMessages(int delay) throws Exception {
        if (delay == 0){
            socket.setSoTimeout(0);
        }
        else{
            socket.setSoTimeout(delay);
        }

        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try{
            socket.receive(packet);
        }catch (SocketTimeoutException e){
            return;
        }

        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

        InetAddress senderAddress = packet.getAddress();
        int senderPort = packet.getPort();

        //handleMessage function here
        handleMessage(message, senderAddress, senderPort);
    }

    private void handleMessage(String message, InetAddress senderAddress, int senderPort) throws  Exception{
        System.out.println("Raw Recieve " + message);
        String[] splitMessage = message.split(" ", 3);

        String transactionID = message.substring(0, 2);

        if (message.charAt(2) != ' '){
            return;
        }

        String type = message.substring(3, 4);

        String rest = "";
        if (message.length() > 5){
            rest = message.substring(5);
        }
       // System.out.println(type + "This is testing length " + splitMessage.length);

        switch (type)
        {
            case "G": {
                handleNameRequest(transactionID, senderAddress, senderPort);
                break;
            }
            case "N": {
                String targetHash = rest;
                handleNearestRequest(transactionID, targetHash, senderAddress, senderPort);
                break;
            }
            case "E": {
                String key = decodeString(rest);
                handleExistsRequest(transactionID, key, senderAddress, senderPort);
                break;
            }
            case "R": {

                System.out.println("Read Handling");
                System.out.println("datastore "+ dataStore);
                String key = decodeString(rest);
                System.out.println("hasKey: " + key);
                System.out.println("responsible " + isResponsibleForKey(key));

                handleReadRequest(transactionID, key, senderAddress, senderPort);

                break;
            }
            case "W": {
                System.out.println("Write Handling");
                String key = decodeString(rest);
                int keyEnd = encodedStringLength(rest);

                String valuePart = rest.substring(keyEnd + 1);
                System.out.println(valuePart);
                String writeValue = decodeString(valuePart);

                System.out.println("Double checking the write part is " + writeValue);
                handleWriteRequest(transactionID, key, writeValue, senderAddress, senderPort);
                break;
            }
            case "C": {
                System.out.println("Handling CAS! REQUEST");
                String key = decodeString(rest);
                int keyEnd = encodedStringLength(rest);

                String currentPart = rest.substring(keyEnd + 1);
                String currentValue  = decodeString(currentPart);
                int currentEnd = encodedStringLength(currentPart);

                String newPart = currentPart.substring(currentEnd + 1);
                String newValue = decodeString(newPart);

                handleCASRequest(transactionID, key, currentValue, newValue, senderAddress, senderPort);
                break;
            }
            case "V":{
                handleRelayRequest(transactionID, rest, senderAddress, senderPort);
                break;
            }

            case "H":{
                String realName = decodeString(rest);

                String senderHost = senderAddress.getHostAddress();
                String sendValue = senderHost + ":" + senderPort;

                addressBook.put(realName, sendValue);

                responses.put(transactionID, message);
                break;
            }

            case "S":
            case "X":
            case "F":
            case "D":
            case "O":
            {
                System.out.println("Inserting transactionId with Message");
                responses.put(transactionID, message);
                break;
            }
            default:
                break;

        }
    }

    private void sendMessage(String message, InetAddress address, int port) throws Exception{
        System.out.println("Raw Send " + message);

        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }

    private void handleExistsRequest(String transactionID, String key, InetAddress senderAddress, int senderPort) throws Exception{
        boolean hasKey = dataStore.containsKey(key);
        boolean responsible = isResponsibleForKey(key);
        System.out.println("Handling Exists REQUEST");

        String responseCode;

        if(hasKey){
            responseCode = "Y";
        }
        else if (responsible) {
            responseCode = "N";
        }
        else {
            responseCode = "?";
        }
        String response = transactionID + " F " + responseCode;
        sendMessage(response, senderAddress, senderPort);
    }
    private void handleNameRequest(String transactionID, InetAddress senderAddress, int senderPort) throws Exception{
        System.out.println("Handling Name REQUEST");

        String response = transactionID + " H " + encodeString(nodeName);
        sendMessage(response, senderAddress, senderPort);
    }
    private void handleNearestRequest(String transactionID, String targetHash, InetAddress senderAddress, int senderPort) throws Exception {
        // will doo  this later on ->>> Need to check is the hashId is valid
        System.out.println("Handling NEAREST REQUEST");

        List<Map.Entry<String, String>> closest = getClosestAddressPairs(targetHash);

        StringBuilder response = new StringBuilder();
        response.append(transactionID).append(" O");

        for(Map.Entry<String, String> entry: closest){
            response.append(" ");
            response.append(encodeString(entry.getKey()));
            response.append(" ");
            response.append(encodeString(entry.getValue()));
        }

        sendMessage(response.toString(), senderAddress, senderPort);
    }


    private boolean hasKey(String key){
        if(key.startsWith("N:")){
            return addressBook.containsKey(key);
        }
        return dataStore.containsKey(key);
    }

    private String getValue(String key){
        if (key.startsWith("N:")){
            return addressBook.get(key);
        }
        return dataStore.get(key);
    }
    private void handleReadRequest(String transactionID, String key, InetAddress senderAddress, int senderPort) throws Exception{
        boolean hasKey = hasKey(key);
        boolean responsible = isResponsibleForKey(key);
        String response;
        System.out.println("Handling Read REQUEST");


        if (hasKey){
            String value = getValue(key);
            response = transactionID + " S Y " + encodeString(value);
        }
        else if (responsible){
            response = transactionID + " S N";
        }
        else{
            response = transactionID + " S ?";
        }

        sendMessage(response, senderAddress, senderPort);
    }

    private void storeKeyValue(String key, String value) {
        if (key.startsWith("N:")) {
            addressBook.put(key, value);
        } else {
            dataStore.put(key, value);
        }
    }
    private void handleWriteRequest(String transactionID, String key, String value, InetAddress senderAddress, int senderPort) throws Exception{
        boolean hasKey = dataStore.containsKey(key);
        boolean responsible = isResponsibleForKey(key);
        System.out.println("Handling Write REQUEST");

        String responseCode;

        if (hasKey){
            storeKeyValue(key, value);
            responseCode = "R";
        }
        else if (responsible)
        {
            storeKeyValue(key, value);
            responseCode = "A";
        }
        else
        {
            responseCode = "X";
        }
        String response = transactionID + " X " + responseCode;
        sendMessage(response, senderAddress, senderPort);
    }

    private void handleCASRequest(String transactionID, String key, String currentValue, String newValue, InetAddress senderAddress, int senderPort) throws  Exception{
        boolean hasKey = dataStore.containsKey(key);
        boolean responsible = isResponsibleForKey(key);

        System.out.println("Handling CAS REQUEST");

        String responseCode;

        if (hasKey)
        {
            String storedValue = dataStore.get(key);

            if(storedValue.equals(currentValue))
            {
                dataStore.put(key, newValue);
                responseCode = "R";
            }
            else
            {
                responseCode = "N";
            }
        } else if (responsible)
        {
            dataStore.put(key, newValue);
            responseCode = "A";
        }
        else{
            responseCode = "X";
        }

        String response = transactionID + " D " + responseCode;
        sendMessage(response, senderAddress, senderPort);
    }

    private void handleRelayRequest(String relayTXID, String rest, InetAddress originalSenderAddress, int originalSenderPort) throws  Exception{
        System.out.println("Handling Relay REQUEST");

        String targetNode = decodeString(rest);
        int targetEnd = encodedStringLength(rest);

        String message = rest.substring(targetEnd + 1);
        String targetAddress = addressBook.get(targetNode);

        if (targetAddress == null){
            return;
        }

        if (message.length() < 2){
            return;
        }

        String newTxID = generateTransactionID();
        String rewriteMessage = newTxID + message.substring(2);

        sendToAddress(rewriteMessage, targetAddress);
        String targetResponse = waitForResponse(newTxID, 5000);

        if (targetResponse == null){
            return;
        }

        if (targetResponse.length() < 2){
            return;
        }

        String responseToOriginalSender  = relayTXID + targetResponse.substring(2);

        sendMessage(responseToOriginalSender, originalSenderAddress, originalSenderPort);
    }
    private void requestNearest(String targetHash) throws  Exception{
        Map.Entry<String, String> targetNode = getAnyKnownNode();

        if (targetNode == null){
            return;
        }

        String txID = generateTransactionID();
        String request = txID + " N " + targetHash;

        String response = sendRequest(txID, request, targetNode.getKey(),targetNode.getValue());


        if (response != null){
            parseNearestResponse(response);
        }
    }
    private  String decodeString(String input) throws  Exception{
        if (input == null || input.isEmpty()){
            throw new Exception("Invalid encoded string");
        }

        int firstSpace = input.indexOf(' ');

        int spaceCount = Integer.parseInt(input.substring(0, firstSpace));

        int start = firstSpace + 1;

        if (spaceCount == 0){
            int nextSpace = input.indexOf(' ', start);
            if (nextSpace == -1){
                return input.substring(start);
            }
            return input.substring(start, nextSpace);
        }

        int spaceSeen = 0;

        for (int i = start; i < input.length(); i++){
            if (input.charAt(i) == ' '){
                spaceSeen++;

                if(spaceSeen == spaceCount) {
                    int nextSpace = input.indexOf(' ', i + 1);

                    if (nextSpace == -1) {
                        return input.substring(start);
                    }
                    return input.substring(start, nextSpace);
                }
            }
        }
        throw new Exception("Incomplete encoded String");
    }
    private String encodeString(String s) {
        int spaces = 0;

        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ') {
                spaces++;
            }
        }

        return spaces + " " + s + " ";
    }

    public boolean isActive(String nodeName) throws Exception {
        System.out.println("Checking if " + nodeName + " is active");
        Map.Entry<String, String> targetNode;

        if (addressBook.get(nodeName) != null){
            targetNode = Map.entry(nodeName, addressBook.get(nodeName));
        }
        else{
            targetNode = null;
        }

        if (targetNode == null){
            return false;
        }

        String txID = generateTransactionID();
        String request = txID + " G";

        String response =  sendRequest(txID, request, targetNode.getKey(),targetNode.getValue());

        String[] parts = response.split(" ", 3);

        if(parts.length < 3 || !parts[1].equals("H")){
            return false;
        }

        String returnedName = decodeString(parts[2]);
        return returnedName.equals(nodeName);
    }
    
    public void pushRelay(String nodeName) throws Exception {
        if (nodeName == null){
            throw new Exception("Invalid NodeName");
        }

        relayStack.push(nodeName);

    }

    public void popRelay() throws Exception {
        System.out.println("Pop the relaystack!");
        if (!relayStack.empty()){
           relayStack.pop();
       }
    }

    public boolean exists(String key) throws Exception {
	    if (key == null){
            throw new Exception("Key cannot be null");
        }
        System.out.println("Checking if this exists " + key);
        Map.Entry<String, String> targetNode = getAnyKnownNode();

        if (targetNode == null){
            return dataStore.containsKey(key);
        }

        String txID = generateTransactionID();
        String request = txID + " E " + encodeString(key);

        String response =  sendRequest(txID, request, targetNode.getKey(),targetNode.getValue());

        if (response == null){
            return false;
        }

        String[] parts = response.split(" ", 4);
        if(parts.length < 3 || !parts[1].equals("F")){
            return false;
        }

        String code = parts[2];

        return code.equals("Y");
    }
    
    public String read(String key) throws Exception {
	    if (key == null){
            throw new Exception("Key cannot be null");
        }
        System.out.println("This is reading " + key);

        requestNearest(sha256Hex(key));
        List<Map.Entry<String, String>> PossibleNodes = getClosestAddressPairs(sha256Hex(key));

        for (Map.Entry<String, String> targetNode: PossibleNodes){
            if (targetNode.getKey().equals(this.nodeName)){
                continue;
            }

            String txID = generateTransactionID();
            String request = txID + " R " + encodeString(key);

            String response = sendRequest(txID, request, targetNode.getKey(), targetNode.getValue());

            if (response == null){
                continue;
            }

            String[] parts = response.split(" ", 4);

            String responseCode = parts[2];

            if(responseCode.equals("Y") && parts.length >= 4){
                return decodeString(parts[3]);
            }

            if (responseCode.equals("N")){
                return null;
            }
        }
        return dataStore.get(key);
    }

    public boolean write(String key, String value) throws Exception {
        //System.out.println("This is writing " + key + " " + value);


	    if (key == null || value == null){
            throw new Exception("Key or Value cannot be null");
        }

        List<Map.Entry<String, String>> candidates = getClosestAddressPairs(sha256Hex(key));

        for (Map.Entry<String, String> targetNode : candidates) {
            if (targetNode.getKey().equals(this.nodeName)) continue;

            String txID = generateTransactionID();
            String request = txID + " W " + encodeString(key) + encodeString(value);

            String response = sendRequest(txID, request, targetNode.getKey(), targetNode.getValue());

            if (response == null) continue;

            String[] parts = response.split(" ", 4);

            if (parts.length >= 3 && parts[1].equals("X")) {
                String code = parts[2];

                if (code.equals("A") || code.equals("R")) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
	    if (key == null || currentValue == null || newValue == null){
            throw  new Exception("Arguments cannot be null");
        }

        Map.Entry<String, String> targetNode = getAnyKnownNode();


        handleIncomingMessages(1);

        if (targetNode == null){ //Again this is just for local testing
            if (dataStore.containsKey(key)){
                String stored = dataStore.get(key);

                if (stored.equals(currentValue)){
                    dataStore.put(key, newValue);
                    return true;
                }
                return false;
            }

            if (isResponsibleForKey(key)){
                dataStore.put(key, newValue);
                return true;
            }
            return false;
        }


        String txID = generateTransactionID();
        String request = txID + " C " + encodeString(key) + " " + encodeString(currentValue) + encodeString(newValue);
        String response =  sendRequest(txID, request, targetNode.getKey(),targetNode.getValue());

        if (response == null){
            return false;
        }

        String[] parts = response.split(" ", 4);
        if (parts.length < 3 || !parts[1].equals("D")){
            return false;
        }

        String code = parts[2];

        return code.equals("R") || code.equals("A");
    }

    //this bit is purely for testing
    public void addTestAddress(String nodeName, String address){
        addressBook.put(nodeName, address);
    }

    public void testNearest(String hash) throws Exception{
        Map.Entry<String, String> targetNode = getAnyKnownNode();

        String txID = generateTransactionID();
        String request = txID + " N " + hash;

        String response = sendRequest(txID, request, targetNode.getKey(),targetNode.getValue());

        System.out.println("Nearest response "+ response);
    }

    public String testHash(String key) throws Exception{
        return sha256Hex(key);
    }
}
