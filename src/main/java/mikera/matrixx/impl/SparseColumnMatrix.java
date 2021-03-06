package mikera.matrixx.impl;

import java.util.List;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.Map;
// import java.util.Map.Entry;

import mikera.arrayz.ISparse;
import mikera.matrixx.AMatrix;
import mikera.matrixx.Matrix;
import mikera.vectorz.AVector;
import mikera.vectorz.Op;
import mikera.vectorz.Vector;
import mikera.vectorz.Vectorz;
import mikera.vectorz.impl.SparseIndexedVector;
import mikera.vectorz.util.ErrorMessages;
import mikera.vectorz.util.VectorzException;

/**
 * Matrix stored as a collection of normally sparse column vectors
 * 
 * This format is especially efficient for:
 * - transposeInnerProduct() with another matrix
 * - access via getColumn() operation
 * - transpose into SparseRowMatrix
 * 
 * @author Mike
 *
 */
public class SparseColumnMatrix extends ASparseRCMatrix implements ISparse, IFastColumns {
	private static final long serialVersionUID = -5994473197711276621L;

	private static final long SPARSE_ELEMENT_THRESHOLD = 1000L;
	
	private final AVector emptyColumn;

	protected SparseColumnMatrix(int rowCount, int columnCount) {
		this(new AVector[columnCount],rowCount,columnCount);
	}
	
	protected SparseColumnMatrix(AVector[] data, int rowCount, int columnCount) {
		super(rowCount,columnCount,data);
        if (data.length != columnCount)
            throw new IllegalArgumentException(ErrorMessages.incompatibleShapes(columnCount, data.length));
		emptyColumn=Vectorz.createZeroVector(rowCount);
	}

	protected SparseColumnMatrix(AVector... vectors) {
		this(vectors, vectors[0].length(), vectors.length);
	}

	protected SparseColumnMatrix(List<AVector> data, int rowCount, int columnCount) {
		this(data.toArray(new AVector[0]),rowCount,columnCount);
	}

	protected SparseColumnMatrix(List<AVector> data) {
		this(data.toArray(new AVector[0]));
	}

//  	protected SparseColumnMatrix(HashMap<Integer,AVector> data, int rowCount, int columnCount) {
//  		super(rowCount,columnCount,data);
//  		emptyColumn=Vectorz.createZeroVector(rowCount);
//  	}

	public static SparseColumnMatrix create(int rows, int cols) {
		return new SparseColumnMatrix(rows, cols);
	}

	public static SparseColumnMatrix create(AVector[] data, int rows, int cols) {
		return new SparseColumnMatrix(data, rows, cols);
	}

	public static SparseColumnMatrix create(AVector... vecs) {
		return new SparseColumnMatrix(vecs);
        // don't validate; user can call validate() if they want it.
	}
	
	public static SparseColumnMatrix create(List<AVector> vecs) {
		return create(vecs.toArray(new AVector[0]));
	}
	
	public static SparseColumnMatrix wrap(AVector[] vecs, int rows, int cols) {
		return create(vecs, rows, cols);
	}
	
	public static SparseColumnMatrix wrap(AVector... vecs) {
		return create(vecs);
	}
	
	public static SparseColumnMatrix create(AMatrix source) {
		int cc=source.columnCount();
		int rc=source.rowCount();
		AVector[] data = new AVector[cc];
		for (int i=0; i<cc; i++) {
			AVector col = source.getColumn(i);
			if (!col.isZero())
			    data[i] = Vectorz.createSparse(col);
		}
		return new SparseColumnMatrix(data,rc,cc);
	}
	
	public static SparseColumnMatrix wrap(List<AVector> vecs) {
		return create(vecs);
	}
	
//	public static SparseColumnMatrix wrap(HashMap<Integer,AVector> cols, int rowCount, int columnCount) {
//		return new SparseColumnMatrix(cols,rowCount,columnCount);
//	}
	
