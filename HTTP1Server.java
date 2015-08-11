import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.file.AccessDeniedException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * HTTP1Server.java<p>
 *
 * <p>
 *
 * Usage:  Invoke this class using a UNIX script or Windows batch file or equivalent.
 *         All of the CGI environment variables must be passed from the script into
 *         the Java application using the -D option.
 *
 *
 *
 * @version 1.0
 * @author
 *
 */

class HTTPRequest implements Runnable {
    private Socket socket;
    private final static String[] REQUEST_METHODS = {"GET", "POST"};
    private final static String HTTP_VERSION = "HTTP/1.0";
    private final static String CRLF = "\r\n";
    private final static SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    private static String testCasePartSeparator = "------------";

    public HTTPRequest(Socket clientSocket) {
        socket = clientSocket;
    }

    /**
     *
     * Determine if the REQUEST_METHOD used to
     * send the data from the browser was the GET method.
     *
     * @return true, if the REQUEST_METHOD was GET.  false, otherwise.
     *
     */
    public static boolean MethGet(String requestMethod)
    {
        boolean returnVal = false;
        if (requestMethod != null && requestMethod.equalsIgnoreCase("GET")) {
            returnVal=true;
        }
        return returnVal;
    }

    /**
     *
     * Determine if the REQUEST_METHOD used to
     * send the data from the browser was the POST method.
     *
     * @return true, if the REQUEST_METHOD was POST.  false, otherwise.
     *
     */
    public static boolean MethPost(String requestMethod)
    {
        boolean returnVal = false;
        if (requestMethod != null && requestMethod.equalsIgnoreCase("POST")) {
            returnVal=true;
        }
        return returnVal;
    }

    /**
     * Entry point of the thread to handle the request
     */
    public void run() {
        try {
            processRequest();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        } finally {
            HTTP1ServerASP.decrementConnections();
        }
    }

    /**
     * Sends an error code to client
     *
     * @param os output stream to client
     * @param br input from client
     * @param errorCode error code to send
     * @throws Exception when message sending failed
     */
    private void sendErrorMessageThenCloseConnection(DataOutputStream os, int errorCode) throws Exception {
        String message = "";

        switch (errorCode) {
            case 304:
                message = "Not Modified";
                break;
            case 400:
                message = "Bad Request";
                break;
            case 403:
                message = "Forbidden";
                break;
            case 404:
                message = "Not Found";
                break;
            case 405:
                message = "Method Not Allowed";
                break;
            case 408:
                message = "Request Timeout";
                break;
            case 411:
                message = "Length Required";
                break;
            case 500:
                message = "Internal Server Error";
                break;
            case 501:
                message = "Not Implemented";
                break;
            case 503:
                message = "Service Unavailable";
                break;
            case 505:
                message = "HTTP Version Not Supported";
                break;
        }

        // +1 day for expiration of any file
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, 1);

        os.writeBytes(HTTP_VERSION + " " + errorCode + " " + message + CRLF);

        os.writeBytes(CRLF);

        os.flush();

        // Whenever failure comes, sleep for half a second before closing connection
        Thread.sleep(500);
        socket.close();

