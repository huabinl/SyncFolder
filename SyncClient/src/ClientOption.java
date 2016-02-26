import org.kohsuke.args4j.Option;
/** @author Huabin Liu (ID. 658274) */

/*
 * this class contains all the command options of client, i.e. -f,-h,-p.
 */
public class ClientOption {

	  @Option(name = "-f", usage = "folder-path", required = true)
	  public String folderPath;
	 
	  @Option(name = "-h", usage = "hostname", required = true)
	  public String hostName;
	 
	  //port has a default value 4444
	  @Option(name = "-p", usage = "port", required = false)
	  public int port = 4444;
}
