import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Logger;
import static java.lang.Math.toIntExact;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.uima.UIMAException;
import gov.nih.nlm.nls.stmt.Norm.LvgNorm;
import gov.nih.nlm.nls.stmt.Synonym.Synonyms;
import gov.nih.nlm.nls.stmt.Api.StmtApi;
import gov.nih.nlm.nls.lvg.Api.NormApi;
import gov.nih.nlm.nls.stmt.Api.StmtApiApp;

public class mapping_query {
	
	static Logger logger = Logger.getLogger(mapping_encoder.class.getName());
	
	static IndexReader term_reader = null;
	static IndexSearcher term_searcher = null;
	static Analyzer term_analyzer = null;
	static QueryParser term_parser = null;
	static Map<String, List<String>> synonymMap = null;
	static Map<String, List<String>> currentMap = null;
	static Map<String, List<String>> tfidfMap = null;
	
	// filepath for synonym file
	static String synonym_file = "/Users/Fyxstkala/Desktop/GitHub/term_mapping/synonyms.txt";
	// filepath for mapping dict file
	static String current_mapping_file = "/Users/Fyxstkala/Desktop/GitHub/term_mapping/combined_mapping_dict.txt";
 	
	public static void main(String argv[]) throws IOException, ParseException {
		String index = "/Users/Fyxstkala/Desktop/research/term_mapping/index";
		setIndexDir(index);
		
		//Vector<String> normed = getNorm("LuNg canCers");
		loadCurrentMappings(current_mapping_file);
		loadSynonym(synonym_file);
		compareMappings();
		compareMappingsWithSynonyms();
		//UMLSSynonymMappings();

		
	} 
	
