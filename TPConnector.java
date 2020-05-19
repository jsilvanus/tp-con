import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

public class TPConnector {
    // Constants
    final private int LISTENING_INTERVAL_MILLISECONDS = 200;
    final private String DEFAULT_HOST = "127.0.0.1";
    final private int DEFAULT_PORT = 12136;

    // Static Vector for listing all TPConnectors
    private static List<TPConnector> allConnectors;

    // Basic information for contacting TP
    private String PluginID; // only set at object creation
    public String HostName;
    public int HostPort;
    public boolean DebugState = false;
    public boolean DebugTime = true;

    // Variable that defines whether we continue listening or simply close
    // By default this simply sends a message and then quits
    public boolean ContinueListening = false;

    // Socket & Data Streams
    private Socket TPSocket;
    private DataInputStream StreamIn;
    private DataOutputStream StreamOut;

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

      if(StartListening) debugMessage("DEBUG: creating new listening connector "+id);
      else debugMessage("DEBUG: creating new connector "+id);

      allConnectors.add( this ); // add this to the list
    }

    // *** FUNCTIONALITY
    // Functionality to use a method for host & port setting. Set only once! (finals)
    public void setHost(String host, int port)
    {
      HostName = host;
      HostPort = port;
    }

    // Functionality to connect socket & streams and close them
    public void connect(boolean Pair) throws IOException
    {
      if(PluginID == null) throw new IOException("ERROR: no pluginID");

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
      if(Pair) {
        try { this.sendMessage(this.constructJSONPAir()); }
        catch (IOException e) { e.printStackTrace(); }
      }
    }

    public void connect() throws IOException
    {
      this.connect(true);
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
    public void sendMessage(String msg) throws IOException
    {
      if(TPSocket == null || StreamOut == null) { throw new IOException("ERROR: no socket or output stream"); }
      debugMessage("DEBUG: sending message: "+msg);
      StreamOut.writeBytes(msg+"\n");
      StreamOut.flush();
    }

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
}
