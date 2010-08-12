import edu.uci.ics.jung.graph.util.Pair;

import owl.core.structure.Atom;
import owl.core.structure.Pdb;
import owl.core.structure.PdbAsymUnit;
import owl.core.structure.graphs.AICGEdge;
import owl.core.structure.graphs.AICGraph;
import owl.core.util.MySQLConnection;


public class testAICGraph {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {


		PdbAsymUnit pdb = new PdbAsymUnit("2vjz",new MySQLConnection(),"pdbase");
		
		//Collection<Pdb> chains = pdb.getAllChains();
		String chain1 = "C";
		String chain2 = "D";
		
		Pdb chainA = pdb.getChain(chain1);
		Pdb chainB = pdb.getChain(chain2);
		
		System.out.println("Chain "+chain1+" # of obs res: "+chainA.getObsLength());
		System.out.println("Chain "+chain2+" # of obs res: "+chainB.getObsLength());
		
		
		AICGraph graph = chainA.getAICGraph(chainB, "ALL", 3.0);

		System.out.println("Graph # edges: "+graph.getEdgeCount());
		
		for (AICGEdge edge:graph.getEdges()) {
			Pair<Atom> pair = graph.getEndpoints(edge);
			System.out.println(pair.getFirst()+" - "+pair.getSecond()+
					" ("+pair.getFirst().getParentResSerial()+" - "+pair.getSecond().getParentResSerial()+") "+
					" : "+String.format("%5.1f",edge.getDistance()));
		}
		
	}

}