        //HTTP1Server.decrementConnections();
    }

    /**
     * Validate and check if the provided request is valid for HTTP 1.0
     *
     * @param request Command request
     * @return true if valid, otherwise false
     */
    private boolean isValidRequest(String request) {
        for (String requestMethod : REQUEST_METHODS) {
            if (requestMethod.equals(request)) {
                return true;
            }
        }

        return false;
    }

    private void processRequest() throws Exception {
        socket.setSoTimeout(3000);

        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        InputStreamReader isr = new InputStreamReader(socket.getInputStream());

        DataOutputStream os = new DataOutputStream(socket.getOutputStream());

        // Read the first line of the request which contains the request
        Queue<String> requestLines = new LinkedList<String>();
        String requestLine;

        String payload = null;
        try {
            requestLine = br.readLine();

            while (requestLine != null && !requestLine.isEmpty()) {
                requestLines.offer(requestLine);
                requestLine = br.readLine();
            }

            ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
            while(br.ready()) {
                outBuffer.write(br.read());
            }

            if(outBuffer.size() > 0) {
                requestLines.offer(outBuffer.toString());
                payload = outBuffer.toString();
            }
            requestLine = requestLines.poll();
        } catch (SocketTimeoutException e) {
            sendErrorMessageThenCloseConnection(os, 408);
            return;
        }

        if(requestLine != null) {
            // Process the request line make sure it is of the right format
            String[] tokens = requestLine.split(" ");

            // In HTTP 1.0 we should be expecting 3 tokens, first is the command, second is the resource, 3rd is the HTTP version
            // The resource file should always starts with a '/', otherwise it is malformed and should be returned as a bad request
            if (tokens.length != 3 || !tokens[1].startsWith("/") || !tokens[2].startsWith("HTTP/") || tokens[2].split("/").length != 2) {
                sendErrorMessageThenCloseConnection(os, 400);
                return;
            }

            double httpVersion = 0;

            // Verify the HTTP version, the only supported HTTP version is 1.0 and below
            try {
                httpVersion = Double.parseDouble(tokens[2].split("/")[1]);
            } catch (Exception e) {
                sendErrorMessageThenCloseConnection(os, 400);
                return;
            }

            // Check if the given command is part of HTTP 1.0 specs
            if (!isValidRequest(tokens[0])) {
                sendErrorMessageThenCloseConnection(os, 400);
                return;
            }

            // Right now, only GET and POST is the supported command, any other than that is is a not implemented response
            if (!MethGet(tokens[0]) && !MethPost(tokens[0])) {
                sendErrorMessageThenCloseConnection(os, 501);
                return;
            }

            // The only supported version is 1.1 and below
            if (httpVersion > 1.1) {
                sendErrorMessageThenCloseConnection(os, 505);
                return;
            }

            if (MethGet(tokens[0])) {
                // Attempt to access the resource if it exists
                // Prepend a "." so that file request is within the current directory
                String fileName = null;
                if(tokens[1].contains(".cgi")) {
                    fileName = tokens[1];
                    processGetRequest(fileName, requestLines, os);
                } else {
                    fileName = "./" + tokens[1];
                    processGetRequestForResources(fileName, os);
                }

            } else {
                processPostRequest(tokens[1], requestLines, payload, os);
            }
        }

        // Wait half a second before closing connection
        Thread.sleep(500);
        socket.close();

        //HTTP1Server.decrementConnections();
    }

    private void processGetRequest(String fileName, Queue<String> requestLines, DataOutputStream os) throws Exception {

        File f = new File("." + fileName);
        if(!f.exists()) {
            sendErrorMessageThenCloseConnection(os, 404);
        }
        if(!f.canExecute()) {
            sendErrorMessageThenCloseConnection(os, 403);
        }

        Map<String, String> envVariables = processHeaders(requestLines);

        envVariables.put("Request-Method", "GET");
        envVariables.put("Content-Length", "0");

        if(!envVariables.containsKey("Content-Length")) {
            sendErrorMessageThenCloseConnection(os, 411);
        }

        String[] parsedEnvVariable = processEnvVariables(envVariables, os);

        Process process = Runtime.getRuntime().exec("." + fileName, parsedEnvVariable);
        InputStream in = process.getInputStream();
        OutputStream out = process.getOutputStream();

        process.waitFor();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int i = -1;
        while((i = in.read()) != -1) {
            buffer.write(i);
        }

        buffer.flush();
        String responsePayload = buffer.toString();

        processResponse(responsePayload, os);
        os.flush();
    }

    private void processPostRequest(String fileName, Queue<String> requestLines, String payload, DataOutputStream os) throws Exception {

        Map<String, String> envVariables = processHeaders(requestLines);

        envVariables.put("SCRIPT_NAME", fileName);

        envVariables.put("Request-Method", "POST");

        //TODO check how the Content-Length and Content-Type headers are passed.
        // Perform modification check
        if(!envVariables.containsKey("Content-Length")) {
            sendErrorMessageThenCloseConnection(os, 411);
        }

        if(!envVariables.containsKey("Content-Type")) {
            sendErrorMessageThenCloseConnection(os, 500);
        } else if(!envVariables.get("Content-Type").equals("application/x-www-form-urlencoded")
                && !envVariables.get("Content-Type").contains("multipart/form-data")) {
            sendErrorMessageThenCloseConnection(os, 500);
        }

        if(!fileName.endsWith(".cgi")) {
            sendErrorMessageThenCloseConnection(os, 405);
        }

        try {
            File f = new File("." + fileName);
            if(!f.exists()) {
                sendErrorMessageThenCloseConnection(os, 404);
            }
            if(!f.canExecute()) {
                sendErrorMessageThenCloseConnection(os, 403);
            }

            envVariables.put("Payload", payload);

            String[] parsedEnvVariable = processEnvVariables(envVariables, os);

            Process process = Runtime.getRuntime().exec("." + fileName, parsedEnvVariable);
            InputStream in = process.getInputStream();
            OutputStream out = process.getOutputStream();

            //String payload = envVariables.get("Payload");
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

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int i = -1;
            while((i = in.read()) != -1) {
                buffer.write(i);
            }

            buffer.flush();
            String response = buffer.toString();

            processResponse(response, os);
            //os.writeBytes(CRLF);
        } catch(Exception e) {
            System.out.println("e = " + e);
        }
    }

    private void processResponse(String response, DataOutputStream os) throws IOException {
        String[] headerAndPayload = response.split("\n\n");
        String responseHeaders = headerAndPayload[0];

        String[] parsedResponseHeaders = parseResponseHeaders(responseHeaders);
        String responsePayload = null;

            /* first \n\n combination is for separating out the header and payload
            * if there are \n\n in the payload then we need to stitch them back
            * there can be a better way to do it but right now going with this approach
            * as it is simple */
        if(headerAndPayload.length > 2) {
            for(int j = 1; j < headerAndPayload.length; j++) {
                responsePayload = responsePayload + "\n\n" + headerAndPayload[j];//stitching \n\n back to payload
            }
        } else {
            if(headerAndPayload.length == 2) {
                responsePayload = headerAndPayload[1];
            }
        }
        // If program reaches here then everything is good
        // Send the header
        // +1 day for expiration of any file
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, 1);

        if(responsePayload == null || responsePayload.isEmpty()) {
            os.writeBytes(HTTP_VERSION + " 204 No Content" + CRLF);
        } else {
            os.writeBytes(HTTP_VERSION + " 200 OK" + CRLF);
        }
        os.writeBytes("Allow: GET, HEAD, POST" + CRLF);
        os.writeBytes("Content-Encoding: identity" + CRLF);
        String length = "Content-Length: " + (responsePayload == null ? 0 : responsePayload.length());
        os.writeBytes(length  + CRLF);
        os.writeBytes("Content-Type: text/html" + CRLF);
        os.writeBytes("Expires: " + format.format(calendar.getTime()) + CRLF);
        //os.writeBytes("Set-Cookie: name=Nicholas; expires=Sat, 02 May 2016 23:38:25 GMT" + CRLF);

        for(String header : parsedResponseHeaders) {
            if(header != null && !header.isEmpty()) {
                os.writeBytes(header + CRLF);
            }
        }
        os.writeBytes(CRLF + responsePayload);
    }

    private String[] parseResponseHeaders(String responseHeaders) {
        String[] splittedResponseHeaders = responseHeaders.split("\n");
        //Map<String, String> parsedResponseHeaders = new HashMap<String, String>();
        String[] trimmedResponseHeaders = new String[splittedResponseHeaders.length];
        int i = 0;
        for(String responseHeader : splittedResponseHeaders) {
            String trimmed = responseHeader.trim();
            /*int loc = trimmed.indexOf(":");
            String headerKey = trimmed.substring(0, loc);
            String headerValue = trimmed.substring(loc+1, trimmed.length());
            parsedResponseHeaders.put(headerKey, headerValue);*/
            if(!trimmed.isEmpty()) {
                trimmedResponseHeaders[i] = trimmed;
                i++;
            }
        }
        return trimmedResponseHeaders;
    }

    private Map<String, String> processHeaders(Queue<String> requestLines) throws UnsupportedEncodingException {
        String requestLine = null;
        Map<String, String> envVariables = new HashMap<String, String>();

        String payload = null;
        // Handle the next request lines, if there is
        while (!requestLines.isEmpty()) {
            requestLine = requestLines.poll();
            String[] header = requestLine.split(": ");
            if(header.length == 2) {
                String key = new String(header[0]);
                String value = new String(header[1]);
                envVariables.put(key, value);
            } else {
                String key = new String(header[0]);
                payload = URLDecoder.decode(key, "UTF-8");
                envVariables.put("Payload", payload);
            }
        }
        return envVariables;
    }

    /*private String parsePayload(String output, BufferedReader br) {
        if(output.endsWith("</td></tr>"))
        return null;
    }*/

    private String[] processEnvVariables(Map<String, String> requestHeaders, DataOutputStream os) throws Exception{
        int i = 0;
        String[] envVariable = new String[requestHeaders.size()];
        for(String key : requestHeaders.keySet()) {
            if(key.equals("Content-Length")) {
                int contentLength = 0;
                try {
                    contentLength = Integer.parseInt(requestHeaders.get("Content-Length"));
                    int payloadLength = 0;
                    if(requestHeaders.get("Content-Type") != null
                            && requestHeaders.get("Content-Type").contains("multipart/form-data")) {
                        payloadLength = requestHeaders.get("Payload").getBytes().length;
                    } else {
                        payloadLength = getPayloadLength(requestHeaders.get("Payload"));
                    }
                    if(payloadLength != 0) {
                        contentLength = payloadLength;
                    }
                } catch (Exception e) {
                    sendErrorMessageThenCloseConnection(os, 411);
                }
                envVariable[i] = "CONTENT_LENGTH=" + contentLength;
            } else if(key.equals("From")) {
                envVariable[i] = "HTTP_FROM=" + requestHeaders.get(key);
            } else if(key.equals("User-Agent")) {
                envVariable[i] = "HTTP_USER_AGENT=" + requestHeaders.get(key);
            } else if(key.equals("Request-Method")) {
                envVariable[i] = "REQUEST_METHOD=" + requestHeaders.get(key);
            } else if(key.equals("Cookie")) {
                envVariable[i] = "HTTP_COOKIE=" + requestHeaders.get(key);
            } else if(key.equals("Content-Type")) {
                envVariable[i] = "CONTENT_TYPE=" + requestHeaders.get(key);
            }else{
                envVariable[i] = key + "=" + requestHeaders.get(key);
            }

            i++;
        }
        return envVariable;
    }

    private int getPayloadLength(String payload) {
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

    private void processGetRequestForResources(String fileName, DataOutputStream os) throws Exception {

        // Verify that all contents of the file is readable with permission
        try {
            BufferedReader inFile = new BufferedReader(new FileReader(fileName));
            java.lang.StringBuilder line = new StringBuilder();
            line.append(inFile.readLine());

            String temp = null;
            while ((temp = inFile.readLine()) != null) {
                line.append(temp);
            }

            inFile.close();
        } catch (AccessDeniedException e) {
            //ACHTUNG!

            // If forbidden return an error
            sendErrorMessageThenCloseConnection(os, 403);
            return;
        } catch (FileNotFoundException e) {

            //ACHTUNG!

            if (e.getMessage().toLowerCase().contains("access is denied") ||
                    e.getMessage().toLowerCase().contains("permission denied")) {
                // If forbidden return an error
                sendErrorMessageThenCloseConnection(os, 403);
            } else {
                // Stop if file doesn't exists
                sendErrorMessageThenCloseConnection(os, 404);
            }

            return;
        } catch (Exception e) {

            // Anything else that is causing the resource not to be accessed is an internal error
            sendErrorMessageThenCloseConnection(os, 500);
            return;
        }

        // If program reaches here then everything is good
        // Send the header
        // +1 day for expiration of any file
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, 1);

        os.writeBytes(HTTP_VERSION + " 200 OK" + CRLF);
        os.writeBytes("Allow: GET, HEAD, POST" + CRLF);
        os.writeBytes("Content-Encoding: identity" + CRLF);
        os.writeBytes("Content-Length: " + new File(fileName).length() + CRLF);
        os.writeBytes("Content-Type: " + getContentType(fileName) + CRLF);
        os.writeBytes("Expires: " + format.format(calendar.getTime()) + CRLF);
        os.writeBytes("Last-Modified: " + format.format(new File(fileName).lastModified()) + CRLF);
        os.writeBytes(CRLF);

        // HEAD request do not return the body
        FileInputStream fis = new FileInputStream(fileName);
        sendBytes(fis, os);

        fis.close();
        os.flush();
    }

    /**
     * Send the file contents to the client
     *
     * @param fis input file where to get content
     * @param os output going to client
     * @throws Exception exceptions won't happen here cause it was already
     * pre-checked
     */
    private void sendBytes(InputStream fis, OutputStream os) throws Exception {
        // construct a 1k buffer to hold bytes their way to the socket
        byte[] buffer = new byte[1024];
        int bytes;

        // copy the requested file into the socket's output stream
        while ((bytes = fis.read(buffer)) != -1) {
            os.write(buffer, 0, bytes);
        }
    }

    /**
     * Identify the content type
     *
     * @param fileName name of the file
     * @return the content type
     */
    private String getContentType(String fileName) {
        fileName = fileName.toLowerCase();

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

        return "application/octet-stream";
    }
}

