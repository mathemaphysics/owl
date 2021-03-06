package owl.core.runners;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.vecmath.Matrix3d;

import owl.core.structure.PdbChain;
import owl.core.structure.PdbAsymUnit;
import owl.core.structure.PdbLoadException;
import owl.core.util.FileFormatException;

/**
 * Wrapper class to call the external tool Polypose from the CCP4 package.
 * Polypose does a minimum RMSD fit of multiple structures.
 * 
 * Because of the way, CCP4 is structured, a setup script needs to be called
 * before using any of the CCP4 tools. To work around this, this class writes
 * a temporary shell script file, which calls the setup script and then the
 * Polypose executable. This means that the implementation is highly platform
 * dependent, requires /bin/sh to exist and ccp4_dir/include/ccp4.setup
 * to be a properly configured setup file for a bash environment.
 * 
 * @author stehr
 */
public class PolyposeRunner {

	/*------------------------------ constants ------------------------------*/
	private static final String CCP4_SETUP_SCRIPT_NAME = "/include/ccp4.setup";	// appended to ccp4_dir
	//private static final String POLYPOSE_EXECUTABLE    = "/bin/polypose15";       // appended to ccp4_dir
	private static final String POLYPOSE_EXECUTABLE    = "/bin/polypose50";       // appended to ccp4_dir
	private static final int    POLYPOSE_MAXCYCLE      = 10;
	
	// this will cause multiple jobs on the same machine to conflict but we
	// found that it is much faster than the proper way of doing temp files
	// which is commented out below (and throughout the code)
	// private static final String TMP_DIR = System.getProperty("java.io.tmpdir");
	private static final String TMP_DIR = "/dev/shm/";
	private static final String POLYPOSE_LOG_FILE = 	"polypose.log";
	private static final String TMP_SCRIPT_FILE   = 	"polypose.sh";
	private static final String TMP_PARAM_FILE    = 	"polypose.params";
	private static final String TMP_PDB_FILE      = 	"polypose.%s.pdb";	
	
	//private static final String TMP_FILE_PREFIX =			"polypose";
	//private static final String POLYPOSE_LOG_FILE_SUFFIX = 	".log";
	//private static final String TMP_SCRIPT_FILE_SUFFIX   = 	".sh";
	//private static final String TMP_PARAM_FILE_SUFFIX    = 	".params";
	//private static final String TMP_PDB_FILE_SUFFIX      = 	".pdb";
	
	/*--------------------------- member variables --------------------------*/
	
	// setup
	private File ccp4Dir;
	private File tmpScriptFile;
	private File tmpParamFile;
	private File ccp4SetupScript;
	private File polyposeExecutable;
	private File polyposeLog;
	private File shell;
	
	// results
	private Matrix3d[] rotationMatrices = null;	// stores the rotation matrices after a PolyposeRunner run
	
	/*----------------------------- constructors ----------------------------*/
	public PolyposeRunner(String ccp4Path, String shellPath) throws IOException {
		this.shell = new File(shellPath);
		this.ccp4Dir = new File(ccp4Path);
		
		this.tmpScriptFile = new File(TMP_DIR, TMP_SCRIPT_FILE);
		this.tmpParamFile =  new File(TMP_DIR, TMP_PARAM_FILE);
		this.polyposeLog = new File(TMP_DIR, POLYPOSE_LOG_FILE);
		
//		this.tmpScriptFile = File.createTempFile(TMP_FILE_PREFIX, TMP_SCRIPT_FILE_SUFFIX);
//		this.tmpParamFile = File.createTempFile(TMP_FILE_PREFIX, TMP_PARAM_FILE_SUFFIX);
//		this.polyposeLog = File.createTempFile(TMP_FILE_PREFIX, POLYPOSE_LOG_FILE_SUFFIX);
		
//		// mark temp files to be deleted on exit
//		this.tmpScriptFile.deleteOnExit();
//		this.tmpParamFile.deleteOnExit();
//		this.polyposeLog.deleteOnExit();
		
		// make sure that polypose executable, setup script and /bin/sh exist
		this.ccp4SetupScript = new File(this.ccp4Dir, CCP4_SETUP_SCRIPT_NAME);
		this.polyposeExecutable = new File(this.ccp4Dir, POLYPOSE_EXECUTABLE);
		
		if(!shell.canRead()) {	// better: canExecute() but incompatible with Java 5
			throw new IOException("Could not find shell interpreter " + shell.getAbsolutePath());
		}
		
		if(!polyposeExecutable.canRead()) {
			throw new IOException("Could not find polypose executable " + polyposeExecutable.getAbsolutePath());
		}
		
		if(!ccp4SetupScript.canRead()) { // better: canExecute() but incompatible with Java 5
			throw new IOException("Could not find ccp4 setup script " + ccp4SetupScript.getAbsolutePath());
		}
		
		// create temp script
		writeScriptFile();
	}
	
