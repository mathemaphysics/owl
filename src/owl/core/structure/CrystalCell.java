package owl.core.structure;

import java.util.ArrayList;
import java.util.Collections;

import javax.vecmath.Matrix3d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;


/**
 * A crystal cell's parameters.
 * 
 * @author duarte_j
 *
 */
public class CrystalCell {

	private double a;
	private double b;
	private double c;
	
	private double alpha;
	private double beta;
	private double gamma;
	
	private double alphaRad;
	private double betaRad;
	private double gammaRad;
	
	private double maxDimension; // cached max dimension

	private Matrix3d M; 	// cached basis change transformation matrix
	private Matrix3d Minv;  // cached basis change transformation matrix
	private Matrix3d Mtransp;
	private Matrix3d MtranspInv;
	
	public CrystalCell(double a, double b, double c, double alpha, double beta, double gamma){
		this.a = a;
		this.b = b;
		this.c = c;
		this.alpha = alpha;
		this.beta = beta;
		this.gamma = gamma;
		
		this.alphaRad = Math.toRadians(alpha);
		this.betaRad  = Math.toRadians(beta);
		this.gammaRad = Math.toRadians(gamma);

	}

	public double getA() {
		return a;
	}

	public void setA(double a) {
		this.a = a;
	}

	public double getB() {
		return b;
	}

	public void setB(double b) {
		this.b = b;
	}

	public double getC() {
		return c;
	}

	public void setC(double c) {
		this.c = c;
	}

