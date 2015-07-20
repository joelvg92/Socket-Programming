import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class ClientService extends Thread {
	Socket socket;
	boolean runThread = true;
    private final static String line=System.getProperty("line.separator");
	Timer timer = new Timer();

	public ClientService() {
		super();
	}

	ClientService(Socket sock) {
		System.out.println("Thread ID " + this.getId());
		socket = sock;
		timer.schedule(new RequestTimeoutHandler(sock), 3 * 1000);//set a timer for 3 seconds
	}

	public void run() {
		// Obtain the input stream and the output stream for the socket
		BufferedReader in = null;
		PrintWriter out = null;


		try {
			in = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			out = new PrintWriter(new OutputStreamWriter(
					socket.getOutputStream()));

			// Run in a loop until runThread is set to false
			while (runThread) {
				// read incoming stream
				BufferedReader br = null;
				String filename;
				String input = in.readLine();
				System.out.println("Input from Client :" + input);
				
				if (input != null) {
				    timer.cancel();
					String[] delim = input.split(" ");
			
					if (delim.length != 2) {
						out.println("400 Bad Request"+line);//prints out if arguments are not of length 2
						out.flush();

					} else if (!delim[1].startsWith("/")) {
						out.println("400 Bad Request"+line); //prints out if the 2nd argument does not start with /
						out.flush();
					} else if (!delim[0].equals("GET")) {
						out.println("501 Not Implemented"+line); //prints out if the first agument does not equal get.
						out.flush();
					} else if((delim.length==2) && (delim[1].startsWith("/")) && (delim[0].equals("GET"))) {//checks if there are 2 arguments and also if 2nd argument starts with / and first with GET.
						filename=System.getProperty("user.dir")+File.separator+"doc_root"+File.separator+delim[1];
						File f = new File(filename);
						if(f.exists()){//If file exist then send a 200 ok response
						try{
						br=new BufferedReader(new FileReader(filename));
					    String readline;
						out.println("200 OK"+line);
						out.flush();
						while ((readline = br.readLine()) != null) {
							 out.println(readline);
						     out.flush();
						}
						}catch (IOException e) {
							e.printStackTrace();
						}
						}
					else{
						out.println("404 Not Found"+line);
						     out.flush();
					}
					
				} else{
					out.println("500 Internal Error"+line);
					out.flush();
				}
				}else {
					runThread=false;
					out.println("408 Request Timeout"+line);
					out.flush();
				    
				}

			}
		} catch (Exception e) {
			//e.printStackTrace();
		} finally {
			try {
				in.close();
				out.close();
				socket.close();
			} catch (IOException ioe) {
				//ioe.printStackTrace();
			}
		}
	}

}
//Handles how much time it should wait for the request
class RequestTimeoutHandler extends TimerTask {
	private final static String line=System.getProperty("line.separator");
	Socket socket;
	PrintWriter out = null;

	public RequestTimeoutHandler(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		try {
			out = new PrintWriter(new OutputStreamWriter(
					socket.getOutputStream()));
			out.println("408 Request Timeout"+line);
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			out.close();
			try {
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

}
