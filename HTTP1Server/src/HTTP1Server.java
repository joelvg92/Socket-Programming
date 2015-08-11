//Joel Varghese
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;


public class HTTP1Server {
	 
    ServerSocket myServerSocket;
    boolean server = true;
    private static int c = 0;
 
 
    public HTTP1Server(int port) 
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
               if(getCurrentConnections()<=50){
            	   ClientService client = new ClientService(clientSocket);
               	Thread t = new Thread(client);
               	t.start();
               	increment();
               }else{
            	   DataOutputStream os = new DataOutputStream(clientSocket.getOutputStream());
            	   os.writeBytes("HTTP/1.0 503 Service Unavailable" + "\r\n");
					os.flush();
					os.close();
					clientSocket.close();
               }
                	
                	
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
    
    private synchronized void increment() {
        c++;
    }

    public static synchronized void decrement() {
        c--;
    }

    private synchronized int getCurrentConnections() {
        return c;
    }
 
    public static void main (String[] args) 
    { 
       String port=args[0];
    	new HTTP1Server(Integer.parseInt(port));        
    }
}

class ClientService implements Runnable {
	Socket socket;
	boolean runThread = true;
	private final static String line = "\r\n";
	Timer timer = new Timer();
	String protocol = "HTTP/1.0";
	Queue<String> requestLines = new LinkedList<String>();
    String requestLine=null;
	 private final static SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

	public ClientService() {
		super();
	}

	ClientService(Socket sock) {
		 format.setTimeZone(TimeZone.getTimeZone("GMT"));
		socket = sock;
		timer.schedule(new RequestTimeoutHandler(sock), 3 * 1000);// set a timer
																	// for 3
																	// seconds
	}

