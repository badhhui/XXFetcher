package org.starlab.model;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;

public class FieldTypes {
    public static final FieldType TYPE_TEXT_WITH_POSITIONS_OFFSETS_STORED=new FieldType(TextField.TYPE_STORED);
    public static final FieldType TYPE_TEXT_WITH_POSITIONS_OFFSETS_NOT_STORED=new FieldType(TextField.TYPE_NOT_STORED);

    static {
        TYPE_TEXT_WITH_POSITIONS_OFFSETS_NOT_STORED.setStoreTermVectors(true);
        TYPE_TEXT_WITH_POSITIONS_OFFSETS_NOT_STORED.setStoreTermVectorOffsets(true);
        TYPE_TEXT_WITH_POSITIONS_OFFSETS_NOT_STORED.setStoreTermVectorPositions(true);
        TYPE_TEXT_WITH_POSITIONS_OFFSETS_STORED.setStoreTermVectors(true);
        TYPE_TEXT_WITH_POSITIONS_OFFSETS_STORED.setStoreTermVectorOffsets(true);
        TYPE_TEXT_WITH_POSITIONS_OFFSETS_STORED.setStoreTermVectorPositions(true);
    }

}
