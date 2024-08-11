package com.winlator.contents;

import androidx.annotation.NonNull;

import java.util.List;

public class ContentProfile {
    public static final String MARK_TYPE = "type";
    public static final String MARK_VERSION_NAME = "versionName";
    public static final String MARK_VERSION_CODE = "versionCode";
    public static final String MARK_DESC = "description";
    public static final String MARK_FILE_LIST = "files";

    public enum ContentType {
        CONTENT_TYPE_WINE ("Wine"),
        CONTENT_TYPE_TURNIP ("Turnip"),
        CONTENT_TYPE_VIRGL ("VirGL"),
        CONTENT_TYPE_DXVK ("DXVK"),
        CONTENT_TYPE_VKD3D ("VKD3D");

        final String typeName;
        ContentType(String typeNmae) {
            this.typeName = typeNmae;
        }

        @NonNull
        @Override
        public String toString() {
            return typeName;
        }

        public static ContentType getTypeByName(String name) {
            for (ContentType type : ContentType.values())
                if (type.typeName.equals(name))
                    return type;
            return null;
        }
    }

    public ContentType type;
    public String verName;
    public int verCode;
    public String desc;
    public List<String> fileList;
}
