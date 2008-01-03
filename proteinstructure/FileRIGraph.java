package proteinstructure;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.graph.util.Pair;

/**
 * A RIGraph derived from a single chain pdb protein structure loaded from a graph file in aglappe's format
 * 
 */
public class FileRIGraph extends RIGraph {
	
	private static final long serialVersionUID = 1L;

	private static double DEFAULT_WEIGHT = 1.0;
	
	/**
	 * Constructs RIGraph object by reading a file with contacts
	 * If the contacts file doesn't have the sequence then the RIGraph object won't have sequence or residue types in RIGNodes
	 * @param contactsfile
	 * @throws IOException
	 * @throws GraphFileFormatError
	 */
	public FileRIGraph (String contactsfile) throws IOException, GraphFileFormatError{
		super();
		// we set the sequence to blank when we read from file as we don't have the full sequence
		// if sequence is present in contactsfile then is read from there
		this.sequence="";
		this.contactType=ProtStructGraph.NO_CONTACT_TYPE;
		this.distCutoff=ProtStructGraph.NO_CUTOFF;
		// we initialise pdbCode, chainCode and pdbChainCode to corresponding constants (empty strings at the moment) in case the file doesn't specify then
		this.pdbCode=Pdb.NO_PDB_CODE;
		this.chainCode=Pdb.NO_CHAIN_CODE;
		this.pdbChainCode=Pdb.NO_PDB_CHAIN_CODE;
		
		read_graph_from_file(contactsfile);  
		
	}

	private void read_graph_from_file (String contactsfile) throws FileNotFoundException, IOException, GraphFileFormatError {
		HashMap<Pair<Integer>,Double> contacts2weights = new HashMap<Pair<Integer>,Double>();
		HashSet<Integer> allserials = new HashSet<Integer>();
		BufferedReader fcont = new BufferedReader(new FileReader(new File(contactsfile)));
		int linecount=0;
		String line;
		while ((line = fcont.readLine() ) != null ) {
			linecount++;
			Pattern p = Pattern.compile("^#AGLAPPE.*ver: (\\d\\.\\d)");
			Matcher m = p.matcher(line);
			if (m.find()){
				if (!m.group(1).equals(GRAPHFILEFORMATVERSION)){
					throw new GraphFileFormatError("The graph file "+contactsfile+" can't be read, wrong file format version. Supported version is "+GRAPHFILEFORMATVERSION+" and found version was "+m.group(1));
				}
			} else if (linecount==1){ // #AGLAPPE not found and in first line
				throw new GraphFileFormatError("The graph file "+contactsfile+" can't be read, wrong file format");
			}
			Pattern ps = Pattern.compile("^#SEQUENCE:\\s*(\\w+)$");
			Matcher ms = ps.matcher(line);
			if (ms.find()){
				sequence=ms.group(1);
			}
			ps = Pattern.compile("^#PDB:\\s*(\\w+)");
			ms = ps.matcher(line);
			if (ms.find()){
				pdbCode=ms.group(1);
			}
			ps = Pattern.compile("^#PDB CHAIN CODE:\\s*(\\w+)");
			ms = ps.matcher(line);
			if (ms.find()){
				pdbChainCode=ms.group(1);
			}
			ps = Pattern.compile("^#CHAIN:\\s*(\\w)");
			ms = ps.matcher(line);
			if (ms.find()){
				chainCode=ms.group(1);
			}				
			ps = Pattern.compile("^#CT:\\s*([a-zA-Z/]+)");
			ms = ps.matcher(line);
			if (ms.find()){
				contactType=ms.group(1);
			}												
			ps = Pattern.compile("^#CUTOFF:\\s*(\\d+\\.\\d+)");
			ms = ps.matcher(line);
			if (ms.find()){
				distCutoff=Double.parseDouble(ms.group(1));
			}								

			Pattern pcontact = Pattern.compile("^\\s*(\\d+)\\s+(\\d+)(?:\\s+(\\d+\\.\\d+))?\\s*$");
			Matcher mcontact = pcontact.matcher(line);
			if (mcontact.find()){
				int i = Integer.valueOf(mcontact.group(1));
				int j = Integer.valueOf(mcontact.group(2));
				allserials.add(i);
				allserials.add(j);
				double weight = DEFAULT_WEIGHT;
				if (mcontact.group(3)!=null) {
					weight = Double.valueOf(mcontact.group(3));
				}
				contacts2weights.put(new Pair<Integer>(i,j),weight);
			}

		}
		fcont.close();

		// populating this RIGraph with nodes and setting sequence and fullLength
		serials2nodes = new TreeMap<Integer,RIGNode>();
		if (!sequence.equals("")) {
			this.fullLength = sequence.length();
			for (int i=0;i<sequence.length();i++){
				String letter = String.valueOf(sequence.charAt(i));
				RIGNode node = new RIGNode(i+1,AAinfo.oneletter2threeletter(letter));
				serials2nodes.put(i+1, node);
				this.addVertex(node);
			}
		} else {
			// if contacts have correct residue numbering then this should get the right full length up to the maximum node that makes a contact,
			// we will miss: nodes without contacts at the end of sequence and gaps (unobserved residues) at the end of the sequence.
			// We don't know more without sequence
			this.fullLength = Collections.max(allserials);
			for (int resser:allserials) {
				RIGNode node = new RIGNode(resser);
				serials2nodes.put(resser,node);
				this.addVertex(node);
			}
		}

		//TODO we still use here DIRECTED as default for "/", eventually this should change by taking another parameter "boolean directed", so "/" could have DIRECTED/UNDIRECTED versions
		EdgeType et = EdgeType.UNDIRECTED;
		if (contactType.contains("/")){
			et = EdgeType.DIRECTED;
		}
		// populating this RIGraph with  RIGEdges
		for (Pair<Integer> resPair:contacts2weights.keySet()){
			//TODO we are reading the 3rd column as weights (not as atom weights), we might need to change this or read a 4th column or something else
			this.addEdge(new RIGEdge(contacts2weights.get(resPair)), serials2nodes.get(resPair.getFirst()), serials2nodes.get(resPair.getSecond()), et);
		}
		
		
	}

}
