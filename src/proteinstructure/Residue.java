package proteinstructure;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;

/**
 * A class representing a residue as part of a PDB protein structure 
 * @author duarte
 *
 */
public class Residue implements Iterable<Atom> {

	public enum Chirality {
		L( 1,"L","L-form"),
		D(-1,"D","D-form"),
		U(10,"U","undetermined"),
		C( 0,"C","coplanar");
		private int number;
		private String abbrev;
		private String name;
		private Chirality(int number, String abbrev, String name) {
			this.number = number;
			this.abbrev = abbrev;
			this.name = name;
		}
		public int getNumber() {
			return number;
		}
		public String getAbbrev() {
			return abbrev;
		}
		public String getName() {
			return name;
		}
	}
	
	private AminoAcid aaType;
	private int serial;
	private String pdbSerial;
	
	private Pdb parentPdb;
	
	private SecStrucElement ssElem;

	// following variables are Double/Integer objects instead of primitives
	// to be able to keep the old behaviour of using nulls when something
	// is missing
	private Double consurfScore;
	private Integer consurfColor;
	private Double rsa;
	private Double scRsa;
	
	private TreeMap<String, Atom> atoms; // atom codes to atoms
	
	/**
	 * Constructs a new Residue given its type, serial and parentPdb. The Residue will 
	 * have an empty list of Atoms until they are added using {@link #addAtom(Atom)} 
	 * @param aaType
	 * @param serial
	 * @param parentPdb
	 */
	public Residue(AminoAcid aaType, int serial, Pdb parentPdb) {
		atoms = new TreeMap<String, Atom>();
		this.aaType = aaType;
		this.serial = serial;
		this.parentPdb = parentPdb;
	}

	/**
	 * Adds an atom to this Residue. If the atom code for given Atom is already present
	 * in this Residue a warning will be printed. No duplicate atom codes are allowed in 
	 * one Residue.
	 * @param atom
	 */
	public void addAtom(Atom atom) {
		if (this.containsAtom(atom.getCode())) {
			System.err.println("Warning: atom "+atom+" being added to residue "+this+" is already present in this residue.");
		} else {
			atoms.put(atom.getCode(), atom);
		}
	}
	
	/**
	 * Returns the Atom object given its atomCode or if no such atom for this residue returns null.
	 * @param atomCode
	 * @return the Atom object or null if no such atom exists in this residue
	 */
	public Atom getAtom(String atomCode) {
		if (this.containsAtom(atomCode)) {
			return atoms.get(atomCode);
		} else {
			// we used to have this warning in the Pdb class, copied here to keep it where it should be
			//System.err.println("Couldn't find "+atomCode+" atom for resser="+this.getSerial()+" in protein "+pdbCode+" and chain "+chainCode+". Continuing without that atom for this resser.");
			return null;
		}
	}
	
	/**
	 * Returns true if this Residue instance has the given atomCode
	 * @param atomCode
	 * @return
	 */
	public boolean containsAtom(String atomCode) {
		return atoms.containsKey(atomCode);
	}

	/**
	 * Returns true if this Residue contains an OXT atom (terminal Oxygen atom 
	 * present in the last residue of a chain)
	 * @return
	 */
	public boolean containsOXT() {
		return containsAtom("OXT");
	}
	
	/**
	 * Returns the number of atoms in this Residue, including Hydrogens if they are 
	 * present
	 * @return
	 */
	public int getNumAtoms() {
		return atoms.size();
	}
	
	/**
	 * Returns the number of heavy (non-Hydrogen) atoms in this Residue
	 * @return
	 */
	public int getNumHeavyAtoms() {
		int numAtoms = 0;
		for (Atom atom:atoms.values()) {
			if (atom.getType()!=AtomType.H) {
				numAtoms++;
			}
		}
		return numAtoms;
	}
	
	public AminoAcid getAaType() {
		return aaType;
	}

	public void setAaType(AminoAcid aaType) {
		this.aaType = aaType;
	}

	public int getSerial() {
		return serial;
	}

	public void setSerial(int serial) {
		this.serial = serial;
	}

	public String getPdbSerial() {
		return pdbSerial;
	}

	public void setPdbSerial(String pdbSerial) {
		this.pdbSerial = pdbSerial;
	}

	public String getChainCode() {
		return parentPdb.getChainCode();
	}
	
