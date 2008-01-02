package proteinstructure;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;

import javax.vecmath.Point3d;
import javax.vecmath.Point3i;
import javax.vecmath.Vector3d;

import Jama.Matrix;
import Jama.SingularValueDecomposition;

/**
 * A single chain pdb protein structure
 * 
 * @author		Jose Duarte
 * Class:		Pdb
 * Package:		proteinstructure
 */
public abstract class Pdb {
	
	protected final static int DEFAULT_MODEL=1;				// default model serial (NMR structures)
	public final static String NONSTANDARD_AA_LETTER="X";   // letter we assign to nonstandard aas to use in sequence
	
	protected HashMap<String,Integer> resser_atom2atomserial; // residue serial+atom name (separated by underscore) to atom serials
	protected HashMap<Integer,String> resser2restype;   // residue serial to 3 letter residue type 
	protected HashMap<Integer,Point3d> atomser2coord;  // atom serials to 3D coordinates
	protected HashMap<Integer,Integer> atomser2resser;  // atom serials to residue serials
	protected HashMap<Integer,String> atomser2atom;     // atom serials to atom names
	protected HashMap<String,Integer> pdbresser2resser; // pdb (author) residue serials (can include insetion codes so they are strings) to internal residue serials
	protected HashMap<Integer,String> resser2pdbresser; // internal residue serials to pdb (author) residue serials (can include insertion codes so they are strings)
	
	protected HashMap<Integer,String> resser2secstruct;   // residue serials to secondary structure
	protected TreeMap<String,Interval> secstruct2resinterval;// secondary structure element to residue serial intervals
	
	protected HashMap<String,ArrayList<String>> aas2atoms = AA.getaas2atoms(); // contains atom names for each aminoacid
	
	protected String sequence; 		// full sequence as it appears in SEQRES field
	protected String pdbCode;
    // given "external" pdb chain code, i.e. the classic (author's) pdb code ("NULL" if it is blank in original pdb file)	
	protected String pdbChainCode;
    // Our internal chain identifier:
    // - in reading from pdbase or from msdsd it will be set to the internal chain id (asym_id field for pdbase, pchain_id for msdsd)
    // - in reading from pdb file it coincides with pdbChainCode except for "NULL" where we use "A"
	protected String chainCode;
	protected int model;  			// the model serial for NMR structures
	
	protected String db;			// the db from which we have taken the data (if applies)
	
	
	

	/**
	 * Dumps coordinate data into a file in pdb format (ATOM lines only)
	 * The chain dumped is the value of the chainCode variable, i.e. our internal chain identifier for Pdb objects
	 * @param outfile
	 * @throws IOException
	 */
	public void dump2pdbfile(String outfile) throws IOException {
		TreeMap<Integer,Object[]> lines = new TreeMap<Integer,Object[]>();
		PrintStream Out = new PrintStream(new FileOutputStream(outfile));
		Out.println("HEADER  Dumped from "+db+". pdb code="+pdbCode+", chain='"+chainCode+"'");
		for (String resser_atom:resser_atom2atomserial.keySet()){
			int atomserial = resser_atom2atomserial.get(resser_atom);
			int res_serial = Integer.parseInt(resser_atom.split("_")[0]);
			String atom = resser_atom.split("_")[1];
			String res_type = resser2restype.get(res_serial);
			Point3d coords = atomser2coord.get(atomserial);
			Object[] fields = {atomserial, atom, res_type, chainCode, res_serial, coords.x, coords.y, coords.z};
			lines.put(atomserial, fields);
		}
		for (int atomserial:lines.keySet()){
			// Local.US is necessary, otherwise java prints the doubles locale-dependant (i.e. with ',' for some locales)
			Out.printf(Locale.US,"ATOM  %5d  %3s %3s %1s%4d    %8.3f%8.3f%8.3f\n",lines.get(atomserial));
		}
		Out.println("END");
		Out.close();
	}
	
	/**
	 * Dump the full sequence of this Pdb object in fasta file format
	 * @param seqfile
	 * @throws IOException
	 */
	public void dumpseq(String seqfile) throws IOException {
		PrintStream Out = new PrintStream(new FileOutputStream(seqfile));
		Out.println(">"+pdbCode+"_"+pdbChainCode);
		Out.println(sequence);
		Out.close();
	}
	
	public int get_length(){
		return resser2restype.size();
	}
	
