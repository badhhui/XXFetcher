/*******************************************************************************
 * Copyright (c) 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package org.starlab.model.search;

import com.google.common.io.Closeables;
import org.ansj.library.DicLibrary;
import org.ansj.lucene6.AnsjAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.vectorhighlight.FastVectorHighlighter;
import org.apache.lucene.search.vectorhighlight.FieldPhraseList;
import org.apache.lucene.search.vectorhighlight.FieldPhraseList.WeightedPhraseInfo;
import org.apache.lucene.search.vectorhighlight.FieldQuery;
import org.apache.lucene.search.vectorhighlight.FieldTermStack;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.poi.POITextExtractor;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.openxml4j.opc.PackageAccess;
import org.starlab.model.FieldTypes;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;

/**
 * @author Tran Nam Quang
 */
public final class HighlightServiceTest {
	@Test
	public void testChinesePhraseHighlighter() throws Exception {
		// Create index
		Directory directory = new RAMDirectory();
		//Analyzer analyzer = new StandardAnalyzer( CharArraySet.EMPTY_SET );
		Analyzer analyzer = new AnsjAnalyzer(AnsjAnalyzer.TYPE.index_ansj);
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
		// 创建新索引，删除之前的索引
		iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
		IndexWriter writer = new IndexWriter(directory, iwc);
		Document doc = new Document();
		DicLibrary.insert(DicLibrary.DEFAULT, "交通安全", "ansj", 2000);
		DicLibrary.insert(DicLibrary.DEFAULT, "交通", "ansj", 2000);
		DicLibrary.insert(DicLibrary.DEFAULT, "安全", "ansj", 2000);
//		String content="注意交通安全出行：不强行上下车，做到先下后上，候车要排队，按秩序上车；下车后要等车辆开走后再行走，如要穿越马路，一定要确保安全的情况下穿行；交通信号灯的正确使用，什么事交通安全出行交通信号灯的正确使用，什么事交通安全出行";
		File file = new File("D:\\test\\asa.docx");
		OPCPackage pkg = OPCPackage.open(file);
		String contents = extractText(pkg);
		System.out.println(contents);
		doc.add(new Field("content", contents, FieldTypes.TYPE_TEXT_WITH_POSITIONS_OFFSETS_NOT_STORED));
		writer.addDocument(doc);
		writer.close();

		Thread.sleep(5000);

		// Perform phrase search
		QueryParser queryParser = new QueryParser("content", analyzer);
		Query query = queryParser.parse("content:\"交通安全出行\"");
		FastVectorHighlighter highlighter = new FastVectorHighlighter(true, true, null, null);
		FieldQuery fieldQuery = highlighter.getFieldQuery(query);
		IndexSearcher searcher = null;

		searcher = new IndexSearcher(DirectoryReader.open(directory));
		TopDocs docs = searcher.search(query, 10);
		assertEquals(1, docs.scoreDocs.length);
		int docId = docs.scoreDocs[0].doc;

		// Get phrase highlighting offsets
		FieldTermStack fieldTermStack = new FieldTermStack(searcher.getIndexReader(), docId, "content", fieldQuery);
		FieldPhraseList fieldPhraseList = new FieldPhraseList( fieldTermStack, fieldQuery );
		java.lang.reflect.Field field = fieldPhraseList.getClass().getDeclaredField("phraseList");
		field.setAccessible(true);
		@SuppressWarnings("unchecked") LinkedList<WeightedPhraseInfo> list = (LinkedList<WeightedPhraseInfo>) field.get(fieldPhraseList);
		System.out.println(list.get(0).getStartOffset());
		System.out.println(list.get(0).getEndOffset());
	}

	private static String extractText(@NotNull OPCPackage pkg) throws Exception {
		POITextExtractor extractor = ExtractorFactory.createExtractor(pkg);
		if (extractor instanceof XSSFExcelExtractor excelExtractor) {
            excelExtractor.setFormulasNotResults(true);
			excelExtractor.setIncludeCellComments(true);
		}
        return extractor.getText();
	}
}