	@Override
	protected int lineCount() {
		return cols;
	}

	@Override
	protected int lineLength() {
		return rows;
	}

	@Override
	public double get(int i, int j) {
		return getColumn(j).get(i);
	}

	@Override
	public void set(int i, int j, double value) {
		checkIndex(i,j);
		AVector v = unsafeGetVec(j);
		if (v==null) {
			if (value == 0.0)
                return;
			v = Vectorz.createSparseMutable(rows);
		} else if (v.isFullyMutable()) {
			v.set(i,value);
			return;
		} else {
			v = v.sparseClone();			
		}
		unsafeSetVec(j, v);
		v.set(i, value);
	}
	
	@Override
	public double unsafeGet(int row, int column) {
		return getColumn(column).unsafeGet(row);
	}

	@Override
	public void unsafeSet(int row, int column, double value) {
		AVector v=getColumn(column);
		if (v.isFullyMutable()) {
			v.unsafeSet(row,value);
		} else {
			v=v.mutable();
			replaceColumn(column,v);
			v.unsafeSet(row,value);
		}
	}
	
	@Override
	public void set(AMatrix a) {
		checkSameShape(a);
		for (int i=0; i<cols; i++) {
			setColumn(i,a.getColumn(i));
		}
	}
	
	@Override
	public void addAt(int i, int j, double d) {
		AVector v=getColumn(j);
		if (v.isFullyMutable()) {
			v.addAt(i, d);
		} else {
			v=v.mutable();
			v.addAt(i, d);
			replaceColumn(j,v);
		}
	}
	
	@Override
	public void addToArray(double[] targetData, int offset) {
        for (int i = 0; i < cols; ++i) {
			AVector v = unsafeGetVec(i);
			if (v != null) v.addToArray(targetData, offset+i, cols);
		}
	}
	
	private AVector ensureMutableColumn(int i) {
		AVector v = unsafeGetVec(i);
		if (v == null) {
			AVector nv=SparseIndexedVector.createLength(rows);
            unsafeSetVec(i, nv);
			return nv;
		}
		if (v.isFullyMutable()) return v;
		AVector mv=v.mutable();
		unsafeSetVec(i, mv);
		return mv;
	}
	
	@Override
	public AVector getColumn(int i) {
		if ((i<0)||(i>=cols)) throw new IndexOutOfBoundsException(ErrorMessages.invalidSlice(this, 1, i));
		AVector v = unsafeGetVec(i);
		if (v==null) return emptyColumn;
		return v;
	}
	
	@Override
	public AVector getColumnView(int i) {
		return ensureMutableColumn(i);
	}
	
	@Override
	public boolean isLowerTriangular() {
		int cc=columnCount();
		for (int i=1; i<cc; i++) {
			if (!getColumn(i).isRangeZero(0, i)) return false;
		}
		return true;
	}

	@Override
	public void swapColumns(int i, int j) {
		if (i == j)
			return;
		if ((i < 0) || (i >= cols))
			throw new IndexOutOfBoundsException(ErrorMessages.invalidSlice(this, 1, i));
		if ((j < 0) || (j >= cols))
			throw new IndexOutOfBoundsException(ErrorMessages.invalidSlice(this, 1, j));
		AVector a = unsafeGetVec(i);
		AVector b = unsafeGetVec(j);
		unsafeSetVec(i, b);
		unsafeSetVec(j, a);
	}
	
	@Override
	public void replaceColumn(int i, AVector vec) {
		if ((i<0)||(i>=cols))
            throw new IndexOutOfBoundsException(ErrorMessages.invalidSlice(this, 1, i));
		if (vec.length()!=rows)
            throw new IllegalArgumentException(ErrorMessages.incompatibleShape(vec));
		unsafeSetVec(i, vec);
	}
	
