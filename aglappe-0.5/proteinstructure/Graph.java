package proteinstructure;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.MySQLConnection;


public class Graph {

	public final static String MYSQLSERVER="white";
	public final static String MYSQLUSER=getUserName();
	public final static String MYSQLPWD="nieve";
	
	public final static String GRAPHFILEFORMATVERSION = "1.0";

	ContactList contacts;
	// nodes is a TreeMap of residue serials to residue types (3 letter code)
	TreeMap<Integer,String> nodes;
	public String sequence; // the full sequence (with unobserved residues and non-standard aas ='X')
	public String accode;
	public String chain;
	public String chaincode="";
	public double cutoff;
	public String ct;
	public boolean directed=false;
	
	// fullLength is length of full sequence or:
	// -if sequence not provided (when reading from db): length of everything except possible unobserved residues at end of chain
	// -if sequence and nodes not provided (when reading from file and sequence field missing): length except possible unobserved residues at end of chain and possible nodes without contacts at end of chain
	public int fullLength; 
	public int obsLength;  // length without unobserved, non standard aas 
	
	public int numContacts;
	
	public boolean modified;
	
	// these 2 fields only used when reading from db
	int graphid=0;
	int sm_id=0;
	
	/**
	 * Constructs Graph object by passing ArrayList with contacts and TreeMap with nodes (res serials and types)
	 * Must also pass contact type, cutoff, accession code and chain
	 * @param contacts
	 * @param nodes
	 * @param sequence
	 * @param cutoff
	 * @param ct
	 * @param accode
	 * @param chain
	 */
	public Graph (ContactList contacts, TreeMap<Integer,String> nodes, String sequence, double cutoff,String ct, String accode, String chain, String chaincode) {
		this.contacts=contacts;
		this.cutoff=cutoff;
		this.nodes=nodes;
		this.sequence=sequence;
		this.accode=accode;
		this.chain=chain;
		this.chaincode=chaincode;
		this.ct=ct;
		this.fullLength=sequence.length();
		this.obsLength=nodes.size();
		this.numContacts=contacts.size();
		this.modified=false;
		if (ct.contains("/")){
			directed=true;
		}
	}
	
	/**
	 * Constructs Graph object from graph db, given the dbname, accode, chaincode (classic pdb chain code), ct and cutoff
	 * @param dbname
	 * @param accode
	 * @param chaincode
	 * @param cutoff
	 * @param ct
	 */
	public Graph(String dbname, String accode, String chaincode, double cutoff, String ct) throws GraphIdNotFoundError{
		this.cutoff=cutoff;
		this.accode=accode;
		this.ct=ct;
		// we set the sequence to empty when we read from graph db. We don't have the full sequence in graph db
		// when we pass the sequence in getCM to the ContactMap constructor we want to have either a full sequence (with unobserveds) or a blank in case we don't have the info
		this.sequence=""; 
		//TODO graphs in db are never directed, so this doesn't really apply here. Must solve all this!
		if (ct.contains("/")){
			directed=true;
		}
		MySQLConnection conn = new MySQLConnection(MYSQLSERVER,MYSQLUSER,MYSQLPWD,dbname);
		getgraphid(conn, chaincode); // initialises graphid, sm_id and chain
		read_graph_from_db(conn); // gets contacts, nodes and sequence
		conn.close();
		this.obsLength=nodes.size();
		if (!sequence.equals("")){
			this.fullLength=sequence.length();
		} else {
			// if nodes TreeMap has correct residue numbering then this should get the right full length,
			// we will only miss: gaps (unobserved residues) at the end of the sequence. Those we can't know unless full sequence is given
			this.fullLength=Collections.max(nodes.keySet());
		}
		this.numContacts=contacts.size();
		this.modified=false;
	}
	
