import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import filesync.*;
/** @author Huabin Liu (ID. 658274) */

/*
 * this class achieves all the service of server in a single thread
 */
public class Server {

	/*
	 * the main method of server service
	 */
	public static void main(String[] args) throws IOException, CmdLineException {
		//command line option parsing
		ServerOption serverOption = new ServerOption();
		CmdLineParser parser = new CmdLineParser(serverOption);
	    parser.parseArgument(args);
		
	    String rootPath = serverOption.folderPath;
	    int port = serverOption.port;
	    
		//accept sockets for TCP connections, build a thread for one client
		try (ServerSocket server = new ServerSocket(port)) {
			while (true) {
				Socket socket = server.accept();
				Thread t = new Thread(() -> serverClient(socket, rootPath));
				t.setDaemon(true);
				t.start();
			}
		}
	}

	/*
	 * this thread connect to the particular client and contact with it.
	 */
	private static void serverClient(Socket socket, String rp) {
		//all messages are supposed to be transformed to JSON format
		Gson gson = new GsonBuilder().create();
		SynchronisedFile toFile = null;
		InstructionFactory instFact=new InstructionFactory();
		
		
		try (Socket clientSocket = socket) {
			DataInputStream in = new DataInputStream(clientSocket.getInputStream());
			DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());			
			
			while(true) {
				String message = in.readUTF();
				//the message is the information of target file
				if (message.charAt(0) != '{') {
					toFile = fileInfo(rp, gson, toFile, out, message);
				//the message is an instruction
				} else {
					Instruction receivedInst = instFact.FromJSON(message);
					try {
						toFile.ProcessInstruction(receivedInst);
						//ask for next block
						String next = gson.toJson("next");
						out.writeUTF(next);
						out.flush();
					} catch (IOException e) {
						e.printStackTrace();
						System.exit(-1); 
					//ask for a new block instruction to update the file
					} catch (BlockUnavailableException e) {
						try {
							String newOne = gson.toJson("new");
							out.writeUTF(newOne);
							out.flush();
							String message2 = in.readUTF();
							//the message is an instruction
							if (message.charAt(0) == '{') {
								Instruction receivedInst2 = instFact.FromJSON(message2);
								toFile.ProcessInstruction(receivedInst2);
							//the message is the information of target file
							} else {
								toFile = fileInfo(rp, gson, toFile, out, message2);
							}
						} catch (IOException e1) {
							e1.printStackTrace();
							System.exit(-1);
						} catch (BlockUnavailableException e1) {
							assert(false);
						}
					}		
				}		
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * this method deals with the information of the target file
	 * find/create/delete the target file in server
	 * set its SynchronisedFile (maybe, except for the delete action)
	 */
	private static SynchronisedFile fileInfo(String rp, Gson gson,
			SynchronisedFile toFile, DataOutputStream out, String message)
			throws IOException {
		String filePath;
		String msg = gson.fromJson(message, String.class);
		String ok = gson.toJson("ok");
		filePath = rp + File.separator + msg.substring(1);
		File file = new File(filePath);
		//create/modify a file
		if(msg.charAt(0) == '0'){
			//check the existence of a file
			if(!file.exists()){
				file.createNewFile();
			}
			toFile = new SynchronisedFile(filePath);
			out.writeUTF(ok);
			out.flush();
		}
		//delete an existing file
		if (msg.charAt(0) == '1') {
			if (file.exists()) {
				file.delete();
			}
			out.writeUTF(ok);
			out.flush();
		}
		return toFile;
	}
}
