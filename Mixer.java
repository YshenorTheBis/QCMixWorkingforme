package org.qcmix.mixer;

import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;

public class Mixer {

	/**
	 * For test purposes, allow to reproduce mixes.
	 * 0 means a random seed will be used.
	 */
	public static long seed = 0;

/*	private static void permuter(ArrayList<Object> list, int i, int j) {

		Object tmp;
		tmp = list.get(i);
		list.set(i, list.get(j));
		list.set(j, tmp);

	}
*/
	public static ArrayList<ArrayList<Object>> generateSheets(ArrayList<Object> initial, int n) {
		if (initial.size() == 0) {
			throw new IllegalArgumentException("La liste passée en paramètre est vide");
		}

//		if (n > initial.size()) {
//			throw new IllegalArgumentException(
//					"Le nombre de combinaisons diffÃ©rentes souhaitÃ© est incompatible avec la taille de la liste");
//		}
		ArrayList<ArrayList<Object>> result = new ArrayList<ArrayList<Object>>();

		ArrayList<Object> combinaison = null;
		Vector<Vector<Integer>> possible = new Vector<Vector<Integer>>();
		for(int a = 0; a < 4; a++)
			for(int b = 0; b < 4; b++)
				for(int c = 0; c < 4; c++)
					for(int d = 0; d < 4; d++)
						if(different(a,b, c, d)){
							Vector<Integer> v = new Vector<Integer>();
							v.add(a); v.add(b);v.add(c);v.add(d);
							possible.add(v);
						}
	
		
		
		
		Random random = null;
		if (seed == 0) {
			random = new Random();
		} else {
			random = new Random(seed);
		}
		for (int i = 0; i < n; i++) {
				combinaison = generate(initial, random, possible);

			result.add(combinaison);
		}
		return result;

	}

	static boolean different(int a, int b, int c, int d){
		if(a != b && a != c && a != d)
			if(b != c && b != d)
				if(c != d)
					return true;
		return false;
	}
	
	
	private static ArrayList<Object> generate(ArrayList<Object> initial, Random random, Vector<Vector<Integer>> possible) {

		
		ArrayList<Object> nouvelOrdre = new ArrayList<Object>();
		int r =Math.abs(random.nextInt())%possible.size();
		Vector<Integer> v = possible.remove(r);
		
		System.out.println(initial);
	
		for (int i = 0 ; i < initial.size(); i++) {

			int tmp = v.get(i);
			nouvelOrdre.add(initial.get(tmp));
			
		}
		return nouvelOrdre;
	}

}
