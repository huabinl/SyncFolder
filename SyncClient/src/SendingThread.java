import java.io.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import filesync.*;
/** @author Huabin Liu (ID. 658274) */

/*
 * this class deals with the mirroring issue (sending messages to contact with server) by using a thread per file
 */
public class SendingThread implements Runnable{

	String filePath;
	String fileName;
	Instruction inst;
	SynchronisedFile fromFile;
	DataInputStream in;
	DataOutputStream out;
	File file;
	
	/*
	 * the constructor of SendingThread class
	 */
	public SendingThread(String fp, String fn, SynchronisedFile sf, DataInputStream i, DataOutputStream o){
		filePath = fp;
		fileName = fn;
		fromFile = sf;
		in = i;
		out = o;
		file = new File(filePath);
	}
	
	/*
	 * the run method of SendingThread class
	 * @see java.lang.Runnable#run()
	 */
	public void run() { 
		try {
			while (file.exists() && (inst = fromFile.NextInstruction()) != null) {
				//synchronized file to the server by using send method
				try {
					send(file, fileName, fromFile, in, out, 0, inst);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			//System.out.println("ah-oh");
		} finally {
			//tell the server to delete file by using send method
			try {
				send(file, fileName, fromFile, in, out, 1, inst);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * The send method is synchronized by class
	 * so that only one send method from all threads of file can contact with server at a time
	 */
	synchronized private static void send(File f, String fn, SynchronisedFile ff,
			DataInputStream i, DataOutputStream o, int id, Instruction inst) throws IOException {
		//all messages are supposed to be transformed to JSON format
		Gson gson = new GsonBuilder().create();
		//tell the information of the target file (i.e. operation name and file name) to the server
		String info = gson.toJson(id + fn); 
		o.writeUTF(info); 
		o.flush();
		String r = i.readUTF();
		String response = gson.fromJson(r, String.class);
		if (response.equals("ok")) {
			//id: 0 for create/modify
			if (id == 0) {
				String endMark = "{\"Type\":\"EndUpdate\"}";
				do {
					String msg = inst.ToJSON();
					System.out.println("Sending: " + msg);
					o.writeUTF(msg);
					o.flush();
					String m = i.readUTF();
					String message = gson.fromJson(m, String.class);
					//when the server calls for new block instruction
					if (message.equals("new")){
						Instruction upgraded = new NewBlockInstruction((CopyBlockInstruction)inst);
						String msg2 = upgraded.ToJSON();
						o.writeUTF(msg2);
						o.flush();
						System.out.println("Sending: " + msg2);
					}
					if (msg.equals(endMark)){
						return;
					}
			    } while (f.exists() && (inst = ff.NextInstruction()) != null);
			//id: 1 for delete
			} else {
				return;
			}		
		}
	}	
}