	/**
	 * Gets a TreeMap with atom serials as keys and their coordinates as values for the given contact type
	 * The contact type can't be a cross contact type, it doesn't make sense here
	 * @param ct
	 * @return
	 */
	private TreeMap<Integer,Point3d> get_coords_for_ct(String ct) {
		TreeMap<Integer,Point3d> coords = new TreeMap<Integer,Point3d>(); 
		HashMap<String,String[]> restype2atoms = AA.ct2atoms(ct);
		for (int resser:resser2restype.keySet()){
			String[] atoms = restype2atoms.get(resser2restype.get(resser));
			for (String atom:atoms){
				if (resser_atom2atomserial.containsKey(resser+"_"+atom)){
					int atomser = resser_atom2atomserial.get(resser+"_"+atom);
					Point3d coord = atomser2coord.get(atomser);
					coords.put(atomser, coord);
				}
				else if (atom.equals("O") && resser_atom2atomserial.containsKey(resser+"_"+"OXT")){
					int atomser = resser_atom2atomserial.get(resser+"_"+"OXT");
					Point3d coord = atomser2coord.get(atomser);
					coords.put(atomser, coord);
				}
				else {
					System.err.println("Couldn't find "+atom+" atom for resser="+resser+". Continuing without that atom for this resser.");
				}
			}
		}
		return coords;
	}

	/**
	 * Gets a TreeMap with residue serials+"_"+atomname as keys and their coordinates as values for the given contact type
	 * The contact type can't be a cross contact type, it doesn't make sense here
	 * Used in rmsd method
	 * @param ct
	 * @return
	 */
	private TreeMap<String,Point3d> get_coords_for_ct_4rmsd(String ct) {
		TreeMap<String,Point3d> coords = new TreeMap<String,Point3d>(); 
		HashMap<String,String[]> restype2atoms = AA.ct2atoms(ct);
		for (int resser:resser2restype.keySet()){
			String[] atoms = restype2atoms.get(resser2restype.get(resser));
			for (String atom:atoms){
				if (resser_atom2atomserial.containsKey(resser+"_"+atom)){
					int atomser = resser_atom2atomserial.get(resser+"_"+atom);
					Point3d coord = atomser2coord.get(atomser);
					coords.put(resser+"_"+atom, coord);
				}
				else if (atom.equals("O") && resser_atom2atomserial.containsKey(resser+"_"+"OXT")){
					int atomser = resser_atom2atomserial.get(resser+"_"+"OXT");
					Point3d coord = atomser2coord.get(atomser);
					coords.put(resser+"_"+atom, coord);
				} 
			}
		}
		return coords;
	}

	/**
	 * Returns the distance matrix as a HashMap with Contacts (residue serial pairs) as keys
	 * It doesn't make sense to call this method for multi atom contact 
	 * types (for each residue serial pair there's more than 1 distance)
	 * Thus before calling this we should check AA.isValidSingleAtomCT(ct)
	 * @param ct the contact type
	 * @return
	 */
	public HashMap<Edge, Double> calculate_dist_matrix(String ct){
		HashMap<Edge,Double> distMatrixAtoms = new HashMap<Edge,Double>();
		if (!ct.contains("/")){
			TreeMap<Integer,Point3d> coords = get_coords_for_ct(ct);
			for (int i_atomser:coords.keySet()){
				for (int j_atomser:coords.keySet()){
					if (j_atomser>i_atomser) {
						Edge pair = new Edge(i_atomser,j_atomser);
						distMatrixAtoms.put(pair, coords.get(i_atomser).distance(coords.get(j_atomser)));
					}
				}
			}
		} else {
			String i_ct = ct.split("/")[0];
			String j_ct = ct.split("/")[1];
			TreeMap<Integer,Point3d> i_coords = get_coords_for_ct(i_ct);
			TreeMap<Integer,Point3d> j_coords = get_coords_for_ct(j_ct);
			for (int i_atomser:i_coords.keySet()){
				for (int j_atomser:j_coords.keySet()){
					if (j_atomser!=i_atomser){
						Edge pair = new Edge(i_atomser,j_atomser);
						distMatrixAtoms.put(pair, i_coords.get(i_atomser).distance(j_coords.get(j_atomser)));
					}
				}
			}
		}

		HashMap<Edge,Double> distMatrixRes = new HashMap<Edge, Double>();
		for (Edge cont: distMatrixAtoms.keySet()){
			int i_resser = get_resser_from_atomser(cont.i);
			int j_resser = get_resser_from_atomser(cont.j);
			distMatrixRes.put(new Edge(i_resser,j_resser), distMatrixAtoms.get(cont));
		}

		return distMatrixRes;
	}
	
