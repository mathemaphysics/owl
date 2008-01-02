import tools.MySQLConnection;

import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public class listInfoGain {

	/**
	 * "Hello World" for entropy calculations given a nbhoodstring 
	 * 
	 * @author lappe
	 */
	
	static String user = "lappe"	; // change user name!!
	static MySQLConnection conn;
	
	public static void main(String[] args) {
		double entropy = 0.0, newentropy=0.0, gain, mingain=99999999.9; 
		String nbhood = "", front, middle, tail, newhood="";
		if (args.length<1){
			System.err.println("The starting neighborhood-string needs to be given .... i.e. %K%D%L%I%x%D%C%");
			System.exit(1);
		}		
		nbhood = args[0];
		
		conn = new MySQLConnection("white",user,"nieve","pdb_reps_graph_4_2"); 
		int l = (int)((nbhood.length()-1)/2); 
		int N = 1; 
			System.out.println("ListInfoGain");
			System.out.print("0 - (%x%)("+l+") "); 
			entropy = getEntropy( "%x%"); 
			System.out.println( entropy + " bits."); 
			System.out.println("Symbols in nbhood :"+l); 
			
			for (int i = 0; i<l; i++) {
				front = nbhood.substring(0,i*2); 
				middle = nbhood.substring(i*2, i*2+2); 
				tail = nbhood.substring(i*2+2);
				System.out.print((i+1)+" - "+front+"("+middle+")"+tail);
				
				if (middle.equals("%x")) { // switch from N to C of X  
					N = -1; 
					newhood = "%x%"; 
				} else  {
				   if (N < 0) // we are in the C-terminal section 
				       newhood = "%x"+middle+"%"; 
				   else // N terminal (before X) 
					   newhood = middle+"%x%";
				} // end if    
				System.out.print( " -> " + newhood); 
				newentropy = getEntropy( newhood); 
				gain = newentropy-entropy; 
				System.out.println( " : "+newentropy +"bits, gain="+gain);
				
			} // next symbol in nbhood; 
			
		
	 
		System.out.println("fin."); 
	}	

		
	
	public static double getEntropy( String nbs) {
		int total = 0, num; 
		String sql, res; 
		Statement stmt;  
		ResultSet rsst;
		double p, psum=0.0, logp, plogp, plogpsum=0.0; 
		
		try {
			sql = "select count(*) from single_model_node where n like '"+nbs+"';";
			// System.out.println( sql); 
			stmt = conn.createStatement();
			rsst = stmt.executeQuery(sql);
			if (rsst.next()) total = rsst.getInt( 1); 
			rsst.close(); 
			stmt.close(); 
			
			sql = "select res, count(*) as t, count(*)/"+total+" as p from single_model_node where n like '"+nbs+"' group by res order by res;";
			stmt = conn.createStatement();
			rsst = stmt.executeQuery(sql);
			// System.out.println("res : total t : fraction p : log2(p) : -p*log2(p)");
			while (rsst.next()) {				
				res = rsst.getString(1); // 1st column -- res
				num = rsst.getInt(2); // 2nd column -- num
				p = rsst.getDouble(3); // 3rd: fraction p 
				// System.out.print(res+"   : "+num+ " : " + p);
				logp = Math.log(p)/Math.log(2.0); // to basis 2 for info in bits 
				// System.out.print(" : " + logp); 
				plogp = -1.0 * p * logp;  
				// System.out.print(" : " + plogp);
				plogpsum += plogp;  
				psum += p; 
				// System.out.println("");
			}
			// System.out.println("Sum :"+total+"       : "+psum+"       : "+plogpsum);
			rsst.close(); 
			stmt.close(); 
			
		} catch (SQLException e) {
			e.printStackTrace();
			System.err.println("SQLException: " + e.getMessage());
			System.err.println("SQLState:     " + e.getSQLState());
		}
		return plogpsum; 
	} // end of getEntropy 

}