	public String getPdbChainCode() {
		return parentPdb.getPdbChainCode();
	}

	public Pdb getParentPdb() {
		return parentPdb;
	}

	public void setParentPdb(Pdb pdb) {
		this.parentPdb = pdb;
	}

	public SecStrucElement getSsElem() {
		return ssElem;
	}

	public void setSsElem(SecStrucElement ssElem) {
		this.ssElem = ssElem;
	}

	public Double getConsurfScore() {
		return consurfScore;
	}

	public void setConsurfScore(Double consurfScore) {
		this.consurfScore = consurfScore;
	}

	public Integer getConsurfColor() {
		return consurfColor;
	}

	public void setConsurfColor(Integer consurfColor) {
		this.consurfColor = consurfColor;
	}

	public Double getRsa() {
		return rsa;
	}

	public void setRsa(Double rsa) {
		this.rsa = rsa;
	}

	public Double getScRsa() {
		return scRsa;
	}

	public void setScRsa(Double scrsa) {
		this.scRsa = scrsa;
	}

	/**
	 * Get Collection of Atoms belonging to this Residue sorted by atom codes
	 * @return
	 */
	public Collection<Atom> getAtoms() {
		return atoms.values();
	}
	
	/**
	 * Gets a new Residue object containing only the atoms for the given contact type
	 * (the new atom instances are references to the atom instances of this Residue)
	 * @param ct
	 * @return
	 */
	public Residue getReducedResidue(String ct) {
		Residue reducedResidue = new Residue(this.getAaType(), this.getSerial(), this.getParentPdb());
		Set<String> atomCodes = AAinfo.getAtomsForCTAndRes(ct, this.getAaType().getThreeLetterCode());
		for (String atomCode:atomCodes) {
			if (this.containsAtom(atomCode)) {
				reducedResidue.addAtom(this.getAtom(atomCode));
			}
		}
		return reducedResidue;
	}

	/**
	 * Returns the centre of mass of the heavy side chains atoms present in this Residue,
	 * if no atoms in the sice chain, then the CA coordinates are returned or null if there's
	 * no CA.
	 * @return
	 */
	public Point3d getScCentroid() {
		Residue scOnly = getReducedResidue("SC");
		if (scOnly.getNumAtoms()==0) {
			if (this.containsAtom("CA")) {
				return this.getAtom("CA").getCoords();
			} else {
				return null;
			}
		}
		Point3d centroid = new Point3d();
		double massSum = 0;
		for (Atom atom:scOnly.getAtoms()) {
			Point3d coord = new Point3d(atom.getCoords());
			coord.scale(atom.getType().getAtomicMass());
			centroid.add(coord);
			massSum+=atom.getType().getAtomicMass();
		}
		centroid.scale(1.0/massSum);
		
		return centroid;
	}
	
	/**
	 * Finds out wheter this Residue is of the L-form or the D-form or has 
	 * no chiral center, either because it is a Gly or because one of the 4 atoms 
	 * around the CA is missing: C, CB, HA or N 
	 * See Chapter "The Mathematics of Chirality", Distance Geometry and Molecular Conformation,
	 * G.M. Crippen, T.M. Havel
	 * @return
	 */
	public Chirality getChirality() {
		if (!containsAtom("C") || !containsAtom("CB") || !containsAtom("HA") || !containsAtom("N")) {
			return Chirality.U;
		}
		Point3d h = getAtom("HA").getCoords();
		Point3d c = getAtom("C").getCoords();
		Point3d r = getAtom("CB").getCoords();
		Point3d n = getAtom("N").getCoords();
		
		// see equation 2.1 of book mentioned above
		Matrix4d oriVolMat = new Matrix4d(1,   1,   1,   1, 
										h.x, c.x, r.x, n.x, 
										h.y, c.y, r.y, n.y,
										h.z, c.z, r.z, n.z);
		double oriVol = oriVolMat.determinant();
		if (oriVol>0) {
			return Chirality.L;
		} else if (oriVol<0) {
			return Chirality.D;
		} else {
			// this shouldn't ever happen because oriVol can't really be 0 exactly
			// anyway we keep this just in case
			return Chirality.C;
		}
	}
	
	public String toString() {
		return this.getChainCode()+serial+aaType.getOneLetterCode();
	}

	public Iterator<Atom> iterator() {
		return atoms.values().iterator();
	}
	
}