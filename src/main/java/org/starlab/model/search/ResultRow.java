package org.starlab.model.search;

import org.apache.lucene.search.vectorhighlight.FieldPhraseList;

import java.util.List;

public class ResultRow {
    private String fileName;
    private String weight;
    private String path;
    private String size;

    private int docid;

    public ResultRow(String fileName, String weight, String path, String size, int docid) {
        this.fileName = fileName;
        this.weight = weight;
        this.path = path;
        this.size = size;
        this.docid = docid;
    }

    public String getFileName() {
        return fileName;
    }

    public String getWeight() {
        return weight;
    }

    public String getPath() {
        return path;
    }

    public String getSize() {
        return size;
    }

    public int getDocid() {
    	return docid;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public void setDocid(int docid) {
    	this.docid = docid;
    }

}
