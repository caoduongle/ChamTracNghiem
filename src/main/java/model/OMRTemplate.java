package model;

import org.opencv.core.Rect;

public class OMRTemplate {
    public String templateName;
    public int warpedWidth = 1200;
    public int warpedHeight = 1600;

    // Tọa độ Vùng SBD, Mã Đề
    public Rect roiSBD;
    public Rect roiMaDe;

    // Cấu hình Phần 1
    public Rect roiPart1;
    public int p1ExpectedRows;
    public int[] p1ColXs;
    public int p1ColWidth;

    // Cấu hình Phần 2
    public Rect roiPart2;
    public int p2ExpectedRows;
    public int[] p2ColXs;
    public int p2ColWidth;

    // Cấu hình Phần 3
    public Rect roiPart3;
    public int p3ExpectedRows;
    public int[] p3ColXs;
    public int p3ColWidth;

    public OMRTemplate(String name) {
        this.templateName = name;
    }
}