import java.io.IOException;

public class ExamplePlugin extends TPConnector
{
  public ExamplePlugin()
  {
      super("tpexampleplugin",false,true,true); // debug version!
  }

  public static void main(String[] args)
  {
    if(args.length != 1)
    {
      System.out.println("ERROR: Please give one argument to send as status update.");
      return;
    }
     System.out.println("Pairing and sending status update for the example status.");
     ExamplePlugin dum = new ExamplePlugin();
     try{
       dum.connect();
       dum.sendMessage(dum.constructJSONStateUpdate("examplestate",args[0]));
       dum.close();
     } catch (IOException e)
     {
       e.printStackTrace();
     }

     int conamount = getAllConnectors().size();
     System.out.println("Connectors left: "+conamount);
  }
}
