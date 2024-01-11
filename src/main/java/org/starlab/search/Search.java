package org.starlab.search;

import org.ansj.lucene6.AnsjAnalyzer;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.vectorhighlight.FastVectorHighlighter;
import org.apache.lucene.search.vectorhighlight.FieldPhraseList;
import org.apache.lucene.search.vectorhighlight.FieldQuery;
import org.apache.lucene.search.vectorhighlight.FieldTermStack;
import org.starlab.Main;
import org.starlab.index.Index;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.FSDirectory;
import org.starlab.model.search.ResultRow;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Search {
    public int search(String keyword) throws Exception {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(Index.getIndexPath())));
//        IndexSearcher searcher = new IndexSearcher(reader);
//        Analyzer analyzer = new IKAnalyzer();
//        Analyzer analyzer = new StandardAnalyzer();
        Analyzer analyzer = new AnsjAnalyzer(AnsjAnalyzer.TYPE.index_ansj);

        QueryParser parser = new QueryParser("contents", analyzer);
        if (keyword.isEmpty()) {
            return -1;
        }
        Query query = parser.parse(keyword);
        FastVectorHighlighter highlighter = new FastVectorHighlighter(true, true, null, null);
        FieldQuery fieldQuery = highlighter.getFieldQuery(query);
        IndexSearcher searcher = null;
        try {
            searcher = new IndexSearcher(reader);
            TopDocs docs = searcher.search(query, 10);
            Main.clearTable();
            for (ScoreDoc doc : docs.scoreDocs) {
                System.out.println(searcher.doc(doc.doc).toString());
                String title = searcher.doc(doc.doc).get("title");
                String path = searcher.doc(doc.doc).get("path");
                String size = searcher.doc(doc.doc).get("size");
                String weight = String.valueOf(doc.score);
                System.out.println(doc.doc);
                ResultRow resultRow = new ResultRow(title, weight, path, size, doc.doc);

                FieldTermStack fieldTermStack = new FieldTermStack(searcher.getIndexReader(), doc.doc, "contents", fieldQuery);
                FieldPhraseList fieldPhraseList = new FieldPhraseList(fieldTermStack, fieldQuery);
                Field field = fieldPhraseList.getClass().getDeclaredField("phraseList");
                field.setAccessible(true);
                LinkedList<FieldPhraseList.WeightedPhraseInfo> list = (LinkedList<FieldPhraseList.WeightedPhraseInfo>) field.get(fieldPhraseList);
                List<FieldPhraseList.WeightedPhraseInfo.Toffs> offsets = new ArrayList<>();
                for (FieldPhraseList.WeightedPhraseInfo weightedPhraseInfo : list) {
                    offsets.addAll(weightedPhraseInfo.getTermsOffsets());
                }
                Main.appendOffsets(doc.doc, offsets);
                Main.addItem(resultRow);
            }
        } finally {
            Main.append_JTA_Content_newline(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " : 搜索结束");
            reader.close();
        }
        return 0;
    }
}
