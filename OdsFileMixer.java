package org.qcmix;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;
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
 * <li> Lire le fichier .ODS pour recenser les réponses de chaque questions du QCM. </li>
 * <li> Pour chaque question lue on va mélanger les réponses associées et produire plusieurs versions différentes. </li>
 * <li> On écrit au fur et Ã  mesure dans les différentes versions pour ne pas avoir Ã  reparcourir le fichier une seconde fois pour l'écriture. </li>
 * <li> AprÃ¨s avoir lu toutes les questions du QCM, on sauvegarde les différentes versions produites par le mélange. </li>
 * </ul>
 *
 * <p>
 * Pour paramétrer le nombre de versions du QCM que l'on produit, il faut modifier la variable static nb_copies.
 * </p>
 *
 * @author Simon et Dioulde
 */
public class OdsFileMixer {

	private static final int MAX_ROWS_IN_QCM = 1000;

	private static final int MAX_COL_IN_QCM = 3;

	//Liste des réponses de la question en cours de lecture
	private static ArrayList<Object> reponses_input;

	//Liste des versions du QCM que l'on produit
	private static ArrayList<Sheet> sheets;

	//Indice des questions
	private static int indice;

	//Boolean qui indique si l'on se trouve dans une question
	private static boolean inQuestion;

	//Nombre de copies différentes du QCM que l'on veut produire
	private static int nb_copies = 4;

	//Boolean qui indique si on se trouve dans la colonne 0 et non pas dans la 2
	private static int actualCol = 0;
	
	
	//TODO J'ajoute ces variables
	private static int ligne_Col1 = 0; // compteur de lignes du fichier ods 
	private static int ligne_Col2 = 0; // compteur de lignes du fichier ods 
	private static int ligne_Col3 = 0; // compteur de lignes du fichier ods 
	
	private static Vector<Integer> questionList = new Vector<>(MAX_ROWS_IN_QCM/4);
	
	/**
	 * <b> Fonction principale qui va lire, mélanger et produire les différentes versions du QCM </b>
	 *
	 * @param file : Fichier qui contient le QCM au format .ODS
	 * @param outputDirPath : Chemin du dossier de destination des QCMs
	 * @throws IOException
	 * @throws BadFormatException
	 */
	public static void readFile(File file, String outputDirPath) throws IOException, BadFormatException {
		reset_reponses_input();
		indice = 0;
		setInQuestion(false);

		setCol(0);
		
		Sheet sheet;

		// On charge le fichier, et on prend la premiÃ¨re feuille de calcul
		try {
			sheet = SpreadSheet.createFromFile(file).getSheet(0);
			checkSourceSheetFormat(sheet);
		}
		catch (NullPointerException | ClassCastException e) {
			throw new NullPointerException("Le fichier demandé n'est pas une feuille de calcul!\n\n" + Helper.getStackTrace(e));
		}

		// on crée 4 copies (par défaut)
		sheets = genereSheets(file, nb_copies);

		
		//nombre de ligne de la feuille de calcul
		int rowCount  = sheet.getRowCount();

		//Tant qu'on a pas tout parcouru le fichier
		for(int i = 0; i < MAX_COL_IN_QCM; i++){
			reponses_input = new ArrayList<Object>();
			while(ligne_actuelle() < rowCount && ligne_actuelle() < MAX_ROWS_IN_QCM){
				// on lit la valeur de la cellule à la colonne et ligne actuelle et à  la ligne actuelle
				String cell_text = sheet.getCellAt(actualCol, ligne_actuelle()).getTextValue();
					System.out.println(cell_text);
				// si on trouve un numero de question
				checkQuestion(cell_text, indice, inQuestion);
				// si on trouve une lettre de réponse
				checkReponse(sheet, cell_text, reponses_input, ligne_actuelle(), inQuestion);
				// si on trouve une cellule vide
				try {
					checkEmpty(cell_text, reponses_input, ligne_actuelle(), inQuestion);
				}
				catch (IllegalArgumentException e) {
					throw new BadFormatException("Le nombre de réponses possibles Ã  cette question est insuffisante : ligne ~ " + ligne_actuelle());
				}

				// increment ligne
				incrementCpt_ligne();
			}
			//increment colonne
			actualCol+= 2;
		}

		/*
		 * Crée un nouveau fichier et sauvegarde les changements Ã  faire aprÃ¨s
		 * avoir mélangé les questions
		 */
		System.out.println("Sauvegarde");
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
		//si c'est un numéro de question et que l'on est pas déjà  dans une question
		if(Helper.isNumeric(cell_text) && !inQuestion){
			//on entre dans une question
			setInQuestion(true);
			incIndice();
		}
	}