	// get top n results for an entity
	public static List<UmlsQueryResult> getTopN( String entity, int n ) throws ParseException, IOException {
		Set<UmlsQueryResult> resultList = new HashSet<UmlsQueryResult>();
		
		int hitsPerPage = 50;
		String q = entity;
		if (q.isEmpty()) {
			List<UmlsQueryResult> list = new ArrayList<UmlsQueryResult>(resultList);
			return list;
		}
		
		Query query = null;
		synchronized (term_parser) {
			query = term_parser.parse(q);
		}
		TopDocs results = term_searcher.search(query, hitsPerPage);
		ScoreDoc[] hits = results.scoreDocs;
		
		int numTotalHits = (int) results.totalHits;
		int end = Math.min(hits.length, hitsPerPage);
		
		for (int i = 0; i < end; i++) {
			Document doc = term_searcher.doc(hits[i].doc);                
            String cui = doc.get( "cui" );
            String semantic = doc.get( "semantic" );
            String pt = doc.get( "preferTerm" );
            String term = doc.get( "term" );
            //Integer repeat = Integer.parseInt( doc.get( "repeat" ) );
            String source = doc.get( "source" );
            
            UmlsQueryResult result = new UmlsQueryResult();
            result.cui = cui;
            result.semantic = semantic;
            result.preferTerm = pt;
            result.term = term;
            //result.repeat = repeat;
            result.score = hits[i].score;
            result.source = source;
            
            resultList.add(result);
            
            if( resultList.size() >= n ) {
            	break;
            }
            
		}
		
        List<UmlsQueryResult> ret = new ArrayList<UmlsQueryResult>(resultList);

		
		// sort query results, higher the score, higher the repeat, shorter the word count, worst to best
        Collections.sort( ret, new Comparator< UmlsQueryResult >() {
        	public int compare(UmlsQueryResult o1, UmlsQueryResult o2) {
        		if( o2.score > o1.score ) {
                    return 1;
                } else if( o1.score > o2.score ) {
                    return -1;
                } 
                /*else if( o2.repeat > o1.repeat ) {
                    return 1;
                } else if( o2.repeat < o1.repeat ) { 
                    return -1;
                }
                */ 
                else if( o2.term.split(" ").length > o1.term.split(" ").length  ) {
                    return -1;
                } else if( o1.term.split(" ").length > o2.term.split(" ").length) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        
        /*
        for (UmlsQueryResult r : ret) {
        	System.out.println(r);
        } */
		
		return ret;
	} 
	
	// load synonym file, this is optional if stmt is used directly
	public static void loadSynonym (String filename) throws IOException {
		synonymMap = new HashMap<String, List<String>>();
		BufferedReader infile = new BufferedReader(new FileReader(filename));
		String line = "";
		
		while ((line = infile.readLine()) != null) {
			List<String> splitStr = Arrays.asList(StringUtils.split(line, '|')); 
			String termKey = splitStr.get(0);
			if (splitStr.size() >= 2) {
				synonymMap.put(termKey, splitStr.subList(1, splitStr.size()-1));
			} else {
				synonymMap.put(termKey, Collections.emptyList());
			}
		}
		infile.close();
	}
	
	// return a Vector<String> of the normalized term
	public static Vector<String> getNorm (String term) {
		NormApi api =  new NormApi("/Users/Fyxstkala/Desktop/stmt2015/data/Config/lvg.properties");
		StmtApi stmtapi = new StmtApiApp();
		Vector<String> results = LvgNorm.Normalize(term, api);
		//System.out.print(results);
		//Vector<String> results = stmtapi.GetSynonymSubsititution(term);
		//System.out.print(results);
		return results;
	}
	
	// load mapping_dict.csv
	public static void loadCurrentMappings (String filename) throws IOException {
		currentMap = new HashMap<String, List<String>>();
		BufferedReader br = new BufferedReader(new FileReader(filename));
		String line = "";
		
	    while((line=br.readLine())!=null){
	        List<String> str = Arrays.asList(line.split("[$]"));
	        String termKey = str.get(0);
	        List<String> termMappings = str.subList(1, str.size());
	        
	        currentMap.put(termKey, termMappings);
	    }
	    br.close();
	}
	
	public static void setIndexDir(String modelIndex) throws IOException {
		term_reader = DirectoryReader.open( FSDirectory.open( Paths.get( modelIndex ) ) );
	    term_searcher = new IndexSearcher( term_reader );
        CharArraySet stopword = new CharArraySet( new HashSet<String>(), false );
	    term_analyzer = new StandardAnalyzer(  stopword );
	    term_parser = new QueryParser( "term", term_analyzer );
	    ClassicSimilarity s = new ClassicSimilarity();
        term_searcher.setSimilarity( s );
	}
	
	// compare all terms from mapping dict with TFIDF mappings
	public static void compareMappings() throws IOException, ParseException {

	    int totalCount = 0;
	    int correctCount = 0;
	    	    
	    for (Map.Entry<String, List<String>> entry : currentMap.entrySet()) {
	    	String termKey = entry.getKey();
	    	List<String> termMappings = entry.getValue();
	        
			String entity = "term:" + termKey.replace("/", " ");
			//List<UmlsQueryResult> retList = new ArrayList<UmlsQueryResult>();
			//retList.addAll(getTopN( entity, 5 ));
			
			List<String> tfidfMapping = new ArrayList<String>();
			for (UmlsQueryResult q : getTopN(entity, 5)) {
				tfidfMapping.add(q.term);
			}
			
			if (!Collections.disjoint(tfidfMapping, termMappings)) {
				correctCount += 1;
			}
			totalCount += 1;
	    }
	    System.out.println(correctCount);
	    System.out.println(totalCount);
	}
	
	// compare all terms from mapping dict with TFIDF mappings, including synonyms mapping
	public static void compareMappingsWithSynonyms() throws IOException, ParseException {

	    int totalCount = 0;
	    int correctCount = 0;
	    	    
	    for (Map.Entry<String, List<String>> entry : currentMap.entrySet()) {
	    	String termKey = entry.getKey();
	    	List<String> termMappings = entry.getValue();
	        List<String> termSynonyms = synonymMap.get(termKey);
	        
			List<UmlsQueryResult> retList = new ArrayList<UmlsQueryResult>();
			String entity = "term:" + termKey.replace("/", " ");
			retList.addAll(getTopN( entity, 5 ));
			
			if (termSynonyms != null) {
				for (String syn : termSynonyms) {
					String query = "term:" + syn.replace("/", " ");
					retList.addAll(getTopN(query, 5));
				}
			}
			
			List<String> tfidfMapping = new ArrayList<String>();
			for (UmlsQueryResult q : retList) {
				tfidfMapping.add(q.term);
			}
			
			if (!Collections.disjoint(tfidfMapping, termMappings)) {
				correctCount += 1;
			}
			totalCount += 1;
	    }
	    System.out.println(correctCount);
	    System.out.println(totalCount);
	}
	
	/*
	public static void UMLSSynonymMappings () throws IOException, ParseException {
	    int totalCount = 0;
	    int correctCount = 0;
	    for (Map.Entry<String, List<String>> entry : synonymMap.entrySet()) {
	    	String termKey = entry.getKey();
	    	List<String> termSynonyms = entry.getValue();
	    	List<String> termMappings = currentMap.get(termKey);
	    	
	    	List<UmlsQueryResult> retList = new ArrayList<UmlsQueryResult>();
			String entity = "term:" + termKey.replace("/", " ");
			retList.addAll(getTopN( entity, 5 ));
			
			if (termSynonyms != null) {
				for (String syn : termSynonyms) {
					String query = "term:" + syn.replace("/", " ");
					retList.addAll(getTopN(query, 5));
				}
			}
			
			List<String> tfidfMapping = new ArrayList<String>();
			for (UmlsQueryResult q : retList) {
				tfidfMapping.add(q.term);
			}
			//System.out.println(termMappings);
			//System.out.println(tfidfMapping);
			
			if (!tfidfMapping.isEmpty() && termMappings != null) {
				if (!Collections.disjoint(tfidfMapping, termMappings)) {
					correctCount += 1;
				}
			}
			totalCount += 1;
	    }
	    System.out.println(correctCount);
	    System.out.println(totalCount);

	}*/


}
