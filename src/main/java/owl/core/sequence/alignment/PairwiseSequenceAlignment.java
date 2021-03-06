package owl.core.sequence.alignment;

import java.io.PrintStream;
import java.io.Serializable;

import jaligner.Alignment;
import jaligner.Sequence;
import jaligner.NeedlemanWunschGotoh;
import jaligner.formats.Pair;
import jaligner.matrix.*;
import jaligner.util.*;
	
/**
 * A pairwise protein sequence alignment. This class represents a pair of
 * protein sequences which are globally aligned. Currently it serves mainly
 * as a wrapper to conveniently create an alignment using the NeedlemanWunschGotoh
 * class from the JAligner package with standard parameters.
 * 
 * @author Henning Stehr
 *
 */
public class PairwiseSequenceAlignment implements Serializable {

	private static final long serialVersionUID = 1L;

	/*--------------------------- type definitions --------------------------*/

	// define specific exception for this class
	public class PairwiseSequenceAlignmentException extends Exception {
		private static final long serialVersionUID = 1L;
		public PairwiseSequenceAlignmentException(String str) {
			super(str);
		}
	}

	/*------------------------------ constants ------------------------------*/

	// default parameters
	private static final float		DEFAULT_GAP_OPEN_SCORE =	10f;
	private static final float		DEFAULT_GAP_EXTEND_SCORE =	0.5f;
	private static final String		DEFAULT_MATRIX_NAME =		"BLOSUM50";
	
	/*--------------------------- member variables --------------------------*/

	private String				origSeq1;			// original sequence 1
	private String				origSeq2;			// original sequence 2
	private String				alignedSeq1;		// aligned sequence 1
	private String				alignedSeq2;		// aligned sequence 2
	private int					length;				// total length of alignment
	private int					gaps;				// number of gaps
	private int					identity;			// sequence identity score
	private int					similarity;			// sequence similarity score
	private float				score;				// alignment score
	private transient Alignment	alignment;			// alignment class from JAligner

	private int[]				one2two;			// mapping of sequence indices of sequence 1 to sequence 2
	private int[]				two2one;			// mapping of sequence indices of sequence 2 to sequence 1

	/*----------------------------- constructors ----------------------------*/


	/**
	 * Construct a new pairwise alignment using the Needleman-Wunsch
	 * alignment algorithm with standard parameters. 
	 * @param sequence1
	 * @param sequence2
	 * @throws PairwiseSequenceAlignmentException if problem occurs in parsing the 
	 * sequences or reading the BLOSUM matrix
	 */
	public PairwiseSequenceAlignment(owl.core.sequence.Sequence sequence1, owl.core.sequence.Sequence sequence2) throws PairwiseSequenceAlignmentException {
		this(sequence1.getSeq(),sequence2.getSeq(),sequence1.getName(),sequence2.getName());
	}
	
	/**
	 * Construct a new pairwise alignment using the Needleman-Wunsch
	 * alignment algorithm with standard parameters.
	 * @param seq1 A String containing the first sequence to be aligned
	 * @param seq2 A string containing the second sequence to be aligned
	 * @param name1
	 * @param name2
	 * @throws PairwiseSequenceAlignmentException if problem occurs in parsing the 
	 * sequences or reading the BLOSUM matrix
	 */
	public PairwiseSequenceAlignment(String seq1, String seq2, String name1, String name2) throws PairwiseSequenceAlignmentException {
		this(seq1, seq2, name1, name2, DEFAULT_GAP_OPEN_SCORE, DEFAULT_GAP_EXTEND_SCORE, DEFAULT_MATRIX_NAME);
	}

