import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.lang.Thread;
import java.lang.Runnable;

public class TPConnector {
    // Constants
    final private int HEARTBEAT_INTERVAL_DEFAULT = 200;
    final private String DEFAULT_HOST = "127.0.0.1";
    final private int DEFAULT_PORT = 12136;

    // Static Arraylist for listing all TPConnectors
    private static List<TPConnector> allConnectors;

    // Arraylist for sent and received messages of this connector
    private List<String> MessagesToSend;
    private List<String> MessagesReceived;

    // Basic information for contacting TP
    private String PluginID; // only set at object creation
    public String HostName;
    public int HostPort;
    public boolean DebugState = false;
    public boolean DebugTime = true;

    // Variable that defines whether we continue listening or simply close
    // By default this simply sends a message and then quits
    public boolean ContinueListening = false;

    // Socket & Data Streams & Heartbeat Runnable
    private Socket TPSocket;
    private DataInputStream StreamIn;
    private DataOutputStream StreamOut;
    private PluginHeartbeat Heartbeat;
    private Thread HeartbeatThread;

    // SOME ABSTRACTS
    public void checkListening() { }

    // *** BASIC QUERY METHODS
    // Plugin ID Query
    public String queryPluginID()
    {
      return PluginID;
    }

    // Connection Checks
    public int hasConnectedSocket()
    {
      if((TPSocket != null)) return 1;
      else return 0;
    }

    public int hasConnectedStreams()
    {
      if((StreamIn != null) & (StreamOut != null)) return 2;
      else if((StreamIn != null) | (StreamOut != null)) return 1;
      else if(TPSocket == null) return -1;
      else return 0;
    }

    private void debugMessage(String msg)
    {
      if(!DebugState) return;

      String start = "DEBUG";
      if(DebugTime)
      {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        start = start + " ("+dtf.format(now)+"): ";
      } else { start = start + ": "; }

      System.out.println(start+msg);
    }

    // *** HELPER Functionality
    // Array meddling for personal JSON creation...
    private static String concatenateData(String[] args, String delimiter)
    {
      if(args.length == 0) { return ""; }
      else { return String.join(delimiter, args); }
    }

    private static String concatenateData(String[] args)
    {
      return concatenateData(args," ");
    }

    private static String[] explodeData(String args, String delimiter)
    {
      if(args.length() == 0) return new String[0];
      return args.split(delimiter);
    }

    private String addQuotes(String val)
    {
      return "\""+val+"\"";
    }

    // Basic message makers
    public String constructJSONPAir() {
      return "{\"type\":\"pair\",\"id\":\"" + PluginID + "\"}";
    }

    public String constructJSONStateUpdate(String id, String value) {
      return "{\"type\":\"stateUpdate\",\"id\":\"" + id + "\",\"value\":\"" + value + "\"}";
    }

    public String constructJSONChoiceUpdate(String id, String[] values) {
      for(int i=0;i<values.length;i++) { values[i] = addQuotes(values[i]); }
      final String valstring = concatenateData(values,",");
      return "{\"type\":\"choiceUpdate\",\"id\":\"" + id + "\",\"value\":["+valstring+"]}";
    }

    public String constructJSONSpecificChoiceUpdate(String id, String actionId, String[] values) {
      for(int i=0;i<values.length;i++) { values[i] = addQuotes(values[i]); }
      final String valstring = concatenateData(values,",");

      return "{\"type\":\"choiceUpdate\",\"id\":\"" + id + "\",\"instanceId\":\"" + actionId + "\"\"value\":["+valstring+"]}";
    }

    // *** FUNCTIONALITY:
    // Keep a list of all connectors, add into it when creating an instance
    static // this makes a Vector where all TPConnectors are stored
    {
      allConnectors = new ArrayList<>();
    }

    public static synchronized List<TPConnector> getAllConnectors() // get the Vector
    {
      if(allConnectors.size() == 0) return null;

      List<TPConnector> clone = new ArrayList<TPConnector>(allConnectors);
      return clone;
    }

    // Functionality for finding a TPConnector of a certain pluginID
    public static synchronized TPConnector findTPConnector(String id) // find
    {
      List<TPConnector> Cons = new ArrayList<TPConnector>(allConnectors);

      for(TPConnector con : Cons)
      {
        if(con.queryPluginID() == id) return con;
      }
      return null;
    }