	/**
	 * Fonction qui gère la lecture d'une cellule réponse (lettre)
	 * @param sheet
	 * @param cell_text
	 * @param reponses_input
	 * @param cpt_ligne
	 * @param inQuestion
	 */
	public static void checkReponse(Sheet sheet, String cell_text, ArrayList<Object> reponses_input, int cpt_ligne, boolean inQuestion){
		//si c'est une lettre de réponse et que l'on est déjÃ  dans une question
		if(Helper.isLetter(cell_text) && inQuestion){
			//on ajoute à la liste des réponses de la question
			reponses_input.add(sheet.getCellAt(actualCol+1,cpt_ligne));

		}
	}

	/**
	 * Fonction qui gÃ¨re la lecture d'une cellule vide
	 * @param cell_text : la cellule qu'on lit
	 * @param reponses_input : la liste de réponses de la question
	 * @param cpt_ligne : ligne de la feuille de calcul
	 * @param inQuestion
	 */
	
	private static void checkEmpty(String cell_text, ArrayList<Object> reponses_input,int cpt_ligne, boolean inQuestion){
		//une cellule vide indique que l'on passe Ã  une question différente
		if(cell_text == "" && inQuestion){
			//on génère nb_copie de QCMs
			//TODO MODIFIER LA GENERATION POUR QU'ELLE PUISSE SE FAIRE SUR DEUX COLONNES
			ArrayList<ArrayList<Object>> reponses_output = genereMix(reponses_input,nb_copies);
			
			//on écrit le résultat du mixer dans les nb_copies versions du QCMs
			write(reponses_output,cpt_ligne);

			//on reset la liste des réponses pour la question suivante
			reset_reponses_input();

			setInQuestion(false);
		}
	}

	/**
	 * <b>Fonction qui, Ã  partir d'une liste de réponses de QCM (String), va faire nb_version mélanges différents.</b>
	 * @see Mixer
	 * @param list : liste des réponses d'une question
	 * @param nb_version : nombre de mélanges de réponses que l'on souhaite
	 * @return ArrayList<ArrayList<Object>> : liste de listes de réponses mélangées
	 */
	private static ArrayList<ArrayList<Object>> genereMix(ArrayList<Object> list,int nb_version){
		//si la liste de réponses n'est pas vide
		if(list.size()!=0){
			//on génÃ¨re nb_version mélanges différents
			return Mixer.generateSheets(list, nb_version);
		}
		return null;
	}

	/**
	 * <b> Fonction qui écrit sur les nb_copies versions les différents ordres de réponses d'une question. </b>
	 * @param resultat : Le résultat de genereMix()
	 * @param cpt_ligne : l'indice de la ligne oÃ¹ l'on se trouve dans la feuille de calcul
	 */
	private static void write(ArrayList<ArrayList<Object>> resultat,int cpt_ligne){
		if(resultat != null){
			for (int i=0;i<nb_copies;i++){
				//on écrit sur la i Ã¨me feuille de calcul que l'on a générée
				Sheet sheet = sheets.get(i);
				ArrayList<Object> reponses = resultat.get(i);
				for (int j=0;j<reponses.size();j++){
					sheet.getCellAt(actualCol+1,cpt_ligne-reponses.size()+j).setValue(reponses.get(j));
				}
			}
		}
	}