	/**
	 * Construct a new pairwise alignment using the Needleman-Wunsch
	 * alignment algorithm.
	 * @param seq1 A String containing the first sequence to be aligned
	 * @param seq2 A string containing the second sequence to be aligned
	 * @param name1
	 * @param name2
	 * @param openScore Gap open score
	 * @param extendScore Gap extend score
	 * @param matrixName the matrix name, e.g. "BLOSUM50"
	 * @throws PairwiseSequenceAlignmentException if problem occurs in parsing the 
	 * sequences or reading the BLOSUM matrix
	 */
	public PairwiseSequenceAlignment(String seq1, String seq2, String name1, String name2, float openScore, float extendScore, String matrixName) throws PairwiseSequenceAlignmentException {

		Sequence 	s1			= null;
		Sequence 	s2			= null;
		Matrix 		matrix		= null;
		Alignment	alignment;

		// parse sequences
		try {
			s1 = SequenceParser.parse(seq1);
			s1.setId(name1);
		} catch(SequenceParserException e) {
			throw new PairwiseSequenceAlignmentException("Error parsing seq1: " + e.getMessage());
		}
		try {
			s2 = SequenceParser.parse(seq2);
			s2.setId(name2);
		} catch(SequenceParserException e) {
			throw new PairwiseSequenceAlignmentException("Error parsing seq2: " + e.getMessage());
		}

		// create alignment
		try {
			matrix = MatrixLoader.load(matrixName);
		} catch(MatrixLoaderException e) {
			throw new PairwiseSequenceAlignmentException("Failed to load scoring matrix: " + e.getMessage());
		}

		alignment = NeedlemanWunschGotoh.align(s1, s2, matrix, openScore, extendScore);

		// fill member variables
		this.origSeq1 = 			seq1;
		this.origSeq2 = 			seq2;
		this.alignedSeq1 =			new String(alignment.getSequence1());
		this.alignedSeq2 =			new String(alignment.getSequence2());
		if(getGaplessSequence(alignedSeq1).equals(seq1)
				&& getGaplessSequence(alignedSeq2).equals(seq2)) {
			// alles ok
		} else {
			if(getGaplessSequence(alignedSeq1).equals(seq2)
					&& getGaplessSequence(alignedSeq2).equals(seq1)) {
				// switch sequences
				String tmp = this.alignedSeq1;
				this.alignedSeq1 = this.alignedSeq2;
				this.alignedSeq2 = tmp;
			} else {
				//System.err.println("Error: The following sequences do not match:");
				//System.err.println("Myseq1: " + seq1);			
				//System.err.println("JAseq1: " + getGaplessSequence(this.alignedSeq1));				
				//System.err.println("Myseq2: " + seq2);			
				//System.err.println("JAseq2: " + getGaplessSequence(this.alignedSeq2));
				// BUG!
				throw new PairwiseSequenceAlignmentException("Bug in JAligner");
			}
		}
		this.length =				alignment.getSequence1().length;
		this.gaps =					alignment.getGaps();
		this.identity =				alignment.getIdentity();
		this.similarity =			alignment.getSimilarity();
		this.score =         		alignment.getScore();
		this.alignment =			alignment;

	}

	/*---------------------------- public methods ---------------------------*/

	public char getGapCharacter() { return Alignment.GAP; }		
	
	/**
	 * Returns the length of the alignment.
	 * @return
	 */
	public int getLength() { return this.length; }
	
	/**
	 * Returns the number of gaps in this alignment. 
	 * @return
	 */
	public int getGaps() { return this.gaps; }
		
	public int getIdentity() { return this.identity; }
	public int getSimilarity() { return this.similarity; }
	public float getScore() { return this.score; }
	public float getPercentSimilarity() { return 100.0f * getSimilarity() / length; }
	public float getPercentIdentity() { return 100.0f * getIdentity() / length; }
	public float getPercentGaps() { return 100.0f * getGaps() / length; }
	public float getRelativeScore() { return getScore() / length; }
	
	/**
	 * Returns the aligned sequences in an array of length 2.
	 * @return array of length 2, fist member is first sequence, second member is second sequence
	 */
	public String[] getAlignedSequences() {
		String[] alignedSeqs = {alignedSeq1, alignedSeq2};
		return alignedSeqs;
	}
	
	public char[] getMarkupLine() {
		return this.alignment.getMarkupLine();
	}
	
