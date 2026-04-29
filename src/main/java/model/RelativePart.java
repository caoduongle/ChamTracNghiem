package model;

public class RelativePart {
    // 1. Xác định ô vuông bằng SỐ THỨ TỰ (0, 1, 2, 3...)
    public int originXIndex, originYIndex; // Chỉ số ô vuông Gốc
    public int destXIndex, destYIndex;     // Chỉ số ô vuông Đích

    // 2. Tỷ lệ co dãn khung so với khoảng cách giữa 2 ô vuông đó
    public double offsetXRatio, offsetYRatio;
    public double widthRatio, heightRatio;

    public RelativePart(int oxIdx, int oyIdx, int dxIdx, int dyIdx,
                        double offX, double offY, double w, double h) {
        this.originXIndex = oxIdx; this.originYIndex = oyIdx;
        this.destXIndex = dxIdx;   this.destYIndex = dyIdx;
        this.offsetXRatio = offX;  this.offsetYRatio = offY;
        this.widthRatio = w;       this.heightRatio = h;
    }
}