import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class SimpleHTTPServer {
	 
    ServerSocket myServerSocket;
    boolean server = true;
 
 
    public SimpleHTTPServer(int port) 
    { 
        try
        { 
            myServerSocket = new ServerSocket(port); 
        } 
        catch(IOException ioe) 
        { 
            System.out.println("Could not create server socket on port."); 
            System.exit(-1); 
        } 
 
 
// Successfully created Server Socket. Now wait for connections. 
        while(server) 
        {                        
            try
            { 
                // Accept incoming connections. 
                Socket clientSocket = myServerSocket.accept(); 
 
        
                // For each client, we will start a service thread to 
                // service the client requests. 
				ClientService client = new ClientService(clientSocket);
                client.start(); // Start a Service thread 
 
            } 
            catch(IOException ioe) 
            { 
                System.out.println("Exception encountered on accept. Ignoring. Stack Trace :"); 
                ioe.printStackTrace(); 
            } 
 
        }
 
        try
        { 
            myServerSocket.close();  
        } 
        catch(Exception ioe) 
        { 
           System.out.println("Problem stopping server socket");  
        } 
 
 
 
    } 
 
    public static void main (String[] args) 
    { 
       String port=args[0];
    	new SimpleHTTPServer(Integer.parseInt(port));        
    }
}