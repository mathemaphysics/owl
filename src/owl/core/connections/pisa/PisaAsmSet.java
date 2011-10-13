package owl.core.connections.pisa;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PisaAsmSet implements Iterable<PisaAssembly> {

	private List<PisaAssembly> list;
	
	public PisaAsmSet() {
		list = new ArrayList<PisaAssembly>();
	}
	
	public PisaAssembly get(int i) {
		return list.get(i);
	}
	
	public boolean add(PisaAssembly pisaAssembly) {
		return list.add(pisaAssembly);
	}
	
	@Override
	public Iterator<PisaAssembly> iterator() {
		return list.iterator();
	}

}
