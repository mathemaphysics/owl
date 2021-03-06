package owl.core.structure;

import java.io.Serializable;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point3i;
import javax.vecmath.Vector3d;

import owl.core.util.GeometryTools;

/**
 * Representation of a transformation in a crystal: 
 * - a transformation id (each of the transformations in a space group, 0 to m)
 * - a crystal translation
 * The transformation matrix in crystal basis is stored, representing the basic 
 * transformation together with the crystal translation. 
 * Contains methods to check for equivalent transformations.
 * 
 * 
 * @author duarte_j
 *
 */
public class CrystalTransform implements Serializable {

	private static final long serialVersionUID = 1L;


	public static final Matrix4d IDENTITY = new Matrix4d(1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1);

	/**
	 * The space group to which this transform belongs
	 */
	private final SpaceGroup sg;
	
	/**
	 * The transform id corresponding to the SpaceGroup's transform indices. 
	 * From 0 (identity) to m (m=number of symmetry operations of the space group)
	 * It is unique within the unit cell but equivalent units of different crystal unit cells 
	 * will have same id 
	 */
	private int transformId;
	
	/**
	 * The 4-dimensional matrix transformation in crystal basis.
	 * Note that the translational component of this matrix is not necessarily
	 * identical to crystalTranslation since some operators have fractional 
	 * translations within the cell
	 */
	private Matrix4d matTransform;
	
	/**
	 * The crystal translation (always integer)
	 */
	private Point3i crystalTranslation;
	
	
	/**
	 * Creates a new CrystalTransform representing the identity transform 
	 * in cell (0,0,0)
	 */
	public CrystalTransform(SpaceGroup sg) {
		this.sg = sg;
		this.transformId = 0;
		this.matTransform = (Matrix4d)IDENTITY.clone();
		this.crystalTranslation = new Point3i(0,0,0);
	}
	
	public CrystalTransform(SpaceGroup sg, int transformId) {
		this.sg = sg;
		this.transformId = transformId;
		this.matTransform = (Matrix4d)sg.getTransformation(transformId).clone();
		this.crystalTranslation = new Point3i(0,0,0);

	}
	
	public CrystalTransform(CrystalTransform transform) {
		this.sg = transform.sg;
		this.transformId = transform.transformId;
		this.matTransform = new Matrix4d(transform.matTransform);
		this.crystalTranslation = new Point3i(transform.crystalTranslation);
		
	}
	
	public Matrix4d getMatTransform() {
		return matTransform;
	}
	
	public void setMatTransform(Matrix4d matTransform) {
		this.matTransform = matTransform;
	}
	
	public Point3i getCrystalTranslation() {
		return crystalTranslation;
	}
	
	public void translate(Point3i translation) {
		matTransform.m03 = matTransform.m03+(double)translation.x;
		matTransform.m13 = matTransform.m13+(double)translation.y;
		matTransform.m23 = matTransform.m23+(double)translation.z;
		
		crystalTranslation.add(translation); 

	}
	