	/**
	 * <b> Fonction qui génÃ¨re nb_copie de la feuille de calcul originale </b>
	 * @param sheet : feuille de calcul originale
	 * @param nb : nombre de versions que l'on crée
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
	 * @param sheets : les nb_copies feuilles de calcul que l'on veut génerer
	 * @param nb : nb_copies
	 * @param outputDirPath : répertoire destination
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

			throw new SecurityException("Erreur: impossible de créer le répertoire cible. Permission non accordée."+se.getMessage()+"\n"+se.getStackTrace());
		}
	    try{
	        outputDir.mkdir();
	    }
	    catch(SecurityException se){

	    	throw new SecurityException("Erreur: impossible de créer le répertoire cible. Permission non accordée."+se.getMessage()+"\n"+se.getStackTrace());
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
	 * <b> Fonction qui incrémente la variable statique : l'indice des questions </b>
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
	//TODO J'ajoute ces fonctions
	private static void setCol(int b){
		actualCol = b;
	}
	
	private static void incrementCpt_ligne(){
		if(actualCol == 0)
			ligne_Col1++;
		else if( actualCol == 2)
			ligne_Col2++;
		else
			ligne_Col3++;
	}
	private static int ligne_actuelle(){
	if(actualCol == 0)
		return ligne_Col1;
	else if( actualCol == 2)
		return ligne_Col2;
	else
		return ligne_Col3;
	}
	private static void addCaseToVerifieQuestionIndice(String s1, String s2, String s3){
		questionList.add(Integer.parseInt(s1));
		if(!s2.equals(""))
			questionList.add(Integer.parseInt(s2));
		if(!s3.equals(""))
			questionList.add(Integer.parseInt(s3));
	}
	private static void verifieLettreSurLigne(boolean question, String cell1, String cell3, String cell5, int ligne) throws BadFormatException{
		if(! question){
			verifieLettre(cell1, ligne, 0);
			verifieLettre(cell3, ligne, 2);
			verifieLettre(cell5, ligne, 4);
		}

	}
	private static void verifieLettre(String cell,int ligne, int col) throws BadFormatException{
		if (Pattern.matches("[a-zA-Z]", cell)) {
			String text = "Une lettre a été trouvée au mauvais endroit:\n "
					+ "ligne: " + Integer.toString(ligne + 1) + " - colonne: "+ col + " -> \"" + cell + "\"";
			throw new BadFormatException(text);
		}
	}
	
	private static void verifieNombreSurLigne(boolean question, String cell1, String cell3, String cell5, int ligne, Sheet sheet) throws BadFormatException{
		if(question){
			verifieNombre(cell1, ligne, 0, sheet);
			verifieNombre(cell3, ligne, 2, sheet);
			verifieNombre(cell5, ligne, 4, sheet);
			
		}
		else
			addCaseToVerifieQuestionIndice(cell1, cell3, cell5);
		
	}
	private static void verifieNombre(String cell, int ligne, int col, Sheet sheet) throws BadFormatException{
		boolean isRedactionPart = ((sheet.getCellAt(col, ligne + 1).getTextValue().equals("" + (Integer.parseInt(cell)  + 1))) || sheet.getCellAt(col, ligne + 1).getTextValue().equals(""));
		if (! isRedactionPart) {
			String text = "Un chiffre a été trouvé au mauvais endroit:\n "
					+ "ligne: " + Integer.toString(ligne + 1) + " - colonne: 1" + " -> \"" + cell + "\"";
			throw new BadFormatException(text);
		}
	}
	private static void verifieListQuestion() throws BadFormatException{
		int indice = 1;
		boolean done = false;
		while(!questionList.isEmpty()){
			//System.out.println(questionList);
			for(int i = 0; i < questionList.size(); i++){
				if(questionList.get(i) == indice){
				//	System.out.println("removed "+indice);
					
					indice++;
					done = true;
					questionList.remove(i);
					break;
				}
			}
			if(!done){
				throw new BadFormatException("La question numéro "+indice+" n'est pas renseignée");
			}
			done = false;
		}
	}
	
	
	/**
	 * <b> Recrée une nouvelle liste pour la question suivante </b>
	 */
	private static void reset_reponses_input(){
		reponses_input = new ArrayList<Object>();
	}