	public double getAlpha() {
		return alpha;
	}

	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}

	public double getBeta() {
		return beta;
	}

	public void setBeta(double beta) {
		this.beta = beta;
	}

	public double getGamma() {
		return gamma;
	}

	public void setGamma(double gamma) {
		this.gamma = gamma;
	}
	
	/**
	 * Returns the volume of this unit cell.
	 * See http://en.wikipedia.org/wiki/Parallelepiped
	 * @return
	 */
	public double getVolume() {
		
		return a*b*c*
		Math.sqrt(1-Math.cos(alphaRad)*Math.cos(alphaRad)-Math.cos(betaRad)*Math.cos(betaRad)-Math.cos(gammaRad)*Math.cos(gammaRad)
				+2.0*Math.cos(alphaRad)*Math.cos(betaRad)*Math.cos(gammaRad));
	}
	
	/**
	 * Returns the unit cell translation transformation matrix, given 3 integers for each 
	 * of the directions of the unit cell.
	 * See "Fundamentals of Crystallography" C. Giacovazzo, section 2.5, eq 2.30
	 * @param direction
	 * @return
	 */
	public Matrix4d getTransform(Vector3d direction) {
		Matrix4d mat = new Matrix4d();
		mat.setIdentity();
		mat.setTranslation(getTranslationVector(direction));
		return mat;
	}
	
	private Vector3d getTranslationVector(Vector3d direction) {
		Vector3d translationVec = new Vector3d();
		// see Giacovazzo section 2.E, eq. 2.E.1
		getMTransposeInv().transform(direction, translationVec);
		return translationVec;
	}
	
	/**
	 * Transform given Matrix4d in crystal basis to the orthonormal basis using
	 * the PDB axes convention (NCODE=1)
	 * @param m
	 * @return
	 */
	public Matrix4d transfToOrthonormal(Matrix4d m) {
		Vector3d trans = this.getTranslationVector(new Vector3d(m.m03,m.m13,m.m23));
		
		Matrix3d rot = new Matrix3d();
		m.getRotationScale(rot);
		// see Giacovazzo section 2.E, eq. 2.E.1
		// Rprime = MT-1 * R * MT
		rot.mul(this.getMTranspose());
		rot.mul(this.getMTransposeInv(),rot);

		return new Matrix4d(rot,trans,1.0);
	}

	/**
	 * Returns the change of basis (crystal to orthonormal) transform matrix, that is 
	 * M inverse in the notation of Giacovazzo. 
	 * Using the PDB axes convention (NCODE=1). 
	 * The matrix is only calculated upon first call of this method, thereafter it is cached.
	 * See "Fundamentals of Crystallography" C. Giacovazzo, section 2.5 
	 * @return
	 */
	private Matrix3d getMInv() {
		if (Minv!=null) {
			return Minv;
		}
		// see Table 2.1 of chapter 2 of Giacovazzo
		double cosAlphaStar = (Math.cos(betaRad)*Math.cos(gammaRad)-Math.cos(alphaRad))/(Math.sin(betaRad)*Math.sin(gammaRad));
		double cStar = (this.a*this.b*Math.sin(gammaRad))/getVolume();
		// see eq. 2.30 Giacovazzo
		double m21 = -this.c*Math.sin(betaRad)*cosAlphaStar;
		double m22 = 1.0/cStar;
		Minv =  new Matrix3d(                    this.a,                         0,    0,
							  this.b*Math.cos(gammaRad), this.b*Math.sin(gammaRad),    0,
							  this.c*Math.cos(betaRad) ,                       m21,  m22);
		return Minv;
	}
	
	/**
	 * Returns the change of basis (orthonormal to crystal) transform matrix, that is
	 * M in the notation of Giacovazzo.
	 * Using the PDB convention (NCODE=1).
	 * The matrix is only calculated upon first call of this method, thereafter it is cached. 
	 * See "Fundamentals of Crystallography" C. Giacovazzo, section 2.5 
	 * @return
	 */
	private Matrix3d getM() {
		if (M!=null){
			return M;
		}
		M = new Matrix3d();
		M.invert(getMInv());
		return M;
	}
	
	private Matrix3d getMTranspose() {
		if (Mtransp!=null){
			return Mtransp;
		}
		Matrix3d M = getM();
		Mtransp = new Matrix3d();
		Mtransp.transpose(M);
		return Mtransp;
	}
	
	private Matrix3d getMTransposeInv() {
		if (MtranspInv!=null){
			return MtranspInv;
		}
		MtranspInv = new Matrix3d();
		MtranspInv.invert(getMTranspose());
		return MtranspInv;
	}
	
	/**
	 * Transforms the given crystal basis coordinates into orthonormal coordinates.
	 * e.g. getOrthFromCrystalCoords(new Point3d(1,1,1)) returns the orthonormal coordinates of the 
	 * vertex of the unit cell.
	 * @param vertex
	 * @return
	 */
	public void getOrthFromCrystalCoords(Point3d vertex) {
		getMTransposeInv().transform(vertex);		
	}
	
	/**
	 * Gets the maximum dimension of the unit cell.
	 * @return
	 */
	public double getMaxDimension() {
		if (maxDimension!=0) {
			return maxDimension;
		}
		Point3d vert0 = new Point3d(0,0,0);
		Point3d vert1 = new Point3d(1,0,0);
		getOrthFromCrystalCoords(vert1);
		Point3d vert2 = new Point3d(0,1,0);
		getOrthFromCrystalCoords(vert2);
		Point3d vert3 = new Point3d(0,0,1);
		getOrthFromCrystalCoords(vert3);
		Point3d vert4 = new Point3d(1,1,0);
		getOrthFromCrystalCoords(vert4);
		Point3d vert5 = new Point3d(1,0,1);
		getOrthFromCrystalCoords(vert5);
		Point3d vert6 = new Point3d(0,1,1);
		getOrthFromCrystalCoords(vert6);
		Point3d vert7 = new Point3d(1,1,1);
		getOrthFromCrystalCoords(vert7);

		ArrayList<Double> vertDists = new ArrayList<Double>();
		vertDists.add(vert0.distance(vert7));
		vertDists.add(vert3.distance(vert4));
		vertDists.add(vert1.distance(vert6));
		vertDists.add(vert2.distance(vert5));
		maxDimension = Collections.max(vertDists);
		return maxDimension;
	}
	
	
}
