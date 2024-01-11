package org.starlab;

import org.apache.commons.io.FilenameUtils;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.vectorhighlight.FieldPhraseList;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.checkerframework.checker.units.qual.C;
import org.starlab.index.Index;
import org.starlab.model.FieldTypes;
import org.starlab.model.search.ResultRow;
import org.starlab.search.Search;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.starlab.index.Index.extractText;

public class Main {
    private static final String WINDOW_NAME = "XXFetcher";
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 800;
    private static final float PANEL_WEIGHT_LEFT = 0.3f;
    private static final float PANEL_WEIGHT_RIGHT = 0.7f;

    private static final String[] COLUMN_NAMES = {"文件名", "权重", "路径", "文件大小", "DOC ID"};
    private static JFrame mainFrame;
    //    private static JList<String> list;
//    public static DefaultListModel<String> listModel;
    private static DefaultTableModel model;

    private static JTable table;

    private static JTextArea JTA_Content;

    private static final Map<Integer, List<FieldPhraseList.WeightedPhraseInfo.Toffs>> offsets = new HashMap<>();

    private static final List<Object> highlighterList = new LinkedList<>();
    private static int focusIndex = -1;

    private static int currStartOffset = 0, currEndOffset = 0;

    private static int initWindow() {
        mainFrame = new JFrame(WINDOW_NAME);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        mainFrame.setLayout(new GridBagLayout());

        GridBagConstraints MFConstraints = new GridBagConstraints();

        JPanel leftPanel = new JPanel();
        leftPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        JPopupMenu popupMenu = createJPopMenu();
        leftPanel.setComponentPopupMenu(popupMenu);

        MFConstraints.gridx = 0;
        MFConstraints.gridy = 0;
        MFConstraints.weightx = PANEL_WEIGHT_LEFT;
        MFConstraints.weighty = 1;
        MFConstraints.fill = GridBagConstraints.BOTH;
        mainFrame.add(leftPanel, MFConstraints);

        JPanel rightPanel = new JPanel();
        rightPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        rightPanel.setLayout(new GridBagLayout());
        GridBagConstraints RPConstraints = new GridBagConstraints();

        // 创建搜索面板
        JPanel searchPanel = new JPanel();
        JTextField searchField = new JTextField(30);
        JButton searchButton = new JButton("搜索");
        searchButton.addActionListener(e -> {
            String keyword = searchField.getText();
            System.out.println(keyword);
            new Thread(() -> {
                try {
                    append_JTA_Content_newline(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " : 开始搜索");
                    new Search().search(keyword);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).start();
        });
        searchPanel.add(searchField);
        searchPanel.add(searchButton);

        // 创建文件表格
        model = new DefaultTableModel(COLUMN_NAMES, 0){
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().removeColumn(table.getColumnModel().getColumn(4));
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) { // 该检查防止多次处理相同的事件
                    int selectedRow = table.getSelectedRow();
                    if (selectedRow >= 0) {
                        // 获取选中行的数据
                        String path = (String) model.getValueAt(selectedRow, 2);
                        // 在文本区域显示文件信息
                        try {
                            readFileAndDisplay(path, selectedRow);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                        // 高亮关键字
                        currStartOffset = offsets.get(Integer.valueOf((String) model.getValueAt(selectedRow, 4))).getFirst().getStartOffset();
                        currEndOffset = offsets.get(Integer.valueOf((String) model.getValueAt(selectedRow, 4))).getFirst().getEndOffset();
                        highlighterList.clear();
                        offsets.get(Integer.valueOf((String) model.getValueAt(selectedRow, 4))).forEach(offset -> {
                            highlightText(offset.getStartOffset(), offset.getEndOffset() - offset.getStartOffset(), -1);
                        });
                        focusIndex = 0;
                        highlightText(currStartOffset, currEndOffset - currStartOffset, new Color(184, 207, 229), focusIndex);
                    }
                }
            }
        });
        JScrollPane listScrollPane = new JScrollPane(table);
        table.setFillsViewportHeight(true);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new GridBagLayout());
        GridBagConstraints BPConstraints = new GridBagConstraints();

        // 创建控制台
        JPanel consolePanel = new JPanel();
        consolePanel.setLayout(new BorderLayout());
        JButton nextButton = new JButton("下一个");
        JButton prevButton = new JButton("上一个");
        nextButton.addActionListener(e -> {
            if (focusIndex < offsets.get(Integer.valueOf((String) model.getValueAt(table.getSelectedRow(), 4))).size() - 1) {
                // 取消当前蓝色高亮，设置为黄色
                highlightText(currStartOffset, currEndOffset - currStartOffset, focusIndex);
                // 下一个偏移
                focusIndex++;
                currStartOffset = offsets.get(Integer.valueOf((String) model.getValueAt(table.getSelectedRow(), 4))).get(focusIndex).getStartOffset();
                currEndOffset = offsets.get(Integer.valueOf((String) model.getValueAt(table.getSelectedRow(), 4))).get(focusIndex).getEndOffset();
                // 设置为蓝色高亮
                highlightText(currStartOffset, currEndOffset - currStartOffset, new Color(184, 207, 229), focusIndex);
                JTA_Content.setCaretPosition(currStartOffset);
            }
        });
        prevButton.addActionListener(e -> {
            if (focusIndex > 0) {
                highlightText(currStartOffset, currEndOffset - currStartOffset, focusIndex);
                focusIndex--;
                currStartOffset = offsets.get(Integer.valueOf((String) model.getValueAt(table.getSelectedRow(), 4))).get(focusIndex).getStartOffset();
                currEndOffset = offsets.get(Integer.valueOf((String) model.getValueAt(table.getSelectedRow(), 4))).get(focusIndex).getEndOffset();
                highlightText(currStartOffset, currEndOffset - currStartOffset, new Color(184, 207, 229), focusIndex);
                JTA_Content.setCaretPosition(currStartOffset);
            }
        });
        consolePanel.add(nextButton, BorderLayout.EAST);
        consolePanel.add(prevButton, BorderLayout.WEST);

