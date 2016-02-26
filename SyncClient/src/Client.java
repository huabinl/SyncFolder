import static java.nio.file.StandardWatchEventKinds.*;
import static org.kohsuke.args4j.ExampleMode.ALL;

import java.io.*;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.*;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import filesync.*;
/** @author Huabin Liu (ID. 658274) */
/*
 * this class achieves the main thread of client
 */
public class Client {

	//Two hashmaps for each file, key is the name of the file, one value is its SynchronisedFile, the other is its Thread.
	private static Map<String, SynchronisedFile> sfMap = new HashMap<String,SynchronisedFile>();
	private static Map<String, Thread> tMap = new HashMap<String, Thread>(); 
	
	/*
	 * the main method of client service
	 */
	public static void main(String[] args) throws IOException, InterruptedException, CmdLineException {
		//command line option parsing
		ClientOption clientOption = new ClientOption();
	    CmdLineParser parser = new CmdLineParser(clientOption);
	    parser.parseArgument(args);
		
	    String rootPath = clientOption.folderPath;
	    String host = clientOption.hostname;
	    int port = clientOption.port;
		
	    //build a TCP connection to the server
		try (Socket socket = new Socket(host, port)) {
			DataInputStream in = new DataInputStream(socket.getInputStream());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
		 
			//build a thread for each file in the folder and mirror it initially.
			File root = new File(rootPath);
			File[] files = root.listFiles();
	        for (File file : files) {
			    String filePath = file.getAbsolutePath();
			    File tempFile = new File (filePath.trim());
			    String fileName = tempFile.getName();
			    SynchronisedFile syncFile = new SynchronisedFile(filePath);	
			    sfMap.put(fileName, syncFile);
			    Thread t = new Thread (new SendingThread(filePath, fileName, syncFile, in, out));
			    tMap.put(fileName, t);
			    t.setDaemon(true);
			    t.start();
			    syncFile.CheckFileState();
			}
	        
	        //check the change happened in the folder
	        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
				Map<WatchKey, Path> keyMap = new HashMap<>();
			    Path path = Paths.get(rootPath);
				keyMap.put(path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), path);
				WatchKey watchKey;
						
				do {
					watchKey = watcher.take();
							
					for(WatchEvent<?> event: watchKey.pollEvents()){
						WatchEvent.Kind<?> kind = event.kind();
								
						if (kind == OVERFLOW) {
						    continue;
						}
								
						Path eventPath = (Path) event.context();
						String fileName = eventPath.toString();
						
						//the operation is modifying a file
						if (kind == ENTRY_MODIFY) {
							//check the state of the corresponding SynchronisedFile
							sfMap.get(fileName).CheckFileState();
						}
						
						//the operation is creating a new file
						if (kind == ENTRY_CREATE) {
							//build a new thread for this file and mirror it.
							String filePath = rootPath + File.separator + fileName;
							SynchronisedFile syncFile = new SynchronisedFile(filePath);	
						    sfMap.put(fileName, syncFile);
						    Thread t = new Thread (new SendingThread(filePath, fileName, syncFile, in, out));
						    tMap.put(fileName, t);
						    t.isDaemon();
						    t.start();
						    syncFile.CheckFileState();
						}
						
						//the operation is delete an existing file
						if (kind == ENTRY_DELETE) {
							//interrupt its corresponding
							tMap.get(fileName).interrupt();
							//remove its record in hashmaps
							sfMap.remove(fileName);
							tMap.remove(fileName);
						}
					}
		        } while(watchKey.reset());
			}
	        in.close();
	        out.close();
		}	    	
    } 
}