	/**
	 * Returns true if the given CrystalTransform is equivalent to this one.
	 * Two crystal transforms are equivalent if one is the inverse of the other, i.e.
	 * their transformation matrices multiplication is equal to the identity.
	 * @param other
	 * @return
	 */
	public boolean isEquivalent(CrystalTransform other) {
		Matrix4d mul = new Matrix4d();
		mul.mul(this.matTransform,other.matTransform);

		if (mul.epsilonEquals(IDENTITY, 0.0001)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Tells whether this transformation is a pure crystal lattice translation,
	 * i.e. no rotational component and an integer translation vector.
	 * @return
	 */
	public boolean isPureCrystalTranslation() {
		return (transformId==0 && (crystalTranslation.x!=0 || crystalTranslation.y!=0 || crystalTranslation.z!=0));
	}
	
	/**
	 * Tells whether this transformation is the identity: no rotation and no translation
	 * @return
	 */
	public boolean isIdentity() {
		return (transformId==0 && crystalTranslation.x==0 && crystalTranslation.y==0 && crystalTranslation.z==0);
	}
	
	/**
	 * Tells whether this transformation is a pure translation:
	 * either a pure crystal (lattice) translation or a fractional (within
	 * unit cell) translation: space groups Ixxx, Cxxx, Fxxx have operators
	 * with fractional translations within the unit cell.
	 * @return
	 */
	public boolean isPureTranslation() {
		if (isPureCrystalTranslation()) return true;
		if (SpaceGroup.deltaComp(matTransform.m00,1,SpaceGroup.DELTA) &&
			SpaceGroup.deltaComp(matTransform.m01,0,SpaceGroup.DELTA) &&
			SpaceGroup.deltaComp(matTransform.m02,0,SpaceGroup.DELTA) &&
			
			SpaceGroup.deltaComp(matTransform.m10,0,SpaceGroup.DELTA) &&
			SpaceGroup.deltaComp(matTransform.m11,1,SpaceGroup.DELTA) &&
			SpaceGroup.deltaComp(matTransform.m12,0,SpaceGroup.DELTA) &&
			
			SpaceGroup.deltaComp(matTransform.m20,0,SpaceGroup.DELTA) &&
			SpaceGroup.deltaComp(matTransform.m21,0,SpaceGroup.DELTA) &&
			SpaceGroup.deltaComp(matTransform.m22,1,SpaceGroup.DELTA) &&
			(	Math.abs(matTransform.m03-0.0)>SpaceGroup.DELTA || 
				Math.abs(matTransform.m13-0.0)>SpaceGroup.DELTA || 
				Math.abs(matTransform.m23-0.0)>SpaceGroup.DELTA)) {
			return true;
		}
				
		return false;
	}
	
	/**
	 * Tells whether this transformation contains a fractional translational
	 * component (whatever its rotational component). A fractional translation
	 * together with a rotation means a screw axis.
	 * @return
	 */
	public boolean isFractionalTranslation() {
		if ((Math.abs(matTransform.m03-crystalTranslation.x)>SpaceGroup.DELTA) ||
			(Math.abs(matTransform.m13-crystalTranslation.y)>SpaceGroup.DELTA) ||
			(Math.abs(matTransform.m23-crystalTranslation.z)>SpaceGroup.DELTA)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Tells whether this transformation is a rotation disregarding the translational component,
	 * i.e. either pure rotation or screw rotation, but not improper rotation.
	 * @return
	 */
	public boolean isRotation() {
		// if no SG, that means a non-crystallographic entry (e.g. NMR). We return false
		if (sg==null) return false;
		
		// this also takes care of case <0 (improper rotations): won't be considered as rotations
		if (sg.getAxisFoldType(this.transformId)>1) return true;
		
		return false;
	}
	
	/**
	 * Returns the TransformType of this transformation: AU, crystal translation, fractional translation
	 * , 2 3 4 6-fold rotations, 2 3 4 6-fold screw rotations, -1 -3 -2 -4 -6 inversions/rotoinversions. 
	 * @return
	 */
	public TransformType getTransformType() {
		
		// if no SG, that means a non-crystallographic entry (e.g. NMR). We return AU as type
		if (sg==null) return TransformType.AU;
		
		int foldType = sg.getAxisFoldType(this.transformId);
		boolean isScrewOrGlide = false;
		Vector3d translScrewComponent = getTranslScrewComponent();
		if (Math.abs(translScrewComponent.x-0.0)>SpaceGroup.DELTA || 
			Math.abs(translScrewComponent.y-0.0)>SpaceGroup.DELTA || 
			Math.abs(translScrewComponent.z-0.0)>SpaceGroup.DELTA) {
			
			isScrewOrGlide = true;
		}

		if (foldType>1) {

			if (isScrewOrGlide) {
				switch (foldType) {
				case 2:
					return TransformType.TWOFOLDSCREW;
				case 3:
					return TransformType.THREEFOLDSCREW;
				case 4:
					return TransformType.FOURFOLDSCREW;
				case 6:
					return TransformType.SIXFOLDSCREW;
				default:
					throw new NullPointerException("This transformation did not fall into any of the known types! This is most likely a bug.");					
				}
			} else {
				switch (foldType) {
				case 2:
					return TransformType.TWOFOLD;
				case 3:
					return TransformType.THREEFOLD;
				case 4:
					return TransformType.FOURFOLD;
				case 6:
					return TransformType.SIXFOLD;
				default:
					throw new NullPointerException("This transformation did not fall into any of the known types! This is most likely a bug.");
				}				
			}

		} else if (foldType<0) {
			switch (foldType) {
			case -1:
				return TransformType.ONEBAR;
			case -2:
				if (isScrewOrGlide) {
					return TransformType.GLIDE;
				}
				return TransformType.TWOBAR;
			case -3:
				return TransformType.THREEBAR;
			case -4:
				return TransformType.FOURBAR;
			case -6:
				return TransformType.SIXBAR;
			default:
				throw new NullPointerException("This transformation did not fall into any of the known types! This is most likely a bug.");
			}	
		} else {
			if (isIdentity()) {
				return TransformType.AU;
			}
			if (isPureCrystalTranslation()) {
				return TransformType.XTALTRANSL;
			}
			if (isFractionalTranslation()) {
				return TransformType.CELLTRANSL;
			}
			throw new NullPointerException("This transformation did not fall into any of the known types! This is most likely a bug.");
		}
	
	}
	
	public Vector3d getTranslScrewComponent() {
		return GeometryTools.getTranslScrewComponent(matTransform);
	}
	
	public int getTransformId() {
		return transformId;
	}
	
	public void setTransformId(int transformId) {
		this.transformId = transformId;
	}
	
	public String toString() {
		return String.format("[%2d-(%2d,%2d,%2d)]",transformId,crystalTranslation.x,crystalTranslation.y,crystalTranslation.z);
	}

}