	/**
	 * Get the graph for given contact type and cutoff for this Pdb object.
	 * Returns a Graph object with the contacts
	 * We do geometric hashing for fast contact computation (without needing to calculate full distance matrix)
	 * @param ct
	 * @param cutoff
	 * @return
	 */
	public Graph get_graph(String ct, double cutoff){ 
		TreeMap<Integer,Point3d> i_coords = null;
		TreeMap<Integer,Point3d> j_coords = null;		// only relevant for asymetric edge types
		boolean directed = false;
		if (!ct.contains("/")){
			i_coords = get_coords_for_ct(ct);
			directed = false;
		} else {
			String i_ct = ct.split("/")[0];
			String j_ct = ct.split("/")[1];
			i_coords = get_coords_for_ct(i_ct);
			j_coords = get_coords_for_ct(j_ct);
			directed = true;
		}
		int[] i_atomserials = new  int[i_coords.size()]; // map from matrix indices to atomserials
		int[] j_atomserials = null;
		
		int SCALE=100; // i.e. we use units of hundredths of Amstrongs (thus cutoffs can be specified with a maximum precission of 0.01A)
		
		int boxSize = (int) Math.floor(cutoff*SCALE);
		
		HashMap<Point3i,Box> boxes = new HashMap<Point3i,Box>();
		int i=0;
		for (int i_atomser:i_coords.keySet()){
			//coordinates for atom serial atomser, we will use i as its identifier below
			Point3d coord = i_coords.get(i_atomser);
			int floorX = boxSize*((int)Math.floor(coord.x*SCALE/boxSize));
			int floorY = boxSize*((int)Math.floor(coord.y*SCALE/boxSize));
			int floorZ = boxSize*((int)Math.floor(coord.z*SCALE/boxSize));
			Point3i floor = new Point3i(floorX,floorY,floorZ);
			if (boxes.containsKey(floor)){
				// we put the coords for atom i in its corresponding box (identified by floor)
				boxes.get(floor).put_i_Point(i, coord);
				if (!directed){
					boxes.get(floor).put_j_Point(i, coord);
				}
			} else {
				Box box = new Box(floor);
				box.put_i_Point(i, coord);
				if (!directed){
					box.put_j_Point(i, coord);
				}
				boxes.put(floor,box);
			}
			i_atomserials[i]=i_atomser; //as atomserials in coords were ordered (TreeMap) the new indexing will still be ordered
			i++;
		}
		int j=0;
		if (directed) {
			j_atomserials = new  int[j_coords.size()];
			for (int j_atomser:j_coords.keySet()){
				//coordinates for atom serial atomser, we will use j as its identifier below
				Point3d coord = j_coords.get(j_atomser);
				int floorX = boxSize*((int)Math.floor(coord.x*SCALE/boxSize));
				int floorY = boxSize*((int)Math.floor(coord.y*SCALE/boxSize));
				int floorZ = boxSize*((int)Math.floor(coord.z*SCALE/boxSize));
				Point3i floor = new Point3i(floorX,floorY,floorZ);
				if (boxes.containsKey(floor)){
					// we put the coords for atom j in its corresponding box (identified by floor)
					boxes.get(floor).put_j_Point(j, coord);
				} else {
					Box box = new Box(floor);
					box.put_j_Point(j, coord);
					boxes.put(floor,box);
				}
				j_atomserials[j]=j_atomser; //as atomserials in coords were ordered (TreeMap) the new indexing will still be ordered
				j++;
			}
		} else {
			j_atomserials = i_atomserials;
		}

		
		double[][]distMatrix = new double[i_atomserials.length][j_atomserials.length];
		
		for (Point3i floor:boxes.keySet()){ // for each box
			// distances of points within this box
			boxes.get(floor).getDistancesWithinBox(distMatrix,directed);

			//TODO should iterate only through half of the neighbours here 
			// distances of points from this box to all neighbouring boxes: 26 iterations (26 neighbouring boxes)
			for (int x=floor.x-boxSize;x<=floor.x+boxSize;x+=boxSize){
				for (int y=floor.y-boxSize;y<=floor.y+boxSize;y+=boxSize){
					for (int z=floor.z-boxSize;z<=floor.z+boxSize;z+=boxSize){
						if (!((x==floor.x)&&(y==floor.y)&&(z==floor.z))) { // skip this box
							Point3i neighbor = new Point3i(x,y,z);
							if (boxes.containsKey(neighbor)){
								boxes.get(floor).getDistancesToNeighborBox(boxes.get(neighbor),distMatrix,directed);
							}
						}
					}
				}
			} 
		} 
		
		// getting the contacts (in residue serials) from the atom serials (partial) distance matrix 
		EdgeSet contacts = new EdgeSet();
		for (i=0;i<distMatrix.length;i++){
			for (j=0;j<distMatrix[i].length;j++){
				// the condition distMatrix[i][j]!=0.0 takes care of skipping several things: 
				// - diagonal of the matrix in case of undirected
				// - lower half of matrix in case of undirected
				// - cells for which we didn't calculate a distance because the 2 points were not in same or neighbouring boxes (i.e. too far apart)
				if (distMatrix[i][j]!=0.0 && distMatrix[i][j]<=cutoff){
					int i_resser = atomser2resser.get(i_atomserials[i]);
					int j_resser = atomser2resser.get(j_atomserials[j]);
					Edge resser_pair = new Edge(i_resser,j_resser);
					// for multi-atom models (BB, SC, ALL or BB/SC) we need to make sure that we don't have contacts from residue to itself or that we don't have duplicates				
					if (i_resser!=j_resser){ // duplicates are automatically taken care by the EdgeSet class which is a TreeSet and doesn't allow duplicates 
						contacts.add(resser_pair);
					}
				}

			}
		}

		// creating and returning the graph object
		TreeMap<Integer,String> nodes = new TreeMap<Integer,String>();
		for (int resser:resser2restype.keySet()){
			nodes.put(resser,resser2restype.get(resser));
		}
		Graph graph = new Graph (contacts,nodes,sequence,cutoff,ct,pdbCode,chainCode,pdbChainCode);

		return graph;
	}
	
