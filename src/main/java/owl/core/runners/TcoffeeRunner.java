package owl.core.runners;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;

import owl.core.sequence.Sequence;
import owl.core.sequence.alignment.AlignmentConstructionException;
import owl.core.sequence.alignment.MultipleSequenceAlignment;
import owl.core.util.FileFormatException;


/**
 * A class to run tcoffee.
 * @author duarte
 *
 */
public class TcoffeeRunner {

	// t-coffee seems to have a bug in outputting directly to fasta_aln format: it will add 
	// one extra character to all sequences except for the target. This is why we use clustalw
	private static final String DEFAULT_SEQ2PROF_OUTFORMAT = "clustalw";
	
	private static final boolean DEBUG = false;
	private File tcofProg;

	private String cmdLine;
	private File logFile;
	
	public TcoffeeRunner(File tcofProg) {
		this.tcofProg = tcofProg;
	}

	/**
	 * Aligns a sequence to a profile (in the form of a multiple sequence alignment)
	 * by using tcoffee's profile comparison
	 * @param seq the sequence object (its tag will be the one used in the output Alignment)
	 * @param profile the multiple sequence alignment representing the profile to align to 
	 * @return
	 * @throws TcoffeeException if tcoffee fails to run
	 * @throws IOException if problem while reading/writing temp files needed to run tcoffee
	 */
	public MultipleSequenceAlignment alignSequence2Profile(Sequence seq, MultipleSequenceAlignment profile, File logFile) throws TcoffeeException, IOException, InterruptedException {
		File inFile = File.createTempFile("tcof.", ".in");
		if (!DEBUG) inFile.deleteOnExit();
		seq.writeToFastaFile(inFile);
		File outFile = File.createTempFile("tcof.", ".out");
		if (!DEBUG) outFile.deleteOnExit();
		File outTreeFile = File.createTempFile("tcof.", ".dnd");
		if (!DEBUG) outTreeFile.deleteOnExit();
		File profileFile = File.createTempFile("tcof", "profile");
		if (!DEBUG) profileFile.deleteOnExit();
		PrintStream out = new PrintStream(profileFile);
		profile.writeFasta(out, 80, true);
		out.close();
		buildCmdLine(inFile, outFile, DEFAULT_SEQ2PROF_OUTFORMAT, outTreeFile, profileFile, logFile, false, 1);
		runTcoffee();
		
		MultipleSequenceAlignment al =  null;
		try {
			al = new MultipleSequenceAlignment(outFile.getAbsolutePath(), MultipleSequenceAlignment.CLUSTALFORMAT);
		} catch (AlignmentConstructionException e) {
			throw new TcoffeeException("Couldn't construct Alignment from Tcoffee output alignment "+outFile+". Error "+e.getMessage());			
		} catch (FileFormatException e) {
			throw new TcoffeeException("Couldn't construct Alignment from Tcoffee output alignment "+outFile+". Error "+e.getMessage());
		}
		
		return al;
	}
	
	/**
	 * Builds the t_coffee command line returning it.
	 * The command line will then be executed upon call of {@link #runTcoffee(File, File, String, File, File, File, boolean, int)}
	 * @param inFile the file with the sequences to be aligned
	 * @param outFile the output file that will contain the aligned sequences
	 * @param outFormat the output format, valid values are: fasta, clustalw
	 * @param outTreeFile the .dnd file where the guide tree will be written to
	 * @param profileFile the fasta file with a multiple sequence alignment representing 
	 * the profile to align to, if null no profile will be used
	 * @param logFile all stdout/stderr of t_coffee will be logged, if null no logging at all (quiet mode)
	 * @param veryFast if true will use t_coffee quickaln mode (faster but less accurate)
	 * @param nThreads how many CPU cores should t_coffee (-n_core option of t_coffee) 
	 * @return
	 */
	public String buildCmdLine(File inFile, File outFile, String outFormat, File outTreeFile, File profileFile, File logFile, boolean veryFast, int nThreads) {
		this.logFile = logFile;
		String profStr = "";
		if (profileFile!=null) {
			profStr = "-profile "+profileFile+" -profile_comparison=full50";
		}
		String quietStr = "-quiet";
		String veryFastStr = "";
		if (veryFast) {
			veryFastStr = "-mode quickaln";
		}
		cmdLine = tcofProg + " "+ inFile + " "+ profStr + " -output=" +outFormat+" -outfile="+outFile+" "
				+" -newtree="+outTreeFile+" "
				+quietStr+" "+veryFastStr+" -n_core="+nThreads;
		return cmdLine;
	}
	
	/**
	 * Runs t_coffee with command line built trough {@link #buildCmdLine(File, File, String, File, File, File, boolean, int)}
	 * @throws TcoffeeException if t_coffee exits with non 0 status or an IOException occurs
	 */
	public void runTcoffee() throws TcoffeeException, InterruptedException {
		
		try {
			
			PrintWriter tcofLog = new PrintWriter(logFile);
			
			Process proc = Runtime.getRuntime().exec(cmdLine);

			BufferedReader errorBR = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
			String line;
			while((line = errorBR.readLine()) != null) {
				tcofLog.println(line);
			}

			int exitValue = proc.waitFor();
			// throwing exception if exit state is not 0 
			if (exitValue!=0) {
				tcofLog.close();
				throw new TcoffeeException(tcofProg + " exited with value "+exitValue+". Revise log file "+logFile);
			}

			tcofLog.close();
			
		} catch (IOException e) {
			throw new TcoffeeException("IO error while trying to run "+tcofProg+": "+e.getMessage());
		}
		
		
	}
	
	/**
	 * Returns the last run t_coffee cached command line
	 * @return
	 */
	public String getCmdLine() {
		return cmdLine;
	}
	
	public static void main(String[] args) throws Exception {
		File tcofProg = new File("/project/StruPPi/bin/t_coffee");
		File logFile = new File("/tmp/tcoffee.log");
		TcoffeeRunner tcr = new TcoffeeRunner(tcofProg);
		File seqFile = new File("/project/StruPPi/CASP7/targets/T0290.fa");
		File alFile = new File("/project/StruPPi/CASP8/dryrun/casp7_bla_pb_gtg/bla_max10_e5_s1000_cct4/T0290/T0290.templates_aln.fasta");
		Sequence seq = new Sequence();
		seq.readFromFastaFile(seqFile);
		MultipleSequenceAlignment al = new MultipleSequenceAlignment(alFile.getAbsolutePath(),MultipleSequenceAlignment.FASTAFORMAT);
		MultipleSequenceAlignment outAl = tcr.alignSequence2Profile(seq, al, logFile);
		outAl.writeFasta(System.out, 60, true);
		
	}
	
}
