package model;

import org.opencv.core.Rect;

public class AnchorZone {
    public Rect region;    // Vùng hành lang (Zone)
    public boolean isX;    // true: tìm Neo ngang (X), false: tìm Neo dọc (Y)


    //drawCoordinateRuler(warped);
    public AnchorZone(Rect region, boolean isX) {
        this.region = region;
        this.isX = isX;
    }
}