	public void calcGridDensity(String ct, double cutoff, Map<Integer, Integer> densityCount) { 
		TreeMap<Integer,Point3d> i_coords = null;
		TreeMap<Integer,Point3d> j_coords = null;		// only relevant for asymetric edge types
		boolean directed = false;
		if (!ct.contains("/")){
			i_coords = get_coords_for_ct(ct);
			directed = false;
		} else {
			String i_ct = ct.split("/")[0];
			String j_ct = ct.split("/")[1];
			i_coords = get_coords_for_ct(i_ct);
			j_coords = get_coords_for_ct(j_ct);
			directed = true;
		}
		int[] i_atomserials = new  int[i_coords.size()]; // map from matrix indices to atomserials
		int[] j_atomserials = null;
		
		int SCALE=100; // i.e. we use units of hundredths of Amstrongs (thus cutoffs can be specified with a maximum precission of 0.01A)
		
		int boxSize = (int) Math.floor(cutoff*SCALE);
		
		HashMap<Point3i,Box> boxes = new HashMap<Point3i,Box>();
		int i=0;
		for (int i_atomser:i_coords.keySet()){
			//coordinates for atom serial atomser, we will use i as its identifier below
			Point3d coord = i_coords.get(i_atomser);
			int floorX = boxSize*((int)Math.floor(coord.x*SCALE/boxSize));
			int floorY = boxSize*((int)Math.floor(coord.y*SCALE/boxSize));
			int floorZ = boxSize*((int)Math.floor(coord.z*SCALE/boxSize));
			Point3i floor = new Point3i(floorX,floorY,floorZ);
			if (boxes.containsKey(floor)){
				// we put the coords for atom i in its corresponding box (identified by floor)
				boxes.get(floor).put_i_Point(i, coord);
				if (!directed){
					boxes.get(floor).put_j_Point(i, coord);
				}
			} else {
				Box box = new Box(floor);
				box.put_i_Point(i, coord);
				if (!directed){
					box.put_j_Point(i, coord);
				}
				boxes.put(floor,box);
			}
			i_atomserials[i]=i_atomser; //as atomserials in coords were ordered (TreeMap) the new indexing will still be ordered
			i++;
		}
		int j=0;
		if (directed) {
			j_atomserials = new  int[j_coords.size()];
			for (int j_atomser:j_coords.keySet()){
				//coordinates for atom serial atomser, we will use j as its identifier below
				Point3d coord = j_coords.get(j_atomser);
				int floorX = boxSize*((int)Math.floor(coord.x*SCALE/boxSize));
				int floorY = boxSize*((int)Math.floor(coord.y*SCALE/boxSize));
				int floorZ = boxSize*((int)Math.floor(coord.z*SCALE/boxSize));
				Point3i floor = new Point3i(floorX,floorY,floorZ);
				if (boxes.containsKey(floor)){
					// we put the coords for atom j in its corresponding box (identified by floor)
					boxes.get(floor).put_j_Point(j, coord);
				} else {
					Box box = new Box(floor);
					box.put_j_Point(j, coord);
					boxes.put(floor,box);
				}
				j_atomserials[j]=j_atomser; //as atomserials in coords were ordered (TreeMap) the new indexing will still be ordered
				j++;
			}
		} else {
			j_atomserials = i_atomserials;
		}
		
		for(Point3i floor:boxes.keySet()) {
			int size = boxes.get(floor).size();
			if(densityCount.containsKey(size)) {
				int old = densityCount.get(size);
				densityCount.put(size, ++old);
			} else {
				densityCount.put(size, 1);
			}
		}
	}
	
	
	public int get_resser_from_pdbresser (String pdbresser){
		return pdbresser2resser.get(pdbresser);
	}
	