	// destructor
	protected void finalize() {
		this.tmpScriptFile.delete();
		this.tmpParamFile.delete();
		this.polyposeLog.delete();
	}
	
	/*---------------------------- private methods --------------------------*/
	
	/**
	 * Writes the temporary shell script which calls the ccp4 setup script and
	 * then the polypose executable. This script should hopefully work both
	 * in bash and tcsh environments.
	 */
	private void writeScriptFile() throws IOException {
		PrintWriter out = new PrintWriter(this.tmpScriptFile);
		out.println("#!" + shell.getAbsolutePath());
		out.println("# this script was automatically generated by " + this.getClass().getCanonicalName());
		out.println("source " + this.ccp4SetupScript.getAbsolutePath());
		out.println(this.polyposeExecutable + " $@ > " + this.polyposeLog.getAbsolutePath() + " < " + this.tmpParamFile);
		out.close();
		Runtime.getRuntime().exec("chmod u+rx " + this.tmpScriptFile.getAbsolutePath());
	}
	
	/**
	 * Writes the parameter file which is passed to polypose.
	 * @param positions the positions to be aligned or null (=all positions)
	 * @throws IOException
	 */
	private void writeParamFile(int[][] positions) throws IOException {
		PrintWriter out = new PrintWriter(this.tmpParamFile);
		out.println("maxcycle " + POLYPOSE_MAXCYCLE);
		out.println("input ca");		// use only C-alpha positions
		//out.println("indep");			// calculate R0 (currently not used)
		out.println("output matrix"); 	// matrix=matrix only, no further output, coords=write pdb
		//out.println("fix 1");			// fit others to structure 1
		out.close();
	}
	
	
	/**
	 * Executes polypose, parses the output file and initializes the member variable rotationMatrices
	 * @param filenames
	 * @param paramFileName
	 * @return the rmsd of the superposition or a negative value on error
	 * @throws IOException
	 */
	private double executePolypose(String[] filenames, String paramFileName) throws IOException {
		double rmsd = -3;
		String cmdLine, line;
		
		// build command line
		cmdLine = this.tmpScriptFile.getAbsolutePath();
		int filenum = 1;
		for(String filename:filenames) {
			cmdLine += " XYZIN" + filenum + " " + filename;
			filenum++;
		}
		//System.out.println(cmdLine);
		
		// run polypose
		Process p = Runtime.getRuntime().exec(cmdLine);
		Matrix3d[] matrices = new Matrix3d[filenames.length];	// rotation matrices
		Matrix3d matrix = new Matrix3d();
		int rotNum = 0;
		try {
			p.waitFor();
			BufferedReader in = new BufferedReader(new FileReader(this.polyposeLog));
			Pattern r = Pattern.compile("Rms distance between structures .R1, EQN 42. =(.*),");
			Pattern rotNumPat = Pattern.compile("rotation vector:\\s+(\\d+)");
			Pattern rotMatPat = Pattern.compile("Rotation matrix");
			int readMatrix = 0;
			while((line = in.readLine()) != null) {
				//System.out.println(line);
				if(readMatrix > 0) {
					// we are in matrix read mode
					if(readMatrix <= 3) {
						// read
						String[] vals = line.trim().split("\\s+");
						double val;
						val = Double.parseDouble(vals[0]);
						matrix.setElement(readMatrix-1, 0, val);
						val = Double.parseDouble(vals[1]);
						matrix.setElement(readMatrix-1, 1, val);
						val = Double.parseDouble(vals[2]);
						matrix.setElement(readMatrix-1, 2, val);
						readMatrix++;
					} else {
						// all three lines of current matrix have been read
						//System.out.println(rotNum);
						//System.out.println(matrix);
						matrices[rotNum-1] = new Matrix3d(matrix); // keep current matrix
						readMatrix = 0; // switch off matrix read mode
						matrix = new Matrix3d(); // reset current matrix
					}
				}				
				Matcher m = r.matcher(line);
				if(m.find()) {
					//System.out.println(line);
					rmsd = Double.parseDouble(m.group(1).trim());
				}
				
				m = rotNumPat.matcher(line);
				if(m.find()) {
					rotNum = Integer.parseInt(m.group(1).trim());
				}
				
				m = rotMatPat.matcher(line);
				if(m.find()) {
					readMatrix = 1;	// switch on matrix reading
				}
			}
			in.close();
		} catch (InterruptedException e) {
			throw new IOException("Polypose process was interrupted.");
		}
		if(rmsd < 0) {
			throw new IOException("Could not find RMSD value in Polypose output file.");
		}
		if(rotNum != matrices.length) {
			throw new IOException("Expected " + matrices.length + " rotation matrices but found " + rotNum + " in " + this.polyposeLog.getAbsolutePath());
		}
		this.rotationMatrices = matrices;
		return rmsd;

	}
	