    public static synchronized TPConnector spotTPConnector(String id)
    {
      TPConnector Con = findTPConnector(id);
      if(Con == null) return new TPConnector(id);
      else
      {
        Con.checkListening();
        return Con;
      }
    }

    // Functionality: remove and destory connectors; overloaded
    public synchronized static void removeConnector(TPConnector Con)
    {
      if(Con == null) return;
      try {
        Con.close();
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
      allConnectors.remove(Con);
    }

    public synchronized static void removeConnector(String id)
    {
      TPConnector Con = findTPConnector(id);
      removeConnector(Con);
    }

    public void removeConnector()
    {
      removeConnector(this);
    }

    // *** CONSTRUCTOR
    // Class constructor; PluginID is necessary for creation.
    public TPConnector(String id)
    {
      this(id,false,false,false);
    }

    // Some overloading with default values
    public TPConnector(String id, boolean StartListening)
    {
      this(id,StartListening,false,false);
    }

    public TPConnector(String id, boolean StartListening, boolean Debug, boolean Timer)
    {
      this.PluginID = id; // only place to set this variable!!!!
      this.ContinueListening = StartListening;
      this.DebugState = Debug;
      this.DebugTime = Timer;

      debugMessage("DEBUG: creating message arrays.");
      MessagesToSend = new ArrayList<String>();
      MessagesReceived = new ArrayList<String>();

      if(StartListening) debugMessage("DEBUG: creating new listening connector "+id);
      else debugMessage("DEBUG: creating new connector "+id);

      // Start the heartbeat here!

      allConnectors.add( this ); // add this to the list
    }

    // *** FUNCTIONALITY
    // Functionality to connect socket & streams and close them
    public void connect() throws IOException
    {
      if(PluginID == null) { throw new IOException("ERROR: no pluginID"); }

      if(HostName == null)
      {
        debugMessage("DEBUG: Using default host: "+DEFAULT_HOST);
        HostName = DEFAULT_HOST;
      }

      if(HostPort == 0)
      {
        debugMessage("DEBUG: Using default port: "+DEFAULT_PORT);
        HostPort = DEFAULT_PORT;
      }

      if(TPSocket == null)
      {
        debugMessage("DEBUG: creating a socket and connecting it & streams");
        TPSocket = new Socket(HostName,HostPort);
      } else {
        debugMessage("DEBUG: socket already exists; trying to re-establish streams");
        StreamOut.close();
        StreamIn.close();
      }

      StreamOut = new DataOutputStream(TPSocket.getOutputStream());
      StreamIn = new DataInputStream(TPSocket.getInputStream());

      debugMessage("DEBUG: pairing the socket with Touch Portal");
      this.writeMessage(this.constructJSONPAir());
    }

    public void close() throws IOException
    {
      if(PluginID == "") { throw new IOException("ERROR: no pluginID"); }
      if(TPSocket == null) { throw new IOException("ERROR: no socket to close"); }

      debugMessage("DEBUG: closing datainputstream");
      this.StreamIn.close();
      debugMessage("DEBUG: closing dataoutputstream");
      this.StreamOut.close();
      debugMessage("DEBUG: closing socket");
      this.TPSocket.close();
    }

    // *** FUNCTIONALITY
    // Send a message
    public void writeMessage(String msg)
    {
      debugMessage("DEBUG: adding message to pool: "+msg);
      MessagesToSend.add(msg);

      // checkHeartbeat();
    }

    // this will send one message to the plugin - called by the heartbeat
    private void sendMessage() throws IOException
    {
      if(MessagesToSend.size() < 1) return;
      if((TPSocket == null) || (StreamOut == null)) { throw new IOException("ERROR: no socket or output stream"); }

      String msg = MessagesToSend.get(0);
      MessagesToSend.remove(0);
      StreamOut.writeBytes(msg+"\n");
      StreamOut.flush();
    }

    private void readMessages() throws IOException
    {
      if((TPSocket == null) || (StreamOut == null)) { throw new IOException("ERROR: no socket or output stream"); }

      InputStreamReader streamReader = new InputStreamReader(StreamIn);
      BufferedReader reader = new BufferedReader(streamReader);

      try {
        String value;
        while((value = reader.readLine()) != null) {
          MessagesReceived.add(value); }
      } catch (IOException e) { throw e; }

      reader.close();
      // parseMessages is called by heartbeat.
    }

    private void parseMessage(String msg)
    {
      debugMessage("Message received: "+msg);
    }

    private void parseMessages()
    {
      while(MessagesReceived.size() > 1)
      {
        String msg = MessagesReceived.get(0);
        MessagesReceived.remove(0);
        parseMessage(msg);
      }
      // Iterate over MessagesReceived and remove then and parse them
      // this will parse messages in MessagesReceived and send them to corresponding methods, see below
    }

    private void receiveDynamicAction(String actionID, String dataJSON) { }

    private void receiveDynamicListChoice(String actionID, String listID, String instanceID, String value) { }

    // *** MAIN
    public static void main(String[] args) throws IOException
    {
      String SYNTAX_MESSAGE = ""+
        "TPCONNECTOR - FOR CONNECTING TOUCH PORTAL PLUGINS\n"+
        "  TPConnector <pluginID> [host:port] - starts a listening TPConnector\n"+
        "    note: if there is no listening plugin prestarted, TPConnector will close after messages\n"+
        "    note: you need to use this, if you need a non-default host:port sending via console\n"+
        "  TPConnector <pluginID> choice <stateid>:choice1|choice 2|choice 3...\n"+
        "  TPConnector <pluginID> state <stateid>:<newvalue> - send a state update to TP\n"+
        "  TPConnector <pluginID> choice2 <stateid>:choice1;choice2;choice3...\n"+
        "  TPConnector <pluginID> specchoice <stateid>:choice1|choice 2|choice 3...\n"+
        "  TPConnector <pluginID> specchoice2 <stateid>:choice1;choice2;choice3...\n"+
        "    note: alternative 2 is if you want to use | in the choices\n";

      System.out.println(SYNTAX_MESSAGE);
    }

    // *** HEARTBEAT FUNCTIONALITY incl. a nested runnable class
    public void startHeartbeat()
    {
      if((Heartbeat != null)||(HeartbeatThread != null))
      {
        debugMessage("Heartbeat or Heartbeat Thread exists already.");
        return;
      }
      debugMessage("Creating new heartbeat and starting it.");
      Heartbeat = this.new PluginHeartbeat();
      HeartbeatThread = new Thread(Heartbeat);
      HeartbeatThread.start();
    }

    public void stopHeartbeat()
    {
      if(Heartbeat == null)
      {
        debugMessage("Tried to stop non-existent heartbeat.");
        return;
      }
      Heartbeat.stopHeartbeat();
    }

    private Thread checkHeartbeatThread()
    {
       debugMessage("Checking for heartbeat...");
       if(HeartbeatThread == null) { startHeartbeat(); }

       return HeartbeatThread;
    }

    private void beat()
    {
      // override this if you want
    }

    private class PluginHeartbeat implements Runnable
    {
      private int HeartbeatInterval;
      private boolean ReportAllHeartbeats;
      private boolean ContinueHeartbeat = true;

      public PluginHeartbeat(int hbint, boolean allhearts)
      {
        HeartbeatInterval = hbint;
        ReportAllHeartbeats = allhearts;
      }

      public PluginHeartbeat()
      {
        this(HEARTBEAT_INTERVAL_DEFAULT,false);
      }

      public void stopHeartbeat() { ContinueHeartbeat = false; }

      @Override
      public void run()
      {
        debugMessage("Running heartbeat for "+TPConnector.this.PluginID);

        while(ContinueHeartbeat)
        {
          try {
              TPConnector.this.beat(); // do something that another plugin author wants!
              TPConnector.this.sendMessage(); // only one msg sent per heartbeat
              TPConnector.this.readMessages(); // all messages read every heartbeat ...
              TPConnector.this.parseMessages(); // ... and parsed.
              // Let the thread sleep for a while.
              Thread.sleep(HeartbeatInterval); }
          catch (IOException | InterruptedException e) {
              debugMessage("Heartbeat for "+TPConnector.this.PluginID+" was interrupted.");
              e.printStackTrace(); }
        }

       debugMessage("Heartbeat for "+TPConnector.this.PluginID+" stopped.");
       // Let the superclass know this can now die.
      }
    }
}