	public String get_pdbresser_from_resser (int resser){
		return resser2pdbresser.get(resser);
	}

	public int get_resser_from_atomser(int atomser){
		return atomser2resser.get(atomser);
	}
	
	public String getChainCode(){
		return this.chainCode;
	}
	
	public String getPdbChainCode(){
		return this.pdbChainCode;
	}
	
	public String getSecStructure(int resser){
		return this.resser2secstruct.get(resser);
	}
	
	public TreeMap<String,Interval> getAllSecStructElements(){
		return secstruct2resinterval;
	}
	
	/**
	 * Calculates rmsd (on atoms given by ct) of this Pdb object to otherPdb object
	 * Both objects must represent structures with same sequence (save unobserved residues or missing atoms)
	 * 
	 * @param otherPdb
	 * @param ct the contact type (crossed contact types don't make sense here)
	 * @return
	 * @throws ConformationsNotSameSizeError
	 */
	public double rmsd(Pdb otherPdb, String ct) throws ConformationsNotSameSizeError {
		TreeMap<String,Point3d> thiscoords = this.get_coords_for_ct_4rmsd(ct);
		TreeMap<String,Point3d> othercoords = otherPdb.get_coords_for_ct_4rmsd(ct);
		
		// there might be unobserved residues or some missing atoms for a residue
		// here we get the ones that are in common
		ArrayList<String> common = new ArrayList<String>();
		for (String resser_atom:thiscoords.keySet()){
			if (othercoords.containsKey(resser_atom)){
				common.add(resser_atom);
			}
		}
		
		// converting the TreeMaps to Vector3d arrays (input format for calculate_rmsd)
		Vector3d[] conformation1 = new Vector3d[common.size()]; 
		Vector3d[] conformation2 = new Vector3d[common.size()];
		int i = 0;
		for (String resser_atom:common){
			conformation1[i] = new Vector3d(thiscoords.get(resser_atom).x,thiscoords.get(resser_atom).y,thiscoords.get(resser_atom).z);
			conformation2[i] = new Vector3d(othercoords.get(resser_atom).x,othercoords.get(resser_atom).y,othercoords.get(resser_atom).z);
			i++;
		}
				
		// this as well as calculating the rmsd, changes conformation1 and conformation2 to be optimally superposed
		double rmsd = calculate_rmsd(conformation1, conformation2);

//		// printing out individual distances (conformation1 and conformation2 are now optimally superposed)
//		for (i=0;i<conformation1.length;i++){
//			Point3d point1 = new Point3d(conformation1[i].x,conformation1[i].y,conformation1[i].z);
//			Point3d point2 = new Point3d(conformation2[i].x,conformation2[i].y,conformation2[i].z);
//			System.out.println(point1.distance(point2));
//		}
		
		return rmsd;

	}
	