public class HTTP1ServerASP {

    public static final int MAX_CONNECTIONS = 50;
    private static int numConnections = 0;

    /**
     *
     * The main routine is included here as a test CGI script to
     * demonstrate the use of all of the methods provided above.
     * You can use it to test your ability to execute a CGI script written
     * in Java.  See the sample UNIX script file included above to see
     * how you would invoke this routine.<p>
     *
     * Please note that this routine references the member functions directly
     * (since they are in the same class), but you would have to
     * reference the member functions using the class name prefix to
     * use them in your own CGI application:<p>
     * <pre>
     *     System.out.println(HTTP1Server.HtmlTop());
     * </pre>
     *
     * @param args An array of Strings containing any command line
     * parameters supplied when this program in invoked.  Any
     * command line parameters supplied are ignored by this routine.
     *
     */
    public static void main( String args[] ) throws Exception
    {

        if (args.length != 1) {
            System.out.println("Error: Please provide the server port number as an argument.");
            return;
        }

        // Create a server socket
        ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]));

        while (true) {
            // Listen for a client connection
            Socket clientSocket = serverSocket.accept();

            // Validate service availability: reject the client if the maximum number of connections reache
            if (HTTP1ServerASP.getNumberOfConnections() == HTTP1ServerASP.MAX_CONNECTIONS) {
                DataOutputStream os = new DataOutputStream(clientSocket.getOutputStream());
                os.writeBytes("HTTP/1.0 503 Service Unavailable \r\n");

                os.writeBytes("\r\n");

                os.flush();

                clientSocket.close();
                continue;//since max connections has reached, dont invoke new thread and continue to listen for new client socket request
            }

            HTTP1ServerASP.increaseConnections();
            // Handle the request in another thread
            new Thread(new HTTPRequest(clientSocket)).start();
        }
    }

    /**
     * A thread safe function that adds connection by 1
     */
    public static synchronized void increaseConnections() {
        numConnections++;
    }

    /**
     * A thread safe function that remove a connection by 1
     */
    public static synchronized void decrementConnections() {
        numConnections--;
    }

    /**
     * Return the number of connections currently available
     *
     * @return number of connections
     */
    public static synchronized int getNumberOfConnections() {
        return numConnections;
    }

}
