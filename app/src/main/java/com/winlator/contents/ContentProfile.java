package com.winlator.contents;

import java.util.List;

public class ContentProfile {
    public static final String CONTENT_TYPE_WINE = "WINE";
    public static final String CONTENT_TYPE_TURNIP = "TURNIP";
    public static final String CONTENT_TYPE_VIRGL = "VIRGL";
    public static final String CONTENT_TYPE_DXVK = "DXVK";
    public static final String CONTENT_TYPE_VKD3D = "VKD3D";

    public String type;
    public String verName;
    public int verCode;
    public String desc;
    public List<String> fileList;
}