	/**
	 * Constructs Graph object from graph db, given the graphid
	 * @param dbname
	 * @param graphid
	 */
	public Graph(String dbname,int graphid) throws GraphIdNotFoundError{
		this.graphid=graphid;
		// we set the sequence to empty when we read from graph db. We don't have the full sequence in graph db
		// when we pass the sequence in getCM to the ContactMap constructor we want to have either a full sequence (with unobserveds) or a blank in case we don't have the info
		this.sequence="";
		MySQLConnection conn = new MySQLConnection(MYSQLSERVER,MYSQLUSER,MYSQLPWD,dbname);
		read_graph_from_db(conn); // gets contacts, nodes and sequence
		get_db_graph_info(conn); // gets accode, chaincode, chain, ct and cutoff from db (from graph_id)
		conn.close();
		//TODO graphs in db are never directed, so this doesn't really apply here. Must solve all this!
		if (ct.contains("/")){
			directed=true;
		}
		this.obsLength=nodes.size();
		if (!sequence.equals("")){
			this.fullLength=sequence.length();
		} else {
			// if nodes TreeMap has correct residue numbering then this should get the right full length,
			// we will only miss: gaps (unobserved residues) at the end of the sequence. Those we can't know unless full sequence is given
			this.fullLength=Collections.max(nodes.keySet());
		}
		this.numContacts=contacts.size();
		this.modified=false;
	}

	/**
	 * Constructs Graph object by reading a file with contacts
	 * If the contacts file doesn't have the sequence then the graph object won't have sequence or nodes
	 * That means it won't be possible to get a ContactMap from it using getCM because CM needs both sequence and nodes
	 * @param contactsfile
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public Graph (String contactsfile) throws IOException, FileNotFoundException{
		// we set the sequence to blank when we read from file as we don't have the full sequence
		// if sequence is present in contactsfile then is read from there
		this.sequence="";
		this.ct="";
		this.cutoff=0.0;
		// we initialise accode, chain and chaincode to empty strings in case the file doesn't specify then
		this.accode="";
		this.chain="";
		this.chaincode="";
		if (ct.contains("/")){
			directed=true;
		}
		read_graph_from_file(contactsfile); // initialises contacts, and nodes (only if sequence is given)
		if (!sequence.equals("")){
			this.fullLength=sequence.length();
			this.obsLength=nodes.size(); 
		} else { 
			// if contacts have correct residue numbering then this should get the right full length up to the maximum node that makes a contact,
			// we will miss: nodes without contacts at the end of sequence and gaps (unobserved residues) at the end of the sequence.
			// We don't know more without nodes and sequence
			this.fullLength=contacts.getMaxNode();
			// in this case nodes has not been initialised so we set obsLength=fullLength as we don't have the information
			this.obsLength=fullLength;  
		}
		this.numContacts=contacts.size();
		this.modified=false;
	}

	//TODO implement (from python) write_graph_to_db, do we really need it here??

	/** get user name from operating system (for use as database username) */
	private static String getUserName() {
		String user = null;
		user = System.getProperty("user.name");
		if(user == null) {
			System.err.println("Could not get user name from operating system. Exiting");
			System.exit(1);
		}
		return user;
	}
	
