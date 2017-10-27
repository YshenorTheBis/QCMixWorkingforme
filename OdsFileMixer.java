package org.qcmix;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.jopendocument.dom.spreadsheet.Sheet;
import org.jopendocument.dom.spreadsheet.SpreadSheet;
import org.qcmix.exceptions.BadFormatException;
import org.qcmix.mixer.Mixer;
import org.qcmix.post_processing.PostProcessing;
import org.qcmix.post_processing.PostProcessingOptions;
import org.qcmix.tools.Helper;

/**
 * <b>Classe qui s'occupe des fichiers de format .ODS</b>
 *
 * <p>
 * La classe se divise en 3 parties :
 * <ul>
 * <li> Ouvrir le fichier .ODS </li>
 * <li> Lire le fichier .ODS pour recenser les rÃ©ponses de chaque questions du QCM. </li>
 * <li> Pour chaque question lue on va mÃ©langer les rÃ©ponses associÃ©es et produire plusieurs versions diffÃ©rentes. </li>
 * <li> On Ã©crit au fur et Ã  mesure dans les diffÃ©rentes versions pour ne pas avoir Ã  reparcourir le fichier une seconde fois pour l'Ã©criture. </li>
 * <li> AprÃ¨s avoir lu toutes les questions du QCM, on sauvegarde les diffÃ©rentes versions produites par le mÃ©lange. </li>
 * </ul>
 *
 * <p>
 * Pour paramÃ©trer le nombre de versions du QCM que l'on produit, il faut modifier la variable static nb_copies.
 * </p>
 *
 * @author Simon et Dioulde
 */
public class OdsFileMixer {

	private static final int MAX_ROWS_IN_QCM = 1000;

	//Liste des rÃ©ponses de la question en cours de lecture
	private static ArrayList<Object> reponses_input;

	//Liste des versions du QCM que l'on produit
	private static ArrayList<Sheet> sheets;

	//Indice des questions
	private static int indice;

	//Boolean qui indique si l'on se trouve dans une question
	private static boolean inQuestion;

	//Nombre de copies diffÃ©rentes du QCM que l'on veut produire
	private static int nb_copies = 4;