	/**
	 * Tells wheter this alignment contains no gaps at all.
	 * @return
	 */
	public boolean isGapless() {
		return (gaps==0);
	}
	
	/**
	 * Returns true if the given sequence 1 position matches to the corresponding residue 
	 * in sequence 2 (identity)
	 * @param i the position in sequence 1
	 * @return true if the residues are identical, false otherwise (mismatch or gap)
	 */
	public boolean isMatchingTo2(int i) {
		int posIn2 = getMapping1To2(i);
		if (posIn2==-1) { // falls on a gap in 2
			return false;
		}
		return (this.origSeq1.charAt(i)==this.origSeq2.charAt(posIn2));
	}
	
	/**
	 * Returns true if the given sequence 2 position matches to the corresponding residue 
	 * in sequence 1 (identity)
	 * @param i the position in sequence 2
	 * @return true if the residues are identical, false otherwise (mismatch or gap)
	 */
	public boolean isMatchingTo1(int i) {
		int posIn1 = getMapping2To1(i);
		if (posIn1==-1) { // falls on a gap in 1
			return false; 
		}
		return (this.origSeq1.charAt(posIn1)==this.origSeq2.charAt(i));
	}
	

	public void printSummary() {
		// summary from member variables
		System.out.println("Sequence alignment");
		System.out.println("-------------------------------------------------------");
		System.out.println();
		System.out.printf("Alignment length:\t\t%d\n", getLength());
		System.out.printf("Identity:\t\t\t%d/%d\t(%.2f%%)\n",
				getIdentity(), getLength(), getPercentIdentity());
		System.out.printf("Similarity:\t\t\t%d/%d\t(%.2f%%)\n",
				getSimilarity(), getLength(), getPercentSimilarity());       		
		System.out.printf("Gaps:\t\t\t\t%d/%d\t(%.2f%%)\n",
				getGaps(), getLength(), getPercentGaps());
		System.out.printf("Score:\t\t\t\t%.2f\n", getScore());  
		System.out.println();       
	}

	public void printFullSummary() {
		// summary from JAligner
		System.out.println ( alignment.getSummary() );   	
	}

	public void printReport() {
		// summary from JAligner
		System.out.println ( alignment.getSummary() );		
		// actual alignment from JAligner
		System.out.println ( new Pair().format(alignment) );
	}

	/**
	 * Prints alignment to standard out.
	 */
	public void printAlignment() {
		writeAlignment(System.out);
	}

	/**
	 * Writes alignment to given PrintStream.
	 * @param ps
	 */
	public void writeAlignment(PrintStream ps) {
		ps.println(getAlignmentString());
	}
	
	/**
	 * Gets the alignment as a string
	 * @return
	 */
	public String getAlignmentString() {
		// actual alignment from JAligner
		return new Pair().format(alignment);
	}
	
	/**
	 * Gets a string with a nicely formatted alignment
	 * @return
	 */
	public String getFormattedAlignmentString() {
		return new Pair().format(alignment);
	}
	
	/**
	 * Return the mapping of indices from sequence 1 to indices in sequence 2 based on the calculated alignment.
	 * Indices are counted from 0 to sequence length - 1.
	 * @return An array containing for each position in sequence 1 the corresponding position in sequence 2 or -1
	 * if the position maps to a gap. 
	 */
	public int[] getMapping1To2() {
		if (one2two!=null) {
			return one2two;
		}
		one2two = getMapping(true); 
		return one2two;
	}

	/**
	 * Return the mapping of indices from sequence 2 to indices in sequence 1 based on the calculated alignment.
	 * Indices are counted from 0 to sequence length - 1.
	 * @return An array containing for each position in sequence 2 the corresponding position in sequence 1 or -1
	 * if the position maps to a gap. 
	 */
	public int[] getMapping2To1() {
		if (two2one!=null) {
			return two2one;
		}
		two2one = getMapping(false);
		return two2one;
	}
	
