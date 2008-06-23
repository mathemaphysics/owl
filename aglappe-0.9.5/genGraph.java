import gnu.getopt.Getopt;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import proteinstructure.Pdb;
import proteinstructure.PdbCodeNotFoundError;
import proteinstructure.PdbLoadError;
import proteinstructure.PdbasePdb;
import proteinstructure.PdbfilePdb;
import proteinstructure.RIGraph;
import proteinstructure.TemplateList;
import tools.MySQLConnection;


public class genGraph {
	/*------------------------------ constants ------------------------------*/
	
	public static final String			PDB_DB = "pdbase";
	public static final String			DB_HOST = "white";								
	public static final String			DB_USER = getUserName();
	public static final String			DB_PWD = "nieve";
	
	/*---------------------------- private methods --------------------------*/
	/** 
	 * Get user name from operating system (for use as database username). 
	 * */
	private static String getUserName() {
		String user = null;
		user = System.getProperty("user.name");
		if(user == null) {
			System.err.println("Could not get user name from operating system.");
		}
		return user;
	}
	
	public static void main(String[] args) throws IOException {
		
		String progName = "genGraph";
		
		String help = "Usage, 3 options:\n" +
				"1)  "+progName+" -i <listfile> -d <distance_cutoff> -t <contact_type> [-o <output_dir>] [-D <pdbase_db>] [-P] \n" +
				"2)  "+progName+" -p <pdb_code> -d <distance_cutoff> -t <contact_type> [-o <output_dir>] [-D <pdbase_db>] [-P] \n" +
				"3)  "+progName+" -f <pdbfile> [-c <chain_pdb_code>] -d <distance_cutoff> -t <contact_type> [-o <output_dir>] [-P] \n\n" +
				"In case 2) also a list of comma separated pdb codes+chain codes can be specified, e.g. -p 1bxyA,1josA \n" +
				"In case 3) if -c not specified then the first chain code in the pdb file will be taken\n" +
				"If pdbase_db not specified, the default pdbase will be used\n" +
				"If output dir not specified default is current\n" +
				"In all cases option -P can be specified to get the graph in paul format instead of aglappe format\n"; 

		String listfile = "";
		String[] pdbIds = null;
		String pdbChainCode4file = null;
		String pdbfile = "";
		String pdbaseDb = PDB_DB;
		String edgeType = "";
		double cutoff = 0.0;
		String outputDir = "."; // we set default to current directory
		boolean paul = false;
		
		Getopt g = new Getopt(progName, args, "i:p:c:f:d:t:o:D:Ph?");
		int c;
		while ((c = g.getopt()) != -1) {
			switch(c){
			case 'i':
				listfile = g.getOptarg();
				break;
			case 'p':
				pdbIds = g.getOptarg().split(",");
				break;
			case 'c':
				pdbChainCode4file = g.getOptarg();
				break;
			case 'f':
				pdbfile = g.getOptarg();
				break;
			case 'd':
				cutoff = Double.valueOf(g.getOptarg());
				break;
			case 't':
				edgeType = g.getOptarg();
				break;
			case 'o':
				outputDir = g.getOptarg();
				break;
			case 'D':
				pdbaseDb = g.getOptarg();
				break;
			case 'P':
				paul = true;
				break;				
			case 'h':
			case '?':
				System.out.println(help);
				System.exit(0);
				break; // getopt() already printed an error
			}
		}

		if (edgeType.equals("") || cutoff==0.0) {
			System.err.println("Some missing option");
			System.err.println(help);
			System.exit(1);
		}
		if (listfile.equals("") && pdbIds==null && pdbfile.equals("")){
			System.err.println("Either a listfile, some pdb codes/chain codes or a pdbfile must be given");
			System.err.println(help);
			System.exit(1);
		}
		if ((!listfile.equals("") && pdbIds!=null) || (!listfile.equals("") && !pdbfile.equals("")) || (pdbIds!=null && !pdbfile.equals(""))) {
			System.err.println("Options -p, -i and -f/-c are exclusive. Use only one of them");
			System.err.println(help);
			System.exit(1);			
		}

		
		MySQLConnection conn = null;		

		try{
			conn = new MySQLConnection(DB_HOST, DB_USER, DB_PWD);
		} catch (Exception e) {
			System.err.println("Error opening database connection. Exiting");
			System.exit(1);
		}
		
		
		if (pdbfile.equals("")){
			
			if (!listfile.equals("")) {		
				pdbIds = TemplateList.readIdsListFile(new File(listfile));
			}

			int numPdbs = 0;

			for (int i=0;i<pdbIds.length;i++) {
				String pdbCode = pdbIds[i].substring(0,4);
				String pdbChainCode = pdbIds[i].substring(4);

				try {
					
					//long start = System.currentTimeMillis();
					
					Pdb pdb = new PdbasePdb(pdbCode, pdbaseDb, conn);
					pdb.load(pdbChainCode);

					// get graph
					RIGraph graph = pdb.get_graph(edgeType, cutoff);
					
					String edgeTypeStr = edgeType.replaceAll("/", ":");
					
					File outputFile = new File(outputDir,pdbCode+pdbChainCode+"_"+edgeTypeStr+"_"+cutoff+".cm");
					if (!paul) {
						graph.write_graph_to_file(outputFile.getAbsolutePath());
					} else {
						graph.writeToPaulFile(outputFile.getAbsolutePath());
					}

					//long end = System.currentTimeMillis();
					//double time = (double) (end -start)/1000;

					System.out.println("Wrote "+outputFile.getAbsolutePath());
					//System.out.printf("%5.3f s\n",time);
					
					numPdbs++;

				} catch (PdbLoadError e) {
					System.err.println("Error loading pdb data for " + pdbCode + pdbChainCode+", specific error: "+e.getMessage());
				} catch (PdbCodeNotFoundError e) {
					System.err.println("Couldn't find pdb code "+pdbCode);
				} catch (SQLException e) {
					System.err.println("SQL error for structure "+pdbCode+pdbChainCode+", error: "+e.getMessage());
				}

			}

			// output results
			System.out.println("Number of structures done successfully: " + numPdbs);


		} else {
			File pdbFile = new File(pdbfile);
			try {
				Pdb pdb = new PdbfilePdb(pdbfile);
				if (pdbChainCode4file==null) {
					pdbChainCode4file = pdb.getChains()[0];
				}

				pdb.load(pdbChainCode4file);
				RIGraph graph = pdb.get_graph(edgeType, cutoff);

				String edgeTypeStr = edgeType.replaceAll("/", ":");
				
				String filename = pdbFile.getName().substring(0, pdbFile.getName().lastIndexOf("."));
				File outputFile = new File(outputDir,filename+"_"+edgeTypeStr+"_"+cutoff+".cm");
				if (!paul) {
					graph.write_graph_to_file(outputFile.getAbsolutePath());
				} else {
					graph.writeToPaulFile(outputFile.getAbsolutePath());
				}
				System.out.println("Wrote graph file "+outputFile.getAbsolutePath()+" from pdb file "+pdbFile);
				
			} catch (PdbLoadError e) {
				System.err.println("Error loading from pdb file "+pdbFile+", specific error: "+e.getMessage());
			}
		}
	}

}