	@Override
	public void add(AMatrix a) {
		int count=columnCount();
		for (int i=0; i<count; i++) {
			AVector myVec=unsafeGetVec(i);
			AVector aVec=a.getColumn(i);
			if (myVec==null) {
				if (!aVec.isZero()) {
					unsafeSetVec(i,aVec.copy());
				}
			} else if (myVec.isMutable()) {
				myVec.add(aVec);
			} else {
				unsafeSetVec(i,myVec.addCopy(aVec));
			}
		}
	}
	
	@Override
	public void copyColumnTo(int i, double[] targetData, int offset) {
		getColumn(i).copyTo(targetData, offset);
	}
	
	@Override
	public void copyRowTo(int row, double[] targetData, int offset) {
        for (int i = 0; i < cols; ++i) {
            AVector e = unsafeGetVec(i);
            targetData[offset+i] = (e==null)? 0.0 : e.unsafeGet(row);
		}		
	}
	
	@Override
	public SparseRowMatrix getTransposeView() {
		return SparseRowMatrix.wrap(data, cols, rows);
	}
	
	@Override
	public AMatrix multiplyCopy(double a) {
		AVector[] ndata=new AVector[lineCount()];
		for (int i = 0; i < lineCount(); ++i) {
            AVector v = unsafeGetVec(i);
            if (v != null) {
                ndata[i] = v.multiplyCopy(a);
            }
		}
		return wrap(ndata,rows,cols);
	}
	
	@Override
	public AVector innerProduct(AVector a) {
		return transform(a);
	}
	
	@Override
	public AVector transform(AVector a) {
		Vector r=Vector.createLength(rows);
		for (int i=0; i<cols; i++) {
			getColumn(i).addMultipleToArray(a.get(i), 0, r.getArray(), 0, rows);
		}
		return r;
	}
	
	@Override
	public void applyOp(Op op) {
		for (int i=0; i<cols; i++) {
			AVector col=getColumn(i);
			if (col.isFullyMutable()) {
				col.applyOp(op);
			} else {
				col=col.mutable();
				col.applyOp(op);
				replaceColumn(i,col);
			}
		}
	}
	
	@Override
	public Matrix toMatrixTranspose() {
		Matrix m=Matrix.create(cols, rows);
        for (int i = 0; i < cols; ++i) {
			getColumn(i).copyTo(m.data, rows*i);
		}
		return m;
	}

	@Override
	public double[] toDoubleArray() {
		Matrix m=Matrix.create(rows, cols);
		for (int i=0; i<cols; i++) {
			AVector v = unsafeGetVec(i);
			if (v != null)
                m.getColumn(i).set(v);
		}
		return m.getArray();
	}
	
	@Override 
	public AMatrix transposeInnerProduct(AMatrix a) {
		return getTranspose().innerProduct(a);	
	}
	
	@Override
	public SparseColumnMatrix exactClone() {
		SparseColumnMatrix result= new SparseColumnMatrix(rows,cols);
        for (int i = 0; i < cols; ++i) {
			AVector col = unsafeGetVec(i);
			if (col != null)
			    result.replaceColumn(i, col.exactClone());
		}
		return result;
	}
	
	@Override
	public AMatrix clone() {
		if (this.elementCount() < SPARSE_ELEMENT_THRESHOLD)
            return super.clone();
		return exactClone();
	}
	
	@Override
	public AMatrix sparse() {
		return this;
	}
	
	@Override
	public void validate() {
		super.validate();
		for (int i=0; i<cols; i++) {
			if (getColumn(i).length()!=rows) throw new VectorzException("Invalid row count at column: "+i);
		}
	}
	
	@Override
	public boolean equals(AMatrix m) {
		if (m==this) return true;
		if (!isSameShape(m)) return false;
		for (int i=0; i<cols; i++) {
			AVector v=unsafeGetVec(i);
            AVector ov = m.getColumn(i);
			if (v==null) {
				if (!ov.isZero()) return false;
			} else {
				if (!v.equals(ov)) return false;
			}
		}
		return true;
	}

}