	/**
	 * Calculates the RMSD between two conformations.      
     * conformation1: Vector3d array (matrix of dimensions [N,3])       
     * conformation2: Vector3d array (matrix of dimensions [N,3]) 
     * 
     * Both conformation1 and conformation2 are modified to be optimally superposed
     * 
     * Implementation taken (python) from http://bosco.infogami.com/Root_Mean_Square_Deviation, 
     * then ported to java using Jama matrix package 
     * (page has moved to: http://boscoh.com/protein/rmsd-root-mean-square-deviation)                
	 * @param conformation1
	 * @param conformation2
	 * @return
	 * @throws ConformationsNotSameSizeError
	 */
	private double calculate_rmsd(Vector3d[] conformation1, Vector3d[] conformation2) throws ConformationsNotSameSizeError{
		if (conformation1.length!=conformation2.length) {
			//System.err.println("Conformations not the same size");
			throw new ConformationsNotSameSizeError(
					"Given conformations have different size: conformation1: "+conformation1.length+", conformation2: "+conformation2.length);
		}
		int n_vec = conformation1.length;
		
		// 1st we bring both conformations to the same centre by subtracting their respectives centres
		Vector3d center1 = new Vector3d();
		Vector3d center2 = new Vector3d();
		for (int i=0;i<n_vec;i++){ // summing all vectors in each conformation
			center1.add(conformation1[i]);
			center2.add(conformation2[i]);
		}
		// dividing by n_vec (average)
		center1.scale((double)1/n_vec);
		center2.scale((double)1/n_vec);
		// translating our conformations to the same coordinate system by subtracting centers
		for (Vector3d vec:conformation1){
			vec.sub(center1);
		}
		for (Vector3d vec:conformation2){
			vec.sub(center2);
		}

		//E0: initial sum of squared lengths of both conformations
		double sum1 = 0.0;
		double sum2 = 0.0;
		for (int i=0;i<n_vec;i++){
			sum1 += conformation1[i].lengthSquared();
			sum2 += conformation2[i].lengthSquared();
		}
		double E0 = sum1 + sum2;
		
		// singular value decomposition
		Matrix vecs1 = vector3dAr2matrix(conformation1);
		Matrix vecs2 = vector3dAr2matrix(conformation2);
		
		Matrix correlation_matrix = vecs2.transpose().times(vecs1); //gives a 3x3 matrix

		SingularValueDecomposition svd = correlation_matrix.svd();
		Matrix U = svd.getU();
		Matrix V_trans = svd.getV().transpose(); 
		double[] singularValues = svd.getSingularValues();
		
		boolean is_reflection = false;
		if (U.det()*V_trans.det()<0.0){ 
			is_reflection = true;
		}
		if (is_reflection){
			// reflect along smallest principal axis:
			// we change sign of last coordinate (smallest singular value)
			singularValues[singularValues.length-1]=(-1)*singularValues[singularValues.length-1];  			
		}
		
		// getting sum of singular values
		double sumSV = 0.0;
		for (int i=0;i<singularValues.length;i++){
			sumSV += singularValues[i];
		}
		
		// rmsd square: Kabsch formula
		double rmsd_sq = (E0 - 2.0*sumSV)/((double) n_vec);
		rmsd_sq = Math.max(rmsd_sq, 0.0);

		// finally we modify conformation2 to be aligned to conformation1
		if (is_reflection) { // first we check if we are in is_reflection case: we need to change sign to last row of U
			for (int j=0;j<U.getColumnDimension();j++){
				// we change sign to last row of U
				int lastRow = U.getRowDimension()-1;
				U.set(lastRow, j, (-1)*U.get(lastRow,j));
			}
		}
		Matrix optimal_rotation = U.times(V_trans); 
		Matrix conf2 = vecs2.times(optimal_rotation);
		for (int i=0;i<n_vec;i++){
			conformation2[i].x = conf2.get(i,0);
			conformation2[i].y = conf2.get(i,1);
			conformation2[i].z = conf2.get(i,2);
		}

		return Math.sqrt(rmsd_sq);
	}
	
	/** Gets a Jama.Matrix object from a Vector3d[] (deep copies) */
	private Matrix vector3dAr2matrix(Vector3d[] vecArray) {
		double[][] array = new double[vecArray.length][3];
		for (int i=0;i<vecArray.length;i++){
			vecArray[i].get(array[i]);
		}
		return new Matrix(array);
	}

}
