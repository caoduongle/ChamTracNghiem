package service;

import model.OMRTemplate;
import org.opencv.core.Rect;

public class TemplateFactory {

    // 1. Mẫu Bộ GD&ĐT (Mã đề 4 số - Nền hồng) -> Mẫu GỐC đang chạy ổn định
    public static OMRTemplate getBoGD4SoTemplate() {
        OMRTemplate t = new OMRTemplate("Phiếu Bộ GD&ĐT (Mã đề 4 số)");

        // Tọa độ SBD và Mã Đề (Tạm tính cho khung 1200x1600)
        t.roiSBD = new Rect(800, 100, 180, 250);
        t.roiMaDe = new Rect(1000, 100, 120, 250);

        // PHẦN I
        t.roiPart1 = new Rect(100, 560, 1050, 290);
        t.p1ExpectedRows = 10;
        t.p1ColXs = new int[]{115, 375, 645, 915};
        t.p1ColWidth = 195;

        // PHẦN II
        t.roiPart2 = new Rect(100, 920, 1050, 150);
        t.p2ExpectedRows = 4;
        t.p2ColXs = new int[]{110, 220, 370, 480, 640, 745, 910, 1005};
        t.p2ColWidth = 98;

        // PHẦN III
        t.roiPart3 = new Rect(100, 1160, 1050, 345);
        t.p3ExpectedRows = 12;
        t.p3ColXs = new int[]{110, 282, 441, 610, 776, 950};
        t.p3ColWidth = 142;

        return t;
    }

    // 2. Mẫu Bộ GD&ĐT (Mã đề 3 số)
    public static OMRTemplate getBoGD3SoTemplate() {
        OMRTemplate t = getBoGD4SoTemplate(); // Kế thừa từ 4 số
        t.templateName = "Phiếu Bộ GD&ĐT (Mã đề 3 số)";
        // Mã đề 3 số thì khu vực mã đề sẽ hẹp hơn một chút
        t.roiMaDe = new Rect(1000, 100, 90, 250);
        return t;
    }

    // 3. Mẫu TNMaker 2025
    public static OMRTemplate getTNMakerTemplate() {
        OMRTemplate t = getBoGD4SoTemplate();
        t.templateName = "Phiếu TNMaker 2025";
        // Bạn có thể tinh chỉnh các chỉ số tọa độ tại đây nếu phiếu bị lệch
        return t;
    }

    // 4. Mẫu Quang Minh 2025
    public static OMRTemplate getQMTemplate() {
        OMRTemplate t = getBoGD4SoTemplate();
        t.templateName = "Phiếu Quang Minh 2025";
        // Bạn có thể tinh chỉnh các chỉ số tọa độ tại đây nếu phiếu bị lệch
        return t;
    }
}