import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
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


public class mapping_encoder {
	
	static Logger logger = Logger.getLogger(mapping_encoder.class.getName());
	static int maxRestrictionLevel = 100;
	private static final int SCALE = 79;
	
	private static Set<String> sourceFilterMap = new HashSet<String> (
			Arrays.asList( "ICD9CM","ICD10CM" ) );
	static Map<String, String> cuiSemanticMap = null;
	static Map<String, String> cuiTuiMap = null; //
	static Map<String, Integer> sourceRankMap = null; // loadRankMap
	

	public static void main (String argv[]) {
		
		String umlsPath = "/Users/Fyxstkala/Desktop/research/term_mapping";
		String outPath = "/Users/Fyxstkala/Desktop/research/term_mapping";

		String mrrank = umlsPath + "/MRRANK.RRF";
		String mrsty = umlsPath + "/MRSTY.RRF";
		String mrconso = umlsPath + "/MRCONSO.RRF";
		String indexDir = outPath + "/index";
		
		sourceRankMap = loadRankMap(mrrank);
		cuiSemanticMap = loadCUISemanticMap(mrsty);
		
		try {
			Directory outdir = FSDirectory.open(Paths.get(indexDir));
			
	        StandardAnalyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			IndexWriter w = new IndexWriter(outdir, config);
			ClassicSimilarity s = new ClassicSimilarity();
			config.setSimilarity(s);
			
			Map<String, Vector<String>> uniqRecordMap = new HashMap<String, Vector<String>>();
			BufferedReader infile = new BufferedReader(new FileReader(mrconso));
			
			String line = "";
			String cui = "";
			String tuis = "";
			
			// PROGRESS
			int numEntries = countLines(mrconso) / SCALE;
			String div = new String(new char[SCALE]).replace("\0", "_");
			int lineCount = 0;
			int lastDot = 0;
			int nextDot = 0;
			System.out.println(div);
			// PROGRESS

			while ((line = infile.readLine()) != null) {
				// PROGRESS
				lineCount++;
				nextDot = lineCount / numEntries;
				if (nextDot > lastDot) {
					System.out.print('|');
					lastDot = nextDot;
				}
				// /PROGRESS

				String[] splitStr = StringUtils.split(line, '|');
				cui = splitStr[0];

				if (!uniqRecordMap.containsKey(cui)) {
					for (String key : uniqRecordMap.keySet()) {
						processUniqCui(uniqRecordMap.get(key),w);
						// logger.info( "cui=[" + cui + "]" );
					}
					uniqRecordMap.clear();
					uniqRecordMap.put(cui, new Vector<String>());
				}

				uniqRecordMap.get(cui).add(line);
			}
			// remaining if any
			
			for (String key : uniqRecordMap.keySet()) {
				processUniqCui(uniqRecordMap.get(key),w);
				uniqRecordMap.clear();
			}
			

			infile.close();
			w.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
	
	
	private static int processUniqCui(Vector<String> recordVec, IndexWriter writer) {
		String preferTerm = "";
		String cui = "";
		int preferRank = -1;
		Map<String, Integer> termRepeatMap = new HashMap<String, Integer>();
		Map<String, Set<String> > sourceMap = new HashMap<String, Set<String> >(); // conso: SOURCE [11], CODE [13]
		
		for (String record : recordVec) {
			String[] splitStr = record.split("\\|");
			// C0000005|ENG|P|L0000005|PF|S0007492|Y|A7755565||M0019694|D012711|MSH|PEN|D012711|(131)I-Macroaggregated
			// Albumin|0|N||
			cui = splitStr[0];
			String lang = splitStr[1];
			if (!lang.equals("ENG")) {
				// ignore none-english terms;
				continue;
			}
			
			String source = splitStr[11]; // vocab source

			// populate source map, use source filter
			if( sourceMap.containsKey( source ) ) {
				if( !splitStr[13].trim().isEmpty() ) {
					sourceMap.get(source).add( splitStr[13] ); // [13] is CODE
				}
			} else if( sourceFilterMap.contains( source ) && !splitStr[13].trim().isEmpty() ) {
				sourceMap.putIfAbsent( source, new HashSet<String>() );
				sourceMap.get(source).add( splitStr[13] );
			}
			
			//populate term repeat map, set prefer term and prefer rank
			String sourceKey = splitStr[11] + "|" + splitStr[12] + "|"
					+ splitStr[16] + "|"; //source, term type, and suppress
			String term = splitStr[14]; // string term
			int rank = 0;
			if (sourceRankMap.containsKey(sourceKey)) {
				rank = sourceRankMap.get(sourceKey);
			}
			if (rank > preferRank) {
				preferTerm = term;
				preferRank = rank;
			}
			if (term.isEmpty()) {
				continue;
			}
			if (!termRepeatMap.containsKey(term)) {
				termRepeatMap.put(term, 0);
			}
			termRepeatMap.put(term, termRepeatMap.get(term) + 1);
		}


		for (String record : recordVec) {
			String[] splitStr = record.split("\\|");

			cui = splitStr[0];
			String lang = splitStr[1];
			String source = splitStr[11];
			String term = splitStr[14];
			if (!lang.equals("ENG")) {
				continue;
			}

			int level = Integer.parseInt(splitStr[15]);
			if (level > maxRestrictionLevel) {
				// keep restrict level 0 and 1;
				continue;
			}
			if (term.isEmpty()) {
				continue;
			}

			if (termRepeatMap.containsKey(term) && sourceMap.containsKey(source)) {
				try {
					Document doc = new Document();
					doc.add(new StringField("cui", cui, Field.Store.YES));
					doc.add(new StringField("repeat", termRepeatMap.get(term).toString(),Field.Store.YES));
					doc.add(new StringField("semantic", cuiSemanticMap.get(cui), Field.Store.YES));
					doc.add(new StringField("preferTerm", preferTerm,Field.Store.YES));
					doc.add(new TextField("term", term, Field.Store.YES));
					doc.add(new StringField( "source", source, Field.Store.YES ));
					if (!sourceFilterMap.contains(source) || !sourceMap.containsKey(source)) {
						System.out.println(source);
					}
					writer.addDocument(doc);

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				termRepeatMap.remove(term);
			}
		}
		return 0;
	}
	
	/**
	 * load semantic types of each cui;
	 * 
	 * @author jwang16
	 * @param filename
	 *            whole path of MRSTY.RRF
	 * @return <CUI, semantic>
	 */
	private static Map<String, String> loadCUISemanticMap(String filename) {
		Map<String, String> cuiSemanticMap = new HashMap<String, String>();
		cuiTuiMap = new HashMap<String, String>();
		try {
			BufferedReader infile = new BufferedReader(new FileReader(filename));
			// C0000005|T116|A1.4.1.2.1.7|Amino Acid, Peptide, or
			// Protein|AT17648347||
			String line = "";
			while ((line = infile.readLine()) != null) {
				String[] splitStr = StringUtils.split(line, '|');
				String cui = splitStr[0];
				String tui = splitStr[1];
				String sem = splitStr[3];
				if (cuiSemanticMap.containsKey(cui)) {
					cuiSemanticMap
							.put(cui, cuiSemanticMap.get(cui) + "|" + sem);
					cuiTuiMap.put(cui, cuiTuiMap.get(cui) + "|" + tui);
				} else {
					cuiSemanticMap.put(cui, sem);
					cuiTuiMap.put(cui, tui);
				}
			}

			infile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		logger.info("load CUISemanticMap finished, size=["
				+ cuiSemanticMap.size() + "]");
		return cuiSemanticMap;
	}
	
	/**
	 * load ranking of each source;
	 * 
	 * @param filename
	 *            whole path of MRRANK.RRF
	 * @return <SourceKey, ranking>
	 */
	private static Map<String, Integer> loadRankMap(String filename) {
		Map<String, Integer> sourceRankMap = new HashMap<String, Integer>();
		try {
			BufferedReader infile = new BufferedReader(new FileReader(filename));
			// 0679|MTH|PN|N|
			String line = "";
			while ((line = infile.readLine()) != null) {
				String[] splitStr = StringUtils.split(line, '|');
				int rank = Integer.parseInt(splitStr[0]);
				String sourceKey = line.substring(line.indexOf("|") + 1);
				sourceRankMap.put(sourceKey, rank);
			}
			infile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		logger.info("load sourceRankMap finished. size=["
				+ sourceRankMap.size() + "]");
		return sourceRankMap;
	}
	
	public static int countLines(String filename) throws IOException {
		LineNumberReader lnr = new LineNumberReader(new FileReader(new File(
				filename)));
		try {
			lnr.skip(Long.MAX_VALUE);
			// Add 1 because line index starts at 0
			return lnr.getLineNumber() + 1;
		} finally {
			lnr.close();
		}
	}

}
