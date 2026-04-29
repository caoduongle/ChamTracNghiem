package model;

import java.util.ArrayList;
import java.util.List;

public class OMRTemplate {
    public String templateName;
    public List<AnchorZone> anchorZones = new ArrayList<>(); // Danh sách hành lang

    // Danh sách các khung (Boxes) chứa đáp án của từng phần
    public List<RelativePart> part1Boxes = new ArrayList<>();
    public int p1ExpectedRows = 10;

    public List<RelativePart> part2Boxes = new ArrayList<>();
    public int p2ExpectedRows = 4;

    public List<RelativePart> part3Boxes = new ArrayList<>();
    public int p3ExpectedRows = 12;

    public OMRTemplate(String name) {
        this.templateName = name;
    }
}