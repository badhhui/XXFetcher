package org.starlab.index;


import org.ansj.lucene6.AnsjAnalyzer;
import org.apache.commons.io.FilenameUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.poi.POITextExtractor;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.tika.Tika;
import org.jetbrains.annotations.NotNull;
import org.starlab.Main;
import org.starlab.model.FieldTypes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class Index {
    private static final String INDEX_PATH = "C:\\Users\\hhui\\Documents\\xxFetcher\\data";
    public int createIndex(String docsPath) throws InterruptedException {
        // 检查参数合法性
        if (docsPath == null) {
            return -1;
        }

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            Main.append_JTA_Content_newline(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " : Document directory '" +docDir.toAbsolutePath()+ "' does not exist or is not readable, please check the path");
            Thread.sleep(3000);
            System.exit(1);
        }

        Date start = new Date();
        try {
            Main.append_JTA_Content_newline(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " : Indexing to directory '" + INDEX_PATH + "'...");
            // 获得索引路径的目录对象
            Directory dir = FSDirectory.open(Paths.get(INDEX_PATH));
            // 创建一个StandardAnalyzer分析器
//            Analyzer analyzer = new IKAnalyzer();
            Analyzer analyzer = new AnsjAnalyzer(AnsjAnalyzer.TYPE.index_ansj);
//            Analyzer analyzer = new StandardAnalyzer();
            // 使用StandardAnalyzer创建一个IndexWriterConfig
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            // 创建新索引，删除之前的索引
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

            // 可选：为了提高索引性能，如果你正在索引许多文档，请增加 RAM缓冲区。
            // 但如果这样做，请增加 JVM 的最大堆大小（例如 add -Xmx512m 或 -Xmx1g）
            // iwc.setRAMBufferSizeMB(256.0);

            IndexWriter writer = new IndexWriter(dir, iwc);
            indexDocs(writer, docDir);

            // 注意：如果想最大限度地提高搜索性能、
            // 可以选择在这里调用强制合并。 这可能是是一个开销非常高的操作
            // 所以一般只有在索引相对静态的情况下才使用（即 你已经完成了文档的添加）：
            // writer.forceMerge(1);

            writer.close();

            Date end = new Date();
            Main.append_JTA_Content_newline(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " : " + (end.getTime() - start.getTime()) + " total milliseconds");

        } catch (IOException e) {
            Main.append_JTA_Content_newline(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " : caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }
        return 0;
    }

    private static void indexDocs(final IndexWriter writer, Path path) throws IOException {
        // 传入的是一个目录
        if (Files.isDirectory(path)) {
            // 遍历目录的文件
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        // 对文件建立索引
                        indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
                    } catch (IOException ignore) {
                        throw new RuntimeException(ignore);// 对于不可读的文件不进行索引
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            // 传入的是文件，直接建立索引
            indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis());
        }
    }

    private static void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            // 创建一个Document对象
            Document doc = new Document();

            doc.add(new StringField("title", file.getFileName().toString(), Field.Store.YES));
            System.out.println(file.getFileName().toString());

            doc.add(new StringField("size", String.valueOf(Files.size(file)), Field.Store.YES));
            System.out.println(Files.size(file));

            // 为文件路径创建一个path字段，进行索引，并保存
            Field pathField = new StringField("path", file.toString(), Field.Store.YES);
            doc.add(pathField);
            String extName = new Tika().detect(file);
            if("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(extName)) {
                OPCPackage pkg = OPCPackage.open(file.toFile());
                doc.add(new Field("contents", extractText(pkg), FieldTypes.TYPE_TEXT_WITH_POSITIONS_OFFSETS_NOT_STORED));
            } else if (extName.startsWith("text")) {
                doc.add(new Field("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), FieldTypes.TYPE_TEXT_WITH_POSITIONS_OFFSETS_NOT_STORED));
            } else {
                doc.add(new StringField("contents", "", Field.Store.NO));
            }
            if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
                // 新建索引，直接添加文档
                Main.append_JTA_Content_newline(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " : adding " + file);
                writer.addDocument(doc);
            } else {
                // 使用文件路径来查找已经建立索引的旧文档，更新索引
                Main.append_JTA_Content_newline(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " : updating " + file);
                writer.updateDocument(new Term("path", file.toString()), doc);
            }
        } catch (Exception e) {
            System.out.println("process file error: " + file);
        }
    }

    public static String getIndexPath() {
        return INDEX_PATH;
    }

    public static String extractText(@NotNull OPCPackage pkg) throws Exception {
        POITextExtractor extractor = ExtractorFactory.createExtractor(pkg);
        if (extractor instanceof XSSFExcelExtractor excelExtractor) {
            excelExtractor.setFormulasNotResults(true);
            excelExtractor.setIncludeCellComments(true);
        }
        return extractor.getText();
    }
}