	/**
	 * Return the position in sequence 2 given a position in sequence 1
	 * Indices are counted from 0 to sequence length-1
	 * @param i the position in sequence 1
	 * @return the position in sequence 2 or -1 if the position maps to a gap
	 */
	public int getMapping1To2(int i) {
		return getMapping1To2()[i];
	}
	
	/**
	 * Return the position in sequence 1 given a position in sequence 2
	 * Indices are counted from 0 to sequence length-1
	 * @param i the position in sequence 2
	 * @return the position in sequence 1 or -1 if the position maps to a gap
	 */
	public int getMapping2To1(int i) {
		return getMapping2To1()[i];
	}
	
	/**
	 * Return the position (in sequence 1 or 2 depending of parameter) of the first occurrence of a match (identity)
	 * @param first true if we want the position in sequence 1, false if we want the position in sequence 2  
	 * @return the position or -1 if there are no matches
	 */
	public int getFirstMatchingPos(boolean first) {
		int pos1 = 0;
		int pos2 = 0;
		for (int i=0;i<alignedSeq1.length();i++) {
			char current1 = alignedSeq1.charAt(i);
			char current2 = alignedSeq2.charAt(i);
			if (current1==current2) {
				if (first) return pos1;
				else return pos2;
			}
			if (current1!=Alignment.GAP) pos1++;
			if (current2!=Alignment.GAP) pos2++;
		}
		return -1;
	}
	
	/**
	 * Return the position (in sequence 1 or 2 depending of parameter) of the last occurrence of a match (identity)
	 * @param first true if we want the position in sequence 1, false if we want the position in sequence 2  
	 * @return the position or -1 if there are no matches
	 */
	public int getLastMatchingPos(boolean first) {
		int pos1 = origSeq1.length()-1;
		int pos2 = origSeq2.length()-1;
		for (int i=alignedSeq1.length()-1;i>=0;i--) {
			char current1 = alignedSeq1.charAt(i);
			char current2 = alignedSeq2.charAt(i);
			if (current1==current2) {
				if (first) return pos1;
				else return pos2;
			}
			if (current1!=Alignment.GAP) pos1--;
			if (current2!=Alignment.GAP) pos2--;
		}
		return -1;		
	}
	
	/*---------------------------- private methods --------------------------*/
	
	/**
	 * Returns a string where all gap characters are removed.
	 * @param str The input sequence
	 * @return A string where all gap characters are removed
	 */
	private static String getGaplessSequence(String str) {
		StringBuilder bufi = new StringBuilder(str.length());
		for(int i = 0; i < str.length(); i++) {
			if(str.charAt(i) != Alignment.GAP) {
				bufi.append(str.charAt(i));
			}
		}	
		return bufi.toString();
	}

	/**
	 * @param one2two specifies which mapping is to be returned
	 * @return Returns the mapping from sequence 1 to sequence 2 (if one2two = true) or
	 * from sequence 2 to sequence 1 (if one2two = false).
	 */
	private int[] getMapping(boolean one2two) {
		int[] m1 = new int[origSeq1.length()];
		int[] m2 = new int[origSeq2.length()];
		int idx1 = 0;
		int idx2 = 0;
		for (int i = 0; i < alignedSeq1.length(); i++) {
			// process seq1
			if(alignedSeq1.charAt(i) != Alignment.GAP) {
				if(alignedSeq2.charAt(i) != Alignment.GAP) {
					m1[idx1] = idx2;
				} else {
					m1[idx1] = -1;
				}
			}
			// process seq2
			if(alignedSeq2.charAt(i) != Alignment.GAP) {
				if(alignedSeq1.charAt(i) != Alignment.GAP) {
					m2[idx2] = idx1;
				} else {
					m2[idx2] = -1;
				}					
			}
			if(alignedSeq1.charAt(i) != Alignment.GAP) idx1++;
			if(alignedSeq2.charAt(i) != Alignment.GAP) idx2++;
		}
		if(one2two) return m1; else return m2;
	}
}