        BPConstraints.gridx = 0;
        BPConstraints.gridy = 0;
        BPConstraints.weightx = 1;
        BPConstraints.weighty = 0.1;
//        BPConstraints.fill = GridBagConstraints.BOTH;
        bottomPanel.add(consolePanel, BPConstraints);

        // 创建内容展示区域
        JTA_Content = new JTextArea();
        JTA_Content.setFont(JTA_Content.getFont().deriveFont(16f));
        JTA_Content.setEditable(false);
        JTA_Content.setLineWrap(true);
        JTA_Content.setWrapStyleWord(true);
        // 设置 JTextArea 的插入符号，使其自动滚动到最底部
        DefaultCaret caret = (DefaultCaret) JTA_Content.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane contentScrollPane = new JScrollPane(JTA_Content);

        BPConstraints.gridy = 1;
        BPConstraints.weighty = 0.9;
        BPConstraints.fill = GridBagConstraints.BOTH;
        bottomPanel.add(contentScrollPane, BPConstraints);

        // 搜索框约束：占据 10% 的高度
        RPConstraints.gridx = 0;
        RPConstraints.gridy = 0;
//        RPConstraints.gridwidth = GridBagConstraints.REMAINDER;
        RPConstraints.weightx = 1;
        RPConstraints.weighty = 0.05;
        RPConstraints.fill = GridBagConstraints.BOTH;
        rightPanel.add(searchPanel, RPConstraints);

        // 文件列表约束：占据 45% 的高度
        RPConstraints.gridy = 1;
        RPConstraints.weighty = 0.3;
        rightPanel.add(listScrollPane, RPConstraints);

        // 内容展示区域约束：占据 45% 的高度
        RPConstraints.gridy = 2;
        RPConstraints.weighty = 0.6;
        rightPanel.add(bottomPanel, RPConstraints);


        MFConstraints.gridx = 1;
        MFConstraints.weightx = PANEL_WEIGHT_RIGHT;
        MFConstraints.weighty = 1;
        MFConstraints.fill = GridBagConstraints.BOTH;
        mainFrame.add(rightPanel, MFConstraints);

        mainFrame.setVisible(true);
        return 0;
    }

    private static JPopupMenu createJPopMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem JMI_CreateIndex = new JMenuItem("创建索引");
        JMI_CreateIndex.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.showOpenDialog(mainFrame);
            String path = fileChooser.getSelectedFile().getAbsolutePath();
            new Thread(() -> {
                try {
                    append_JTA_Content_newline(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " : 开始创建索引");
                    new Index().createIndex(path);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }).start();
        });
        JMenuItem JMI_UpdateIndex = new JMenuItem("更新索引");
        JMI_UpdateIndex.setEnabled(false);
        JMenuItem JMI_RemoveIndex = new JMenuItem("删除索引");
        JMI_RemoveIndex.setEnabled(false);
        popupMenu.add(JMI_CreateIndex);
        popupMenu.add(JMI_UpdateIndex);
        popupMenu.add(JMI_RemoveIndex);
        return popupMenu;
    }

    private static void readFileAndDisplay(String filePath, int selectedRow) throws Exception {
        clear_JTA_Content();
        File file = new File(filePath);
        if(FilenameUtils.getExtension(file.getName()).equals("docx")) {
            OPCPackage pkg = OPCPackage.open(file);
            append_JTA_Content_newline(extractText(pkg));
        } else {
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    append_JTA_Content_newline(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        int startOffset = offsets.get(Integer.valueOf((String) model.getValueAt(selectedRow, 4))).getFirst().getStartOffset();
        JTA_Content.setCaretPosition(startOffset);
    }

    private static void highlightText(int offset, int length, int index) {
        highlightText(offset, length, Color.YELLOW, index);
    }

    private static void highlightText(int offset, int length, Color color, int index) {
        Highlighter highlighter = JTA_Content.getHighlighter();
        if (index >= 0) {
            highlighter.removeHighlight(highlighterList.get(index));
            highlighterList.remove(index);
        }
        Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(color);
        try {
            Object h = highlighter.addHighlight(offset, offset + length, painter);
            if (index >= 0) {
                highlighterList.add(index, h);
            } else {
                highlighterList.add(h);
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    public static void addItem(ResultRow item) {
        addItems(new ResultRow[]{item});
    }

    public static void addItems(ResultRow[] items) {
        for (ResultRow item : items) {
            model.addRow(new String[]{item.getFileName(), item.getWeight(), item.getPath(), item.getSize(), String.valueOf(item.getDocid())});
        }
    }

    public static void clearTable() {
        model.setRowCount(0);
    }

    public static void append_JTA_Content_newline(String content) {
        append_JTA_Content(content + "\r\n");
    }

    public static void append_JTA_Content(String content) {
        JTA_Content.append(content);
    }

    public static void clear_JTA_Content() {
        JTA_Content.setText("");
    }

    public static void appendOffsets(int row, List<FieldPhraseList.WeightedPhraseInfo.Toffs> offsets) {
        Main.offsets.put(row, offsets);
    }

    public static void clearOffsets() {
        Main.offsets.clear();
    }

    public static Map<Integer, List<FieldPhraseList.WeightedPhraseInfo.Toffs>> getOffsets() {
    	return Main.offsets;
    }

    public static void main(String[] args) throws InterruptedException {
        initWindow();
    }
}