	public void read_graph_from_file (String contactsfile) throws FileNotFoundException, IOException {
		contacts = new ContactList();
		System.out.println("Reading contacts from file "+contactsfile);
		BufferedReader fcont = new BufferedReader(new FileReader(new File(contactsfile)));
		String line;
		while ((line = fcont.readLine() ) != null ) {
			Pattern p = Pattern.compile("^#");
			Matcher m = p.matcher(line);
			if (m.find()){
//				Pattern ps = Pattern.compile("^#VER: (\\d\\.\\d)");
//				Matcher ms = ps.matcher(line);
//				if (ms.find()){
//					if (!ms.group(1).equals(GRAPHFILEFORMATVERSION)){
//						throw new GraphFileFormatError("The graph file "+contactsfile+" can't be read, wrong file format version");
//					}
//				}
				Pattern ps = Pattern.compile("^#SEQUENCE:\\s*(\\w+)$");
				Matcher ms = ps.matcher(line);
				if (ms.find()){
					sequence=ms.group(1);
				}
				ps = Pattern.compile("^#PDB:\\s*(\\w+)");
				ms = ps.matcher(line);
				if (ms.find()){
					accode=ms.group(1);
				}
				ps = Pattern.compile("^#PDB CHAIN CODE:\\s*(\\w)");
				ms = ps.matcher(line);
				if (ms.find()){
					chaincode=ms.group(1);
				}
				ps = Pattern.compile("^#CHAIN:\\s*(\\w)");
				ms = ps.matcher(line);
				if (ms.find()){
					chain=ms.group(1);
				}				
				ps = Pattern.compile("^#CT:\\s*([a-zA-Z/]+)");
				ms = ps.matcher(line);
				if (ms.find()){
					ct=ms.group(1);
				}												
				ps = Pattern.compile("^#CUTOFF:\\s*(\\d+\\.\\d+)");
				ms = ps.matcher(line);
				if (ms.find()){
					cutoff=Double.parseDouble(ms.group(1));
				}								
			}
			else{			
				int i = Integer.parseInt(line.split("\\s+")[0]);
				int j = Integer.parseInt(line.split("\\s+")[1]);
				contacts.add(new Contact(i,j));
			}
		}
		fcont.close();
		// if sequence was given we take nodes from it
		nodes = new TreeMap<Integer, String>();
		for (int i=0;i<sequence.length();i++){
			String letter = String.valueOf(sequence.charAt(i));
			nodes.put(i+1, AA.oneletter2threeletter(letter));
		}		

	}
	