	/**
	 * <b> Fonction principale qui va lire, mÃ©langer et produire les diffÃ©rentes versions du QCM </b>
	 *
	 * @param file : Fichier qui contient le QCM au format .ODS
	 * @param outputDirPath : Chemin du dossier de destination des QCMs
	 * @throws IOException
	 * @throws BadFormatException
	 */
	public static void readFile(File file, String outputDirPath) throws IOException, BadFormatException {
		reponses_input = new ArrayList<Object>();

		indice = 0;

		inQuestion = false;

		Sheet sheet;

		// On charge le fichier, et on prend la premiÃ¨re feuille de calcul
		try {
			sheet = SpreadSheet.createFromFile(file).getSheet(0);
			checkSourceSheetFormat(sheet);
		}
		catch (NullPointerException | ClassCastException e) {
			throw new NullPointerException("Le fichier demandÃ© n'est pas une feuille de calcul!\n\n" + Helper.getStackTrace(e));
		}

		// on crÃ©e 4 copies (par dÃ©faut)
		sheets = genereSheets(file, nb_copies);

		int cpt_ligne = 0; // compteur de lignes du fichier ods

		//nombre de ligne de la feuille de calcul
		int rowCount  = sheet.getRowCount();

		//Tant qu'on a pas tout parcouru le fichier
		while(cpt_ligne < rowCount && cpt_ligne < MAX_ROWS_IN_QCM){

			// on lit la valeur de la cellule Ã  la colonne 0 et Ã  la ligne cpt_ligne
			String cell_text = sheet.getCellAt(0, cpt_ligne).getTextValue();

			// si on trouve un numero de question
			checkQuestion(cell_text, indice, inQuestion);

			// si on trouve une lettre de rÃ©ponse
			checkReponse(sheet, cell_text, reponses_input, cpt_ligne, inQuestion);

			// si on trouve une cellule vide
			try {
				checkEmpty(cell_text, reponses_input, cpt_ligne, inQuestion);
			}
			catch (IllegalArgumentException e) {
				throw new BadFormatException("Le nombre de rÃ©ponses possibles Ã  cette question est insuffisante : ligne ~ " + cpt_ligne);
			}

			// increment
			cpt_ligne++;

		}

		/*
		 * CrÃ©e un nouveau fichier et sauvegarde les changements Ã  faire aprÃ¨s
		 * avoir mÃ©langÃ© les questions
		 */
		saveSheets(sheets, nb_copies, outputDirPath,
				file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf(File.separator) + 1));


	}

	/**
	 * Fonction qui gÃ¨re la lecture d'une cellule question (chiffre)
	 * @param cell_text : la cellule du tableur
	 * @param indice : indice de la question
	 * @param inQuestion
	 */
	public static void checkQuestion(String cell_text, int indice, boolean inQuestion){
		//si c'est un numÃ©ro de question et que l'on est pas dÃ©jÃ  dans une question
		if(Helper.isNumeric(cell_text) && !inQuestion){
			//si le numÃ©ro est consÃ©cutif Ã  celui de la question prÃ©cÃ©dente
			if(Integer.parseInt(cell_text)== indice + 1){
				//on notifie que l'on a trouvÃ© une question
				setInQuestion(true);
				incIndice();
			}
		}
	}

	/**
	 * Fonction qui gÃ¨re la lecture d'une cellule rÃ©ponse (lettre)
	 * @param sheet
	 * @param cell_text
	 * @param reponses_input
	 * @param cpt_ligne
	 * @param inQuestion
	 */
	public static void checkReponse(Sheet sheet, String cell_text, ArrayList<Object> reponses_input, int cpt_ligne, boolean inQuestion){
		//si c'est une lettre de rÃ©ponse et que l'on est dÃ©jÃ  dans une question
		if(Helper.isLetter(cell_text) && inQuestion){
			//on ajoute Ã  la liste des rÃ©ponses de la question
			reponses_input.add(sheet.getCellAt(1,cpt_ligne));

		}
	}

	/**
	 * Fonction qui gÃ¨re la lecture d'une cellule vide
	 * @param cell_text : la cellule qu'on lit
	 * @param reponses_input : la liste de rÃ©ponses de la question
	 * @param cpt_ligne : ligne de la feuille de calcul
	 * @param inQuestion
	 */
	private static void checkEmpty(String cell_text, ArrayList<Object> reponses_input,int cpt_ligne, boolean inQuestion){
		//une cellule vide indique que l'on passe Ã  une question diffÃ©rente
		if(cell_text == "" && inQuestion){
			//on gÃ©nÃ¨re nb_copie de QCMs
			ArrayList<ArrayList<Object>> reponses_output = genereMix(reponses_input,nb_copies);

			//on Ã©crit le rÃ©sultat du mixer dans les nb_copies versions du QCMs
			write(reponses_output,cpt_ligne);

			//on reset la liste des rÃ©ponses pour la question suivante
			reset_reponses_input();

			setInQuestion(false);
		}
	}

	/**
	 * <b>Fonction qui, Ã  partir d'une liste de rÃ©ponses de QCM (String), va faire nb_version mÃ©langes diffÃ©rents.</b>
	 * @see Mixer
	 * @param list : liste des rÃ©ponses d'une question
	 * @param nb_version : nombre de mÃ©langes de rÃ©ponses que l'on souhaite
	 * @return ArrayList<ArrayList<Object>> : liste de listes de rÃ©ponses mÃ©langÃ©es
	 */
	private static ArrayList<ArrayList<Object>> genereMix(ArrayList<Object> list,int nb_version){
		//si la liste de rÃ©ponses n'est pas vide
		if(list.size()!=0){
			//on gÃ©nÃ¨re nb_version mÃ©langes diffÃ©rents
			return Mixer.generateSheets(list, nb_version);
		}
		return null;
	}

	/**
	 * <b> Fonction qui Ã©crit sur les nb_copies versions les diffÃ©rents ordres de rÃ©ponses d'une question. </b>
	 * @param resultat : Le rÃ©sultat de genereMix()
	 * @param cpt_ligne : l'indice de la ligne oÃ¹ l'on se trouve dans la feuille de calcul
	 */
	private static void write(ArrayList<ArrayList<Object>> resultat,int cpt_ligne){
		if(resultat != null){
			for (int i=0;i<nb_copies;i++){
				//on Ã©crit sur la i Ã¨me feuille de calcul que l'on a gÃ©nÃ©rÃ©e
				Sheet sheet = sheets.get(i);
				ArrayList<Object> reponses = resultat.get(i);
				for (int j=0;j<reponses.size();j++){
					sheet.getCellAt(1,cpt_ligne-reponses.size()+j).setValue(reponses.get(j));
				}
			}
		}
	}

	/**
	 * <b> Fonction qui gÃ©nÃ¨re nb_copie de la feuille de calcul originale </b>
	 * @param sheet : feuille de calcul originale
	 * @param nb : nombre de versions que l'on crÃ©e
	 * @return ArrayList des nb feuilles de calcul
	 * @throws IOException
	 */
	private static ArrayList<Sheet> genereSheets(File file, int nb) throws IOException{
		ArrayList<Sheet> res = new ArrayList<Sheet>();
		for(int i=0;i<nb;i++){
			res.add(SpreadSheet.createFromFile(file).getSheet(0));
		}
		return res;
	}

	/**
	 * <b> Fonction qui sauvegarde les nb_copies feuilles de calcul </b>
	 * @param sheets : les nb_copies feuilles de calcul que l'on veut gÃ©nerer
	 * @param nb : nb_copies
	 * @param outputDirPath : rÃ©pertoire destination
	 * @param inputName : nom du fichier source
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static void saveSheets(ArrayList<Sheet> sheets, int nb, String outputDirPath, String inputName) throws FileNotFoundException, IOException{

		if (inputName.lastIndexOf('.') > 0) {
			inputName = inputName.substring(0, inputName.lastIndexOf('.'));
		}
		File outputDir = new File(outputDirPath + inputName);

		// if the directory does not exist, create it
		if (!outputDir.exists()) {
			System.out.println("creating directory: " + outputDir.getName());
		}
		try {
			outputDir.mkdir();
		} catch(SecurityException se) {

			throw new SecurityException("Erreur: impossible de crÃ©er le rÃ©pertoire cible. Permission non accordÃ©e."+se.getMessage()+"\n"+se.getStackTrace());
		}
	    try{
	        outputDir.mkdir();
	    }
	    catch(SecurityException se){

	    	throw new SecurityException("Erreur: impossible de crÃ©er le rÃ©pertoire cible. Permission non accordÃ©e."+se.getMessage()+"\n"+se.getStackTrace());
	    }

	    System.out.println("outputDir " + outputDir.getName() + "created");

		File outputFile;
		String name;
		File outputFileMaster;
		String nameMaster;
		for(int i=0;i<nb;i++){
			name = outputDir.getAbsolutePath() + '/' + inputName + '_' + (i+1) + ".ods";
			outputFile = new File(name);
			
			nameMaster = outputDir.getAbsolutePath() + '/' + inputName + '_' + (i+1) + "_master.ods";
			outputFileMaster = new File(nameMaster);

			sheets.get(i).getSpreadSheet().saveAs(outputFile);
			System.out.println("generate "+ outputFile.getName());
			
			sheets.get(i).getSpreadSheet().saveAs(outputFileMaster);
			System.out.println("generate "+ outputFileMaster.getName());
			
			PostProcessing postProcessing = new PostProcessing(name);
			postProcessing.process(PostProcessingOptions.REMOVE_ALL_COLORS);
			
			PostProcessing postProcessingMaster = new PostProcessing(nameMaster);
			postProcessingMaster.process(0);
		}
	}

	/**
	 * <b> Fonction qui incrÃ©mente la variable statique : l'indice des questions </b>
	 */
	private static void incIndice(){
		indice++;
	}

	/**
	 * Setter de inQuestion
	 * @param b : boolean a set
	 */
	private static void setInQuestion(boolean b){
		inQuestion = b;
	}

	/**
	 * <b> RecrÃ©e une nouvelle liste pour la question suivante </b>
	 */
	private static void reset_reponses_input(){
		reponses_input = new ArrayList<Object>();
	}

	/**
	 * VÃ©rifie que le format de la feuille de calcul est correct:
	 * <ul>
	 * <li>pas de texte sur la troiziÃ¨me colonne</li>
	 * <li>une ligne vide entre chaque question/rÃ©ponses</li>
	 * <li>des rÃ©ponses aprÃ¨s chaque questions</li>
	 * <li>pas de blanc entre les rÃ©ponses</li>
	 * </ul>
	 * @param sheet
	 * @throws BadFormatException
	 */
	public static void checkSourceSheetFormat(Sheet sheet) throws BadFormatException {
		int cpt_ligne = 0; // compteur de lignes du fichier ods
		int indice_question = 0;
		boolean question = false; // boolean qui vÃ©rifie si on est en cours de question

		String text = "Le fichier contient une cellule inattendue:\n";

		//nombre de ligne de la feuille de calcul
		int rowCount  = sheet.getRowCount();

		//Tant qu'on a pas tout parcouru le fichier
		while(cpt_ligne < rowCount && cpt_ligne < MAX_ROWS_IN_QCM){
			String cell_text1 = sheet.getCellAt(0, cpt_ligne).getTextValue();
			String cell_text4 = sheet.getCellAt(3, cpt_ligne).getTextValue();
			String cell_text5 = sheet.getCellAt(4, cpt_ligne).getTextValue();
			/*
			 *TODO 
			 * Problematique avec la méthode de QCM sur plusieurs collones, necessitée de relancer sur les collones 4 et 5 ce qui est lancé sur les colonnes 1 et 2
			 * 
			 * @Author Yshenor
			 */
			/*
			// les cellules des colonnes 3, 4 et 5 sont testÃ©es pour qu'elles n'aient aucun contenu
			if (! cell_text4.equals("") || ! cell_text5.equals("")) {
				text += "Cellule interdite:\n";
				if (! cell_text4.equals("")) {
					text += "ligne: " + Integer.toString(cpt_ligne + 1) + " - colonne: 4" + " -> \"" + cell_text4 + "\"";
				}
				else if (! cell_text5.equals("")) {
					text += "ligne: " + Integer.toString(cpt_ligne + 1) + " - colonne: 5" + " -> \"" + cell_text5 + "\"";
				}
				throw new BadFormatException(text);
			}
			*/
			// on vÃ©rifie qu'il y a bien un espace entre chaque question/reponse et que les questions sont bien prÃ©cÃ©dÃ©es d'un chiffre
			if (Pattern.matches("\\d+", cell_text1)) {
				boolean isRedactionPart = ((sheet.getCellAt(0, cpt_ligne + 1).getTextValue().equals("" + (Integer.parseInt(cell_text1)  + 1))) || sheet.getCellAt(0, cpt_ligne + 1).getTextValue().equals(""));
				if (question && ! isRedactionPart) {
					text += "Un chiffre a Ã©tÃ© trouvÃ© au mauvais endroit:\n "
							+ "ligne: " + Integer.toString(cpt_ligne + 1) + " - colonne: 1" + " -> \"" + cell_text1 + "\"";
					throw new BadFormatException(text);
				}
				else if (! question && (Integer.parseInt(cell_text1) != indice_question + 1) && ! isRedactionPart)
				{
					text += "Le chiffre de cette question n'est pas bon:\n "
							+ "ligne: " + Integer.toString(cpt_ligne + 1) + " - colonne: 1" + " -> \"" + cell_text1 + "\"";
					throw new BadFormatException(text);
				}
				question = true;
				indice_question += 1;
			}
			// on vÃ©rifie qu'une rÃ©ponse a bien lieu apres les questions et que les rÃ©ponses sont bien prÃ©cÃ©dÃ©es d'une lettre
			else if (Pattern.matches("[a-zA-Z]", cell_text1)) {
				if (! question) {
					text += "Une lettre a Ã©tÃ© trouvÃ©e au mauvais endroit:\n "
							+ "ligne: " + Integer.toString(cpt_ligne + 1) + " - colonne: 1" + " -> \"" + cell_text1 + "\"";
					throw new BadFormatException(text);
				}
			}
			else if (cell_text1.equals("")) {
				question = false;
			}

			//increment
			cpt_ligne++;
		}
	}

}