	/**
	 * Vérifie que le format de la feuille de calcul est correct:
	 * <ul>
	 * <li>pas de texte sur la troiziÃ¨me colonne</li>
	 * <li>une ligne vide entre chaque question/réponses</li>
	 * <li>des réponses aprÃ¨s chaque questions</li>
	 * <li>pas de blanc entre les réponses</li>
	 * </ul>
	 * @param sheet
	 * @throws BadFormatException
	 */
	public static void checkSourceSheetFormat(Sheet sheet) throws BadFormatException {
		int cpt_ligne = 0; // compteur de lignes du fichier ods
		//int indice_question = 0;
		boolean question = false; // boolean qui vérifie si on est en cours de question

		String text = "Le fichier contient une cellule inattendue:\n";

		//nombre de ligne de la feuille de calcul
		int rowCount  = sheet.getRowCount();

		//Tant qu'on a pas tout parcouru le fichier
		while(cpt_ligne < rowCount && cpt_ligne < MAX_ROWS_IN_QCM){
			String cell_text1 = sheet.getCellAt(0, cpt_ligne).getTextValue();
			String cell_text3 = sheet.getCellAt(2, cpt_ligne).getTextValue();
			String cell_text5 = sheet.getCellAt(4, cpt_ligne).getTextValue();
		//	String cell_text6 = sheet.getCellAt(5, cpt_ligne).getTextValue();
		//	String cell_text7 = sheet.getCellAt(6, cpt_ligne).getTextValue();
			/**
			 *TODO 
			 * Problematique avec la méthode de QCM sur plusieurs collones, necessitée de relancer sur les collones 4 et 5 ce qui est lancé sur les colonnes 1 et 2
			 * 
			 * @author Yshenor
			 */
			/*
			//les cellules des colonnes 5, 6 et 7 sont testées pour qu'elles n'aient aucun contenu
			if (! cell_text6.equals("") || ! cell_text7.equals("")) {
				text += "Cellule interdite:\n";
				if (! cell_text6.equals("")) {
					text += "ligne: " + Integer.toString(cpt_ligne + 1) + " - colonne: 6" + " -> \"" + cell_text6 + "\"";
				}
				else if (! cell_text7.equals("")) {
					text += "ligne: " + Integer.toString(cpt_ligne + 1) + " - colonne: 7" + " -> \"" + cell_text7 + "\"";
				}
				throw new BadFormatException(text);
			}*/
			
			// on vérifie qu'il y a bien un espace entre chaque question/reponse et que les questions sont bien précédées d'un chiffre
			if (Pattern.matches("\\d+", cell_text1)) {
				
				verifieNombreSurLigne(question, cell_text1, cell_text3, cell_text5, cpt_ligne, sheet);
				/*else if (! question && (Integer.parseInt(cell_text1) != indice_question + 1) && ! isRedactionPart)
				{
					text += "Le chiffre de cette question n'est pas bon:\n "
							+ "ligne: " + Integer.toString(cpt_ligne + 1) + " - colonne: 1" + " -> \"" + cell_text1 + "\"";
					throw new BadFormatException(text);
				}*/
				question = true;
				//indice_question += 1;
			}
			// on vérifie qu'une réponse a bien lieu apres les questions et que les réponses sont bien précédées d'une lettre
			else if (Pattern.matches("[a-zA-Z]", cell_text1)) {
				verifieLettreSurLigne(question, cell_text1, cell_text3, cell_text5, cpt_ligne);
			}
			else if (cell_text1.equals("")&&cell_text3.equals("")&&cell_text5.equals("")) {
				question = false;
			}

			//increment
			cpt_ligne++;
			
			
		}
		
		verifieListQuestion();
	}

}
