import java.io.File;

import owl.core.structure.PdbChain;
import owl.core.structure.PdbAsymUnit;
import owl.core.structure.Residue;


/**
 * Executable class to check the chirality of amino acids of a given PDB file
 * @author duarte
 *
 */
public class checkChirality {

	public static void main(String[] args) throws Exception {
		if (args.length==0) {
			System.err.println("Usage: checkChirality <pdb_file_name>");
			System.exit(1);
		}
		
		File file = new File(args[0]);
		PdbAsymUnit pdb = new PdbAsymUnit(file);
		
		for (PdbChain chain:pdb.getAllChains()) {
			System.out.println("Chain "+chain+". D-form residues:");
			int dcount=0;
			int lcount=0;
			int ucount=0;
			for (int resser:chain.getAllSortedResSerials()) {
				Residue res = chain.getResidue(resser);
				Residue.Chirality chir = res.getChirality();
				if (chir == Residue.Chirality.D) {
					//System.out.println(res+" "+chir.getAbbrev());
					dcount++;
				} else if (chir == Residue.Chirality.L) {
					lcount++;
				} else if (chir == Residue.Chirality.U) {
					ucount++;
				}
			}
			System.out.println("L-form: "+lcount+" D-form: "+dcount+" Undetermined: "+ucount);
		}

	}

}