	/**
	 * Rewrites the given temporary pdb files such that all residues except for the ones given are deleted.
	 * The remaining residues are renumbered from 1 to n. The resulting files can then be superimposed with
	 * polypose.
	 * @param filenames
	 * @param positions
	 * @throws IOException 
	 */
	private void extractPositions(ArrayList<String> filenames, int[][] positions) throws IOException {
		if(positions == null || positions.length == 0) {
			return;
		}
		
		File tmpPdbFile = new File(TMP_DIR, String.format(TMP_PDB_FILE, "tmp"));
		//File tmpPdbFile = File.createTempFile(TMP_FILE_PREFIX, TMP_PDB_FILE_SUFFIX);
		//tmpPdbFile.deleteOnExit();
		
		int l = filenames.size();
		if(positions.length != l) {
			throw new IOException("Size of position vector does not match number of files");
		}
		// make sure that number of residues is the same for all files
		for (int i = 0; i < l; i++) {
			if(positions[i].length != positions[0].length) {
				throw new IOException("Number of positions in positions vector are not the same.");
			}
		}
		
		// rewrite files
		for (int i = 0; i < l; i++) {
			String filename = filenames.get(i);
			int[] posArr = positions[i];
			
			// make set of positions
			HashSet<Integer> posSet = new HashSet<Integer>();
			for (int j = 0; j < posArr.length; j++) {
				posSet.add(posArr[j]);
			}
			
			// read atom lines
			File inFile = new File(filename);
			BufferedReader in = new BufferedReader(new FileReader(inFile));
			PrintWriter out = new PrintWriter(tmpPdbFile);
			String line;
			int lastNum = -9999;
			int newNum = 0;
			while((line = in.readLine()) != null) {
				if(line.startsWith("ATOM")) {
					int resNum = Integer.parseInt(line.substring(22, 26).trim());
					if(posSet.contains(resNum)) {
						if(resNum != lastNum) {
							newNum++;
							lastNum = resNum;
						}
						// replace residue number
						String newLine = line.substring(0,22) + String.format("%4d", newNum) + line.substring(26,line.length());
						// write line to temp file
						out.println(newLine);
					}
				}
			}
			in.close();
			out.close();
			
			// copy temp file to original file 
			in = new BufferedReader(new FileReader(tmpPdbFile));
			out = new PrintWriter(inFile);
			while((line = in.readLine()) != null) {
				out.println(line);
			}
			in.close();
			out.close();	
		}
		
	}
	