	public void run() {
		// Obtain the input stream and the output stream for the socket
		BufferedReader in = null;
		//Socket Output Stream = null;
		DataOutputStream os = null;
        
		try {
			in = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			os = new DataOutputStream(socket.getOutputStream());

			// Run in a loop until runThread is set to false
			while (runThread) {
				try {
					// read incoming stream
					String filename;
					String input = in.readLine();
					String [] delim2 = null;
					String[] delim = input.split(" ");
					double httpval = 0;
					int contentlength=0;
					if (input != null) {
						timer.cancel();
						if(delim.length == 3){
							delim2=delim[2].split("/");
							httpval=Double.parseDouble(delim2[1]);
							if(httpval>1.0){
								os.writeBytes(protocol+" 505 HTTP Version Not Supported" + line); // prints out
							os.flush();
							runThread = false;
							}
							else if(((delim[0].equals("PUT"))||(delim[0].equals("DELETE"))||(delim[0].equals("LINK")))||(delim[0].equals("UNLINK"))){
								os.writeBytes(protocol+" 501 Not Implemented" + line); // prints out
							os.flush();
							runThread = false;
							}
							else if ((delim[2].equals("HTTP/1.0"))
									&& (delim[1].startsWith("/"))
									&& ((delim[0].equals("GET"))||(delim[0].equals("HEAD")))) {// checks if there are
																	// 2 arguments and
																	// also if 2nd
																	// argument starts
																	// with / and first
																	// with GET.
								filename = "./resources/"+delim[1];
								//System.out.println("Client ID : " + this.getId() + " | "+filename);
								File f = new File(filename);
								if (f.exists()) {// If file exist then send a 200 ok
													// response
									if(!f.canRead()){
										os.writeBytes(protocol + " 403 Forbidden" + line);
										os.flush();
										
									}else{
										//Set requested resource expiry date to one day
								          Calendar calendar = Calendar.getInstance();
								          calendar.setTime(new Date());
								          calendar.add(Calendar.DATE, 1);  
										
											String inputNextLine = in.readLine();
								            if (inputNextLine.startsWith("If-Modified-Since:") && !delim[0].equals("HEAD")) {
								                // Perform modification check
								                try {
								                    String dateString = inputNextLine.split(": ")[1].trim();
								                    Date sinceDate = format.parse(dateString);
								                    
								                    if(sinceDate.compareTo(new Date(new File(filename).lastModified())) >= 0) {
								                        // File isn't modified
								                    	os.writeBytes(protocol + " 304 Not Modified" + line);
								                    	os.writeBytes("Expires: " + format.format(calendar.getTime()) + line);
														os.flush();
														os.close();
														runThread = false;
														socket.close();
														HTTP1Server.decrement();
														in.close();
								                        return;
								                    }
								                } catch(Exception e) {
								                    // Ignore the request if unparsable
								                }
								            }
								        
										
										
										//get length of file
									      int fileLength = (int)f.length();
									      
									    //get the file's MIME content type
									      String content = getContentType(filename);
									      
									      FileInputStream fileIn = null;
									        //create byte array to store file data
									        byte[] fileData = new byte[fileLength];
									       
										try {
											
											
											//open input stream from file
									          fileIn = new FileInputStream(f);
									          //read file into byte array
									          fileIn.read(fileData);
											  
									          
									          
									          os.writeBytes(protocol + " 200 OK" + line);
									          os.writeBytes("Allow: GET, HEAD, POST" + line);
									          os.writeBytes("Content-Encoding: identity" + line);
									          os.writeBytes("Content-Length: " + new File(filename).length() + line);
									          os.writeBytes("Content-Type: " + content + line);
									          os.writeBytes("Expires: " + format.format(calendar.getTime()) + line);
									          os.writeBytes("Last-Modified: " + format.format(new File(filename).lastModified()) + line);
									          
									          
									          //Do not send response body if the request method is HEAD
									          if(!delim[0].equals("HEAD")){
									        	  os.writeBytes(line);
									        	  sendBytes(filename, os,fileData);
									          }
									          
									          //os.writeBytes(line);
									        fileIn.close();
									        os.flush();
											runThread = false;
										} catch (IOException e) {
											e.printStackTrace();
										}
									}
									
									
								} else {
									os.writeBytes(protocol+" 404 Not Found" + line);//if file not found return a 404 error
									os.flush();
									runThread = false;
								}

							}else if ((delim[2].equals("HTTP/1.0"))
									&& (delim[1].startsWith("/"))
									&& ((delim[0].equals("POST")))) {// checks if there are
																	// 2 arguments and
																	// also if 2nd
																	// argument starts
																	// with / and first
																	// with GET.
								ByteArrayOutputStream buffer = new ByteArrayOutputStream();
								filename = delim[1];
								Map<String, String> envs = new HashMap<String, String>();
						        envs.put("SCRIPT_NAME", filename);
						        String payload = null;
						        // Handle the next request lines, if there is
						        while (!requestLines.isEmpty()) {
						            requestLine = requestLines.poll();
						            String[] header = requestLine.split(": ");
						            if(header.length == 2) {
						                String key = new String(header[0]);
						                String value = new String(header[1]);
						                envs.put(key, value);
						            } else {
						                String key = new String(header[0]);
						                payload = URLDecoder.decode(key, "UTF-8");
						                envs.put("Payload", payload);
						            }
						        }
						        if(!envs.containsKey("Content-Length")) {
						        	os.writeBytes(protocol + " 411 Length Required" + line);
									os.flush();
						        }

						        if(!envs.containsKey("Content-Type")) {
						        	os.writeBytes(protocol + " 500 Internal Server Error" + line);
									os.flush();
						        } else if(!envs.get("Content-Type").equals("application/x-www-form-urlencoded")) {
						        	os.writeBytes(protocol + " 500 Internal Server Error" + line);
									os.flush();
						        }

						        if(!filename.endsWith(".cgi")) {
						        	os.writeBytes(protocol + " 405 Method Not Allowed" + line);
									os.flush();
						        }
								//System.out.println("Client ID : " + this.getId() + " | "+filename);
								
						        File f = new File("."+filename);
								if(!f.exists()){
									os.writeBytes(protocol + " 404 Not Found " + line);
									os.flush();
								}
								
								 if (f.exists()) {// If file exist then send a 200 ok
													// response
									if(!f.canRead()||(!f.canExecute())){
										os.writeBytes(protocol + " 403 Forbidden" + line);
										os.flush();
										
									}else{
										
										String[] parsedenv = processenv(envs, os);
										
										
							            Process process = Runtime.getRuntime().exec("."+filename, parsedenv);
							            java.io.InputStream inp = process.getInputStream();
							            OutputStream out = process.getOutputStream();

							            if(payload != null && !payload.isEmpty() ) {
							                String[] parsedPayload = payload.split("&");
							                for(int i = 0; i < parsedPayload.length; i++) {
							                    String parsed = parsedPayload[i];
							                    if(i != 0) {
							                        parsed = "&" + parsed;
							                    }
							                    out.write(parsed.getBytes());
							                    out.flush();
							                }
							            }

							            process.waitFor();


							            int i = -1;
							            while((i = inp.read()) != -1) {
							                buffer.write(i);
							            }

							            buffer.flush();
							            String responsepload = buffer.toString();  
										
										  Calendar calendar = Calendar.getInstance();
								          calendar.setTime(new Date());
								          calendar.add(Calendar.DATE, 2);  
								          
								          if(responsepload.isEmpty()) {
								                os.writeBytes(protocol + " 204 No Content" + line);
								                os.flush();
								            }else{ 
								            os.writeBytes(protocol + " 200 OK" + line);
								            os.writeBytes("Allow: GET, HEAD, POST" + line);
								            os.writeBytes("Content-Encoding: identity" + line);
								           // System.out.println(responsepload.length());
								            os.writeBytes("Content-Length: " + responsepload.length()  + line);
								            os.writeBytes("Content-Type: text/html" + line);
								            os.writeBytes("Expires: " + format.format(calendar.getTime()) + line);
								            os.writeBytes(line);
								            os.writeBytes(responsepload);
								            os.flush();
											runThread = false;
								            }
									}	
									
									}else {
										os.writeBytes(protocol+" 404 Not Found" + line);//if file not found return a 404 error
										os.flush();
										runThread = false;
									}	
								}else {
									os.writeBytes(protocol+" 400 Bad Request" + line);//if file not found return a 404 error
									os.flush();
									runThread = false;
								}

							}else {
								os.writeBytes(protocol+" 400 Bad Request" + line);//if file exist and not able to access then send a 500 error
								os.flush();
								runThread = false;
							}
						}
						
						if ((delim.length != 3) ||(!delim[1].startsWith("/"))||((!delim[0].equals("GET"))||(!delim[0].equals("POST"))||(!delim[0].equals("HEAD")))){
							os.writeBytes(protocol+" 400 Bad Request" + line);// prints out if
																	// arguments are
																	// not of length
																	// 2
							os.flush();
							runThread = false;

						}
					 else {
						runThread = false;
						os.writeBytes(protocol+" 408 Request Timeout" + line);//If request is null then return a 408 request timeout errror
						os.flush();

					}
				} catch (Exception e) {
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			try {
				//Thread.sleep(500);
				if(in!=null) {
					in.close();
				}
				if(os !=null) {
					os.close();
				}
				if(socket != null && !socket.isClosed())
				{
					socket.close();
					
				}
				HTTP1Server.decrement();
				//System.out.println("Client ID : " + this.getId() + " Closed Socket");
			} catch (Exception ioe) {
				//ioe.printStackTrace();
			}
		}
	}
	
	/**
	   * getContentType returns the proper MIME content type
	   * according to the requested file's extension.
	   *
	   * @param fileRequested File requested by client
	   */
	  private String getContentType(String fileName)
	  {fileName = fileName.toLowerCase();

      if (fileName.endsWith(".txt")) {
          return "text/plain";
      }

      if (fileName.endsWith(".htm") || fileName.endsWith(".html")) {
          return "text/html";
      }

      if (fileName.endsWith(".gif")) {
          return "image/gif";
      }

      if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
          return "image/jpeg";
      }

      if (fileName.endsWith(".png")) {
          return "image/png";
      }

      if (fileName.endsWith(".pdf")) {
          return "application/pdf";
      }

      if (fileName.endsWith(".gz")) {
          return "application/x-gzip";
      }

      if (fileName.endsWith(".zip")) {
          return "application/zip";
      }

      return "application/octet-stream";}
	  
	  public String[] processenv(Map<String, String> requestHeaders, DataOutputStream os) throws Exception{
	        int i = 0;
	        String[] env = new String[requestHeaders.size()];
	        for(String key : requestHeaders.keySet()) {
	            if(key.equals("Content-Length")) {
	                int contentlength = 0;
	                try {
	                    contentlength = Integer.parseInt(requestHeaders.get("Content-Length"));
	                    int payloadLength = parsepayload(requestHeaders.get("Payload"));
	                    if(payloadLength != 0) {
	                        contentlength = payloadLength;
	                    }
	                } catch (Exception e) {
	                	os.writeBytes(protocol+" 411 Length Required" + line);//If request is null then return a 408 request timeout errror
						os.flush();
	                }
	                env[i] = "CONTENT_LENGTH=" + contentlength;
	            } else if(key.equals("From")) {
	                env[i] = "HTTP_FROM=" + requestHeaders.get(key);
	            } else if(key.equals("User-Agent")) {
	                env[i] = "HTTP_USER_AGENT=" + requestHeaders.get(key);
	            } else{
	                env[i] = key + "=" + requestHeaders.get(key);
	            }

	            i++;
	        }
	        return env;
	    }
	  /**
	     * Send the file contents to the client
	     *
	     * @param fis input file where to get content
	     * @param os output going to client
	     */
	  public void sendBytes(String fileName, OutputStream os, byte[] fileData) throws Exception {
	    	 FileInputStream fis = new FileInputStream(fileName);
	        int bytes;
	        // copy the requested file into the socket's output stream
	        while ((bytes = fis.read(fileData)) != -1) {
	            os.write(fileData, 0, bytes);
	        }
	    }

	  public int parsepayload(String payload) {
	        int length = 0;
	        if(payload != null && !payload.isEmpty()) {
	            String[] parsed = payload.split("&");
	            for(String s : parsed) {
	                length += s.length();
	            }
	            length += parsed.length - 1;//to add 1 & at the beginning of each line in the payload but not at the first payload param
	        }
	        return length;
	    }
}

// Handles how much time it should wait for the request
class RequestTimeoutHandler extends TimerTask {
	//private final static String line = System.getProperty("line.separator");
	private final static String line = "\r\n";
	Socket socket;
	DataOutputStream os=null;
	String protocol = "HTTP/1.0";

	public RequestTimeoutHandler(Socket socket) {
		this.socket = socket;
	}

	public void run() {
		try {
			os = new DataOutputStream(socket.getOutputStream());
			os.writeBytes(protocol+" 408 Request Timeout" + line);
			os.flush();
		} catch (IOException e) {
		} finally {
			
			try {
				os.close();
				socket.close();
				HTTP1Server.decrement();
			} catch (Exception e) {
			}
		}

	}

}