	/**
	 * Reads contacts and nodes from db.
	 * The db must be a graph db following our standard format, i.e. must have tables: 
	 * chain_graph, single_model_graph, single_model_node, single_model_edge
	 * We don't care here about the origin of the data (msdsd, pdbase, predicted) for the generation of the graph as long as it follows our data format
	 * We read both edges and nodes from single_model_edge and single_model_node.
	 * The sequence is set to blank, as we can't get the full sequence from graph db
	 * @param conn
	 */
	public void read_graph_from_db(MySQLConnection conn){
		contacts = new ContactList();
		nodes = new TreeMap<Integer, String>();
		try {
			// we read only half of the matrix (contacts in one direction only) so that we have the same type of contacts as when creating Graph from Pdb object
			String sql="SELECT i_num,j_num FROM single_model_edge WHERE graph_id="+graphid+" AND j_num>i_num ORDER BY i_num,j_num ";
			Statement stmt = conn.createStatement();
			ResultSet rsst = stmt.executeQuery(sql);
			while (rsst.next()) {
				int i=rsst.getInt(1);
				int j=rsst.getInt(2);
				contacts.add(new Contact(i,j));
			}
			rsst.close();
			stmt.close();
			sql="SELECT num,res FROM single_model_node WHERE graph_id="+graphid+" ORDER BY num ";
			stmt = conn.createStatement();
			rsst = stmt.executeQuery(sql);
			while (rsst.next()){
				int num=rsst.getInt(1);
				String res=rsst.getString(2);
				nodes.put(num, AA.oneletter2threeletter(res));
			}
			rsst.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}
	
	public void getgraphid (MySQLConnection conn, String chaincode) throws GraphIdNotFoundError{
		// input is chaincode i.e. pdb chain code
        // we take chain (internal chain identifier, pchain_code for msdsd and asym_id for pdbase) from pchain_code field in chain_graph 
        // (in the chain_graph table the internal chain identifier is called 'pchain_code')
		int pgraphid=0;
		String chainstr="='"+chaincode+"' ";
		if (chaincode.equals("NULL")){
			chainstr=" IS NULL ";
		}
		try {
			String sql="SELECT graph_id, pchain_code FROM chain_graph WHERE accession_code='"+accode+"' AND chain_pdb_code"+chainstr+" AND dist="+cutoff;
			Statement stmt = conn.createStatement();
			ResultSet rsst = stmt.executeQuery(sql);
			int check=0;
			while (rsst.next()) {
				check++;
				pgraphid=rsst.getInt(1);
				chain=rsst.getString(2);
			}
			if (check!=1){
				System.err.println("No pgraph_id match or more than 1 match for accession_code="+accode+", chain_pdb_code="+chaincode+", dist="+cutoff);
			}
			rsst.close();
			stmt.close();
			// we set the ctstr to the same as ct except in ALL case, where it is BB+SC+BB/SC
			String ctstr=ct;
			if (ct.equals("ALL")){
				ctstr="BB+SC+BB/SC";
			}
			sql="SELECT graph_id,single_model_id FROM single_model_graph WHERE pgraph_id="+pgraphid+" AND CT='"+ctstr+"' AND dist="+cutoff+" AND CR='(true)' AND CW=1";
			stmt = conn.createStatement();
			rsst = stmt.executeQuery(sql);
			check=0;
			while (rsst.next()){
				check++;
				graphid=rsst.getInt(1);
				sm_id=rsst.getInt(2);
			}
			if (check!=1){
				System.err.println("No graph_id match or more than 1 match for pgraph_id="+pgraphid+", CT="+ctstr+" and cutoff="+cutoff);
				throw new GraphIdNotFoundError("No graph_id match or more than 1 match for pgraph_id="+pgraphid+", CT="+ctstr+" and cutoff="+cutoff);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
	}
	
	public void get_db_graph_info(MySQLConnection conn) throws GraphIdNotFoundError {
		try {
			int pgraphid=0;
			String sql="SELECT pgraph_id,CT,dist FROM single_model_graph WHERE graph_id="+graphid;
			Statement stmt = conn.createStatement();
			ResultSet rsst = stmt.executeQuery(sql);
			int check=0;
			while (rsst.next()) {
				check++;
				pgraphid=rsst.getInt(1);
				ct=rsst.getString(2);
				if (ct.equals("BB+SC+BB/SC")) ct="ALL";
				cutoff=rsst.getDouble(3);
			}
			if (check!=1){
				System.err.println("No pgraph_id match or more than 1 match for graph_id="+graphid);
				throw new GraphIdNotFoundError("No pgraph_id match or more than 1 match for graph_id="+graphid+" in db"+conn.getDbname());
			}
			rsst.close();
			stmt.close();
			sql="SELECT accession_code, chain_pdb_code, pchain_code FROM chain_graph WHERE graph_id="+pgraphid;
			stmt = conn.createStatement();
			rsst = stmt.executeQuery(sql);
			check=0;
			while (rsst.next()){
				check++;
				accode=rsst.getString(1);
				chaincode=rsst.getString(2);
				// java returns a null if the field is a database null, we want actually the "NULL" string in that case
				if (chaincode==null) chaincode="NULL";
				chain=rsst.getString(3);
			}
			if (check!=1){
				System.err.println("No accession_code+chain_pdb_code+pchain_code match or more than 1 match for graph_id="+pgraphid+" in chain_graph table");
			}
			rsst.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}
	
	public void write_contacts_to_file (String outfile) throws IOException {
		PrintStream Out = new PrintStream(new FileOutputStream(outfile));
		for (Contact pair:contacts){
			int i_resser=pair.i;
			int j_resser=pair.j;
			Out.println(i_resser+"\t"+j_resser);
		}
		Out.close();		
	}

	public void write_graph_to_file (String outfile) throws IOException {
		PrintStream Out = new PrintStream(new FileOutputStream(outfile));
		Out.println("#VER: "+GRAPHFILEFORMATVERSION);
		Out.println("#SEQUENCE: "+sequence);
		Out.println("#PDB: "+accode);
		Out.println("#PDB CHAIN CODE: "+chaincode);
		Out.println("#CHAIN: "+chain);
		Out.println("#CT: "+ct);
		Out.println("#CUTOFF: "+cutoff);
		for (Contact pair:contacts){
			int i_resser=pair.i;
			int j_resser=pair.j;
			Out.println(i_resser+"\t"+j_resser);
		}
		Out.close();		
	}
	
	/**
	 * Gets list of contacts as a new ContactList (deep copied)
	 * 
	 */
	public ContactList getContacts(){
		ContactList newContacts = new ContactList();
		for (Contact cont:contacts){
			newContacts.add(new Contact(cont.i,cont.j));
		}
		return newContacts;
	}
	
	/**
	 * Gets TreeMap of nodes, deep copying  
	 * 
	 */
	public TreeMap<Integer,String> getNodes(){
		TreeMap<Integer,String> newNodes = new TreeMap<Integer,String>();
		for (int resser:nodes.keySet()){
			newNodes.put(resser, nodes.get(resser));
		}
		return newNodes;
	}
	
	/**
	 * Deep copies this Graph object returning new one
	 * @return
	 */
	public Graph copy(){
		return new Graph(getContacts(),getNodes(),sequence,cutoff,ct,accode,chain,chaincode);		
	}
	
	/**
	 * Returns an int matrix with 1s for contacts and 0s for non contacts, i.e. the contact map
	 * In non-crossed cases this should give us the upper half matrix (contacts are only j>i)
	 * In crossed cases this gives us a full matrix (contacts are both j>i and i>j since they are directed)
	 * @return
	 */
	public int[][] getIntMatrix(){
		// this initialises the matrix to 0 (i.e. no contact)
		int[][] cm = new int[fullLength][fullLength];
		// we put a 1 for all given contacts
		for (Contact cont:contacts){
			int i_resser = cont.i;
			int j_resser = cont.j;
			cm[i_resser-1][j_resser-1]=1;
		}
		return cm;
	}

	/**
	 * Gets a node's residue type given the residue serial
	 * @param resser
	 * @return
	 */
	public String getResType(int resser){
		return nodes.get(resser);
	}
	
	/**
	 * Gets node neighbourhood given a residue serial
	 * @param resser
	 * @return
	 */
	public NodeNbh getNodeNbh(int resser){
		NodeNbh nbh = new NodeNbh(resser, getResType(resser));
		//this could be implemented using the contact map matrix and scanning through 1 column/row
		//it would be just slightly faster, here we do 2*numContacts iterations, using matrix would be only fullLength iterations
		//however we would then have the overhead of creating the matrix
		for (Contact cont:contacts){
			if (cont.i==resser) nbh.put(cont.j, nodes.get(cont.j));
			if (cont.j==resser) nbh.put(cont.i, nodes.get(cont.i));
		}
		return nbh;
	}
	
	/**
	 * Gets edge neighbourhood (common neighbourhood) given a residue serial pair
	 * @param i_resser
	 * @param j_resser
	 * @return
	 */
	public EdgeNbh getEdgeNbh(int i_resser, int j_resser){
		EdgeNbh nbh = new EdgeNbh(i_resser, getResType(i_resser), j_resser, getResType(j_resser));
		NodeNbh i_nbhd = getNodeNbh(i_resser);
		NodeNbh j_nbhd = getNodeNbh(j_resser);
		if (j_nbhd.size()>=i_nbhd.size()) { //with this we will be slightly faster, always iterating through smallest TreeMap
			for (int resser:i_nbhd.keySet()) {
				if (j_nbhd.containsKey(resser)) nbh.put(resser, i_nbhd.get(resser));
			}
		} else {
			for (int resser:j_nbhd.keySet()) {
				if (i_nbhd.containsKey(resser)) nbh.put(resser, j_nbhd.get(resser));			
			}
		}
		return nbh;
	}

	public void addEdge(Contact cont){
		contacts.add(cont);
		numContacts++;
		modified=true;
	}
	
	public void delEdge(Contact cont){
		contacts.remove(cont);
		numContacts--;
		modified=true;
	}
	
	public void restrictContactsToMaxRange(int range){
		ContactList edgesToDelete = new ContactList();
		for (Contact cont:contacts){
			if (cont.getRange()>range) edgesToDelete.add(cont);
		}
		for (Contact cont:edgesToDelete){
			delEdge(cont);
		}
	}
	
	public void restrictContactsToMinRange(int range){
		ContactList edgesToDelete = new ContactList();
		for (Contact cont:contacts){
			if (cont.getRange()<range) edgesToDelete.add(cont);
		}
		for (Contact cont:edgesToDelete){
			delEdge(cont);
		}
	}
	
}