	/*---------------------------- public methods ---------------------------*/
	
	/**
	 * Returns the rotation matrices of the last Polypose run or null if polypose hasn't been run yet.
	 */
	public Matrix3d[] getRotationMatrices() {
		return this.rotationMatrices;
	}
	
	/**
	 * Calculates a minimum RMSD fit of the given structures and returns the RMSD.
	 * If positions is not null and not an empty array, the given positions will be
	 * used for the fit. positions[i] contains the positions in structure i. The
	 * positions for all structures have to have the same length. If positions is
	 * null or an empty array, all positions will be taken. In this case the
	 * structures must have exactly the same residue numbers. 
	 * @param pdbs the structures to be superimposed
	 * @param positions an array of arrays of positions to be aligned
	 * @return the rmsd of the calculated superimposition
	 */
	public double superimpose(PdbChain[] pdbs, int[][] positions) throws IOException {
		double rmsd = -2;
		File file;
		ArrayList<String> filenames = new ArrayList<String>();
		String[] dummy = new String[pdbs.length];
		int filenum = 1;
		
		writeParamFile(positions);
		
		for(PdbChain pdb:pdbs) {
			file = new File(TMP_DIR, String.format(TMP_PDB_FILE, Integer.toString(filenum)));
			//file = File.createTempFile(TMP_FILE_PREFIX + "_" + filenum + "_", TMP_PDB_FILE_SUFFIX);
			//file.deleteOnExit();
			PrintStream out = new PrintStream(new FileOutputStream(file));
			pdb.writeAtomLines(out);
			out.close();
			filenames.add(file.getAbsolutePath());
			filenum++;
		}
		extractPositions(filenames, positions);
		rmsd = executePolypose(filenames.toArray(dummy), this.tmpParamFile.getAbsolutePath());
		
		// clean up
		this.tmpParamFile.delete();
		for(String fn: filenames) {
			new File(fn).delete();
		}
		
		return rmsd;
	}
	
	/*--------------------------------- main --------------------------------*/
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {

		ArrayList<PdbChain> pdbs = new ArrayList<PdbChain>();
		PdbChain[] dummy = new PdbChain[1];
		String[] filenames = {"/project/StruPPi/CASP8/results/T0464/T0464.reconstructed.pdb",
							  "/project/StruPPi/CASP8/results/T0464/1temp/T0464.reconstructed.pdb",
							  "/project/StruPPi/CASP8/results/T0464/2temps/T0464.reconstructed.pdb"};
		int[][] positions = {{4,5,6},{7,8,9},{1,2,3}};
		double rmsd = -1;
		
		for(String filename:filenames) {
			try {
				PdbAsymUnit fullpdb = new PdbAsymUnit(new File(filename));
				PdbChain pdb = fullpdb.getFirstChain();
				pdbs.add(pdb);
			} catch (PdbLoadException e) {
				System.err.println("Error loading structure: " + e.getMessage());
			} catch (IOException e) {
				System.err.println("Error loading structure: " + e.getMessage());
			} catch (FileFormatException e) {
				System.err.println("Error loading structure: " + e.getMessage());
			}
		}
		
		try {
			PolyposeRunner ppr = new PolyposeRunner("/project/StruPPi/Software/CCP4/ccp4-6.0.1","/bin/sh");
			rmsd = ppr.superimpose(pdbs.toArray(dummy), positions);
		} catch (IOException e) {
			System.err.println("An exception occured while calculating the RMSD: " + e.getMessage());
		}
		System.out.printf("mRMSD = %f\n", rmsd);

	}

}
