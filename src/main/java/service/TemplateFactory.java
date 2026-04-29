package service;

import model.OMRTemplate;
import model.AnchorZone;
import model.RelativePart;
import org.opencv.core.Rect;

public class TemplateFactory {

    private static final int TARGET_W = 1200;
    private static final int TARGET_H = 1600;

    private static Rect zone(double rx, double ry, double rw, double rh) {
        return new Rect((int)(rx * TARGET_W), (int)(ry * TARGET_H), (int)(rw * TARGET_W), (int)(rh * TARGET_H));
    }

    // [ĐÃ SỬA LỖI]: Hàm part giờ nhận 4 số nguyên (int) để đếm ô vuông, và 4 số thập phân (double) để tính tỷ lệ
    private static RelativePart part(int oxIdx, int oyIdx, int dxIdx, int dyIdx,
                                     double offX, double offY, double w, double h) {
        return new RelativePart(oxIdx, oyIdx, dxIdx, dyIdx, offX, offY, w, h);
    }

    public static OMRTemplate getTemplate(String templateId) {
        if (templateId == null) templateId = "BGD4.1";
        switch (templateId.toUpperCase()) {
            // [ĐÃ THÊM]: Nhận diện mã BGD4.1 và gọi đúng hàm bạn vừa tạo
            case "BGD4.1":
                return getBoGD4_1SoTemplate();
            case "BGD3":
                return getBoGD3SoTemplate();
            case "QM":
                return getQMSoTemplate();
            case "TNMaker":
                return getTNMakerSoTemplate();
            case "BGD4":
            default:
                return getBoGD4SoTemplate();
        }
    }

    public static OMRTemplate getBoGD4SoTemplate() {
        OMRTemplate t = new OMRTemplate("Phiếu Bộ GD&ĐT");

        // --- HÀNH LANG QUÉT TÌM Ô VUÔNG (ANCHOR ZONES) ---
        t.anchorZones.add(new AnchorZone(zone(0.17, 0.67, 0.72, 0.02), true));
        t.anchorZones.add(new AnchorZone(zone(0.1, 0.94, 0.82, 0.03), true));
        t.anchorZones.add(new AnchorZone(zone(0.02, 0.96, 0.96, 0.03), true));

        t.anchorZones.add(new AnchorZone(zone(0.26, 0.33, 0.029, 0.35), false));
        t.anchorZones.add(new AnchorZone(zone(0.48, 0.33, 0.029, 0.35), false));
        t.anchorZones.add(new AnchorZone(zone(0.7, 0.33, 0.029, 0.35), false));
        t.anchorZones.add(new AnchorZone(zone(0.02, 0.70, 0.05, 0.7), false)); // Cột dọc mép trái
        t.anchorZones.add(new AnchorZone(zone(0.93, 0.70, 0.05, 0.7), false)); // Cột dọc mép phải
 // Cột dọc mép phải

        // ====================================================================
        // CẤU HÌNH KHUNG ĐÁP ÁN (Dùng Số Thứ Tự Ô Vuông X0, X1... Y0, Y1...)
        // ====================================================================

        // --- PHẦN 1 ---
        t.p1ExpectedRows = 10;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part1Boxes.add(part(0, 0, 2, 1,   0.22, 0.05, 0.5, 0.93));
        t.part1Boxes.add(part(1, 0, 3, 1,   0.37, 0.05, 0.6, 0.93));
        t.part1Boxes.add(part(3, 0, 5, 1,   0.16, 0.05, 0.6, 0.93));
        t.part1Boxes.add(part(4, 0, 6, 1,   0.37, 0.05, 0.5, 0.93));

        // Bạn có thể thêm cấu hình cho part2Boxes, part3Boxes ở đây bằng cách gọi t.part2Boxes.add(...)
        t.p2ExpectedRows = 4;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part2Boxes.add(part(0, 2, 2, 3,   0.22, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(0, 2, 2, 3,   0.45, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(1, 2, 3, 3,   0.37, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(1, 2, 3, 3,   0.66, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(3, 2, 5, 3,   0.16, 0.3, 0.28, 0.66));
        t.part2Boxes.add(part(3, 2, 5, 3,   0.45, 0.3, 0.28, 0.66));
        t.part2Boxes.add(part(4, 2, 6, 3,   0.37, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(4, 2, 6, 3,   0.6, 0.3, 0.27, 0.66));

        t.p3ExpectedRows = 12;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part3Boxes.add(part(0, 3, 1, 4,   0.3, 0.2, 0.7, 0.7));
        t.part3Boxes.add(part(1, 3, 2, 4,   0.1, 0.2, 0.9, 0.7));
        t.part3Boxes.add(part(2, 3, 3, 4,   0.1, 0.2, 0.9, 0.7));
        t.part3Boxes.add(part(3, 3, 4, 4,   0.1, 0.2, 0.9, 0.7));
        t.part3Boxes.add(part(4, 3, 5, 4,   0.1, 0.2, 0.9, 0.7));
        t.part3Boxes.add(part(5, 3, 6, 4,   0.1, 0.2, 0.7, 0.7));

        return t;
    }
    public static OMRTemplate getBoGD3SoTemplate() {
        OMRTemplate t = new OMRTemplate("Phiếu Bộ GD&ĐT");

        // --- HÀNH LANG QUÉT TÌM Ô VUÔNG (ANCHOR ZONES) ---

        t.anchorZones.add(new AnchorZone(zone(0.02, 0.96, 0.96, 0.03), true));

        t.anchorZones.add(new AnchorZone(zone(0.02, 0.30, 0.03, 0.8), false)); // Cột dọc mép trái
        t.anchorZones.add(new AnchorZone(zone(0.95, 0.30, 0.03, 0.8), false)); // Cột dọc mép phải
        // Cột dọc mép phải

        // ====================================================================
        // CẤU HÌNH KHUNG ĐÁP ÁN (Dùng Số Thứ Tự Ô Vuông X0, X1... Y0, Y1...)
        // ====================================================================

        // --- PHẦN 1 ---
        t.p1ExpectedRows = 10;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part1Boxes.add(part(0, 0, 1, 1,   0.07, 0.14, 0.17, 0.84));
        t.part1Boxes.add(part(0, 0, 1, 1,   0.3, 0.14, 0.17, 0.84));
        t.part1Boxes.add(part(0, 0, 1, 1,   0.54, 0.14, 0.17, 0.84));
        t.part1Boxes.add(part(0, 0, 1, 1,   0.78, 0.14, 0.17, 0.84));

        // Bạn có thể thêm cấu hình cho part2Boxes, part3Boxes ở đây bằng cách gọi t.part2Boxes.add(...)
        t.p2ExpectedRows = 4;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part2Boxes.add(part(0, 1, 0, 2,   0.06, 0.34, 0.08, 0.63));
        t.part2Boxes.add(part(0, 1, 0, 2,   0.14, 0.34, 0.08, 0.63));
        t.part2Boxes.add(part(0, 1, 0, 2,   0.28, 0.34, 0.08, 0.63));
        t.part2Boxes.add(part(0, 1, 0, 2,   0.36, 0.34, 0.08, 0.63));
        t.part2Boxes.add(part(0, 1, 0, 2,   0.5, 0.34, 0.08, 0.63));
        t.part2Boxes.add(part(0, 1, 0, 2,   0.58, 0.34, 0.08, 0.63));
        t.part2Boxes.add(part(0, 1, 0, 2,   0.73, 0.34, 0.08, 0.63));
        t.part2Boxes.add(part(0, 1, 0, 2,   0.81, 0.34, 0.08, 0.63));

        t.p3ExpectedRows = 12;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part3Boxes.add(part(0, 2, 1, 3,   0.06, 0.15, 0.12, 0.8));
        t.part3Boxes.add(part(0, 2, 1, 3,   0.21, 0.15, 0.12, 0.8));
        t.part3Boxes.add(part(0, 2, 1, 3,   0.37, 0.15, 0.12, 0.8));
        t.part3Boxes.add(part(0, 2, 1, 3,   0.52, 0.15, 0.12, 0.8));
        t.part3Boxes.add(part(0, 2, 1, 3,   0.67, 0.15, 0.12, 0.8));
        t.part3Boxes.add(part(0, 2, 1, 3,   0.82, 0.15, 0.12, 0.8));

        return t;
    }
    public static OMRTemplate getBoGD4_1SoTemplate() {
        OMRTemplate t = new OMRTemplate("Phiếu Bộ GD&ĐT");

        // --- HÀNH LANG QUÉT TÌM Ô VUÔNG (ANCHOR ZONES) ---
        t.anchorZones.add(new AnchorZone(zone(0.17, 0.67, 0.72, 0.02), true));
        t.anchorZones.add(new AnchorZone(zone(0.1, 0.94, 0.82, 0.03), true));
        t.anchorZones.add(new AnchorZone(zone(0.02, 0.96, 0.96, 0.03), true));

        t.anchorZones.add(new AnchorZone(zone(0.26, 0.33, 0.029, 0.35), false));
        t.anchorZones.add(new AnchorZone(zone(0.48, 0.33, 0.029, 0.35), false));
        t.anchorZones.add(new AnchorZone(zone(0.7, 0.33, 0.029, 0.35), false));
        t.anchorZones.add(new AnchorZone(zone(0.02, 0.70, 0.05, 0.7), false)); // Cột dọc mép trái
        t.anchorZones.add(new AnchorZone(zone(0.93, 0.70, 0.05, 0.7), false)); // Cột dọc mép phải
        // Cột dọc mép phải

        // ====================================================================
        // CẤU HÌNH KHUNG ĐÁP ÁN (Dùng Số Thứ Tự Ô Vuông X0, X1... Y0, Y1...)
        // ====================================================================

        // --- PHẦN 1 ---
        t.p1ExpectedRows = 10;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part1Boxes.add(part(0, 0, 2, 1,   0.22, 0.05, 0.5, 0.93));
        t.part1Boxes.add(part(1, 0, 3, 1,   0.37, 0.05, 0.6, 0.93));
        t.part1Boxes.add(part(3, 0, 5, 1,   0.16, 0.05, 0.6, 0.93));
        t.part1Boxes.add(part(4, 0, 6, 1,   0.37, 0.05, 0.5, 0.93));

        // Bạn có thể thêm cấu hình cho part2Boxes, part3Boxes ở đây bằng cách gọi t.part2Boxes.add(...)
        t.p2ExpectedRows = 4;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part2Boxes.add(part(0, 2, 2, 3,   0.22, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(0, 2, 2, 3,   0.45, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(1, 2, 3, 3,   0.37, 0.3, 0.29, 0.66));
        t.part2Boxes.add(part(1, 2, 3, 3,   0.66, 0.3, 0.29, 0.66));
        t.part2Boxes.add(part(3, 2, 5, 3,   0.16, 0.3, 0.28, 0.66));
        t.part2Boxes.add(part(3, 2, 5, 3,   0.45, 0.3, 0.28, 0.66));
        t.part2Boxes.add(part(4, 2, 6, 3,   0.37, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(4, 2, 6, 3,   0.6, 0.3, 0.27, 0.66));

        t.p3ExpectedRows = 12;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part3Boxes.add(part(0, 3, 1, 4,   0.3, 0.2, 0.7, 0.66));
        t.part3Boxes.add(part(1, 3, 2, 4,   0.1, 0.2, 0.9, 0.66));
        t.part3Boxes.add(part(2, 3, 3, 4,   0.1, 0.2, 0.9, 0.66));
        t.part3Boxes.add(part(3, 3, 4, 4,   0.1, 0.2, 0.9, 0.66));
        t.part3Boxes.add(part(4, 3, 5, 4,   0.1, 0.2, 0.9, 0.66));
        t.part3Boxes.add(part(5, 3, 6, 4,   0.1, 0.2, 0.7, 0.66));

        return t;
    }
    public static OMRTemplate getTNMakerSoTemplate() {
        OMRTemplate t = new OMRTemplate("Phiếu Bộ GD&ĐT");

        // --- HÀNH LANG QUÉT TÌM Ô VUÔNG (ANCHOR ZONES) ---
        t.anchorZones.add(new AnchorZone(zone(0.17, 0.67, 0.72, 0.02), true));
        t.anchorZones.add(new AnchorZone(zone(0.1, 0.94, 0.82, 0.03), true));
        t.anchorZones.add(new AnchorZone(zone(0.02, 0.96, 0.96, 0.03), true));

        t.anchorZones.add(new AnchorZone(zone(0.26, 0.33, 0.029, 0.35), false));
        t.anchorZones.add(new AnchorZone(zone(0.48, 0.33, 0.029, 0.35), false));
        t.anchorZones.add(new AnchorZone(zone(0.7, 0.33, 0.029, 0.35), false));
        t.anchorZones.add(new AnchorZone(zone(0.02, 0.70, 0.05, 0.7), false)); // Cột dọc mép trái
        t.anchorZones.add(new AnchorZone(zone(0.93, 0.70, 0.05, 0.7), false)); // Cột dọc mép phải
        // Cột dọc mép phải

        // ====================================================================
        // CẤU HÌNH KHUNG ĐÁP ÁN (Dùng Số Thứ Tự Ô Vuông X0, X1... Y0, Y1...)
        // ====================================================================

        // --- PHẦN 1 ---
        t.p1ExpectedRows = 10;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part1Boxes.add(part(0, 0, 2, 1,   0.22, 0.05, 0.5, 0.93));
        t.part1Boxes.add(part(1, 0, 3, 1,   0.37, 0.05, 0.6, 0.93));
        t.part1Boxes.add(part(3, 0, 5, 1,   0.16, 0.05, 0.6, 0.93));
        t.part1Boxes.add(part(4, 0, 6, 1,   0.37, 0.05, 0.5, 0.93));

        // Bạn có thể thêm cấu hình cho part2Boxes, part3Boxes ở đây bằng cách gọi t.part2Boxes.add(...)
        t.p2ExpectedRows = 4;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part2Boxes.add(part(0, 2, 2, 3,   0.22, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(0, 2, 2, 3,   0.45, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(1, 2, 3, 3,   0.37, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(1, 2, 3, 3,   0.66, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(3, 2, 5, 3,   0.16, 0.3, 0.28, 0.66));
        t.part2Boxes.add(part(3, 2, 5, 3,   0.45, 0.3, 0.28, 0.66));
        t.part2Boxes.add(part(4, 2, 6, 3,   0.37, 0.3, 0.27, 0.66));
        t.part2Boxes.add(part(4, 2, 6, 3,   0.6, 0.3, 0.27, 0.66));

        t.p3ExpectedRows = 12;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part3Boxes.add(part(0, 3, 1, 4,   0.3, 0.2, 0.7, 0.7));
        t.part3Boxes.add(part(1, 3, 2, 4,   0.1, 0.2, 0.9, 0.7));
        t.part3Boxes.add(part(2, 3, 3, 4,   0.1, 0.2, 0.9, 0.7));
        t.part3Boxes.add(part(3, 3, 4, 4,   0.1, 0.2, 0.9, 0.7));
        t.part3Boxes.add(part(4, 3, 5, 4,   0.1, 0.2, 0.9, 0.7));
        t.part3Boxes.add(part(5, 3, 6, 4,   0.1, 0.2, 0.9, 0.7));

        return t;
    }
    public static OMRTemplate getQMSoTemplate() {
        OMRTemplate t = new OMRTemplate("Phiếu Bộ GD&ĐT");

        // --- HÀNH LANG QUÉT TÌM Ô VUÔNG (ANCHOR ZONES) ---
        t.anchorZones.add(new AnchorZone(zone(0.17, 0.68, 0.72, 0.02), true));
        //t.anchorZones.add(new AnchorZone(zone(0.1, 0.94, 0.82, 0.03), true));
        t.anchorZones.add(new AnchorZone(zone(0.02, 0.96, 0.96, 0.03), true));

        t.anchorZones.add(new AnchorZone(zone(0.26, 0.3, 0.023, 0.42), false));
        t.anchorZones.add(new AnchorZone(zone(0.49, 0.3, 0.027, 0.42), false));
        t.anchorZones.add(new AnchorZone(zone(0.73, 0.3, 0.023, 0.42), false));
        t.anchorZones.add(new AnchorZone(zone(0.02, 0.94, 0.05, 0.2), false)); // Cột dọc mép trái
        t.anchorZones.add(new AnchorZone(zone(0.93, 0.94, 0.05, 0.2), false)); // Cột dọc mép phải
        // Cột dọc mép phải

        // ====================================================================
        // CẤU HÌNH KHUNG ĐÁP ÁN (Dùng Số Thứ Tự Ô Vuông X0, X1... Y0, Y1...)
        // ====================================================================

        // --- PHẦN 1 ---
        t.p1ExpectedRows = 10;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part1Boxes.add(part(0, 0, 2, 1,   0.15, 0.15, 0.8, 0.8));
        t.part1Boxes.add(part(2, 0, 4, 1,   0.18, 0.15, 0.8, 0.8));
        t.part1Boxes.add(part(4, 0, 6, 1,   0.16, 0.15, 0.8, 0.8));
        t.part1Boxes.add(part(6, 0, 8, 1,   0.18, 0.15, 0.8, 0.8));

        // Bạn có thể thêm cấu hình cho part2Boxes, part3Boxes ở đây bằng cách gọi t.part2Boxes.add(...)
        t.p2ExpectedRows = 4;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part2Boxes.add(part(0, 1, 2, 2,   0.16, 0.38, 0.4, 0.57));
        t.part2Boxes.add(part(0, 1, 2, 2,   0.55, 0.38, 0.4, 0.57));
        t.part2Boxes.add(part(2, 1, 4, 2,   0.17, 0.38, 0.4, 0.57));
        t.part2Boxes.add(part(2, 1, 4, 2,   0.56, 0.38, 0.4, 0.57));
        t.part2Boxes.add(part(4, 1, 6, 2,   0.16, 0.38, 0.4, 0.57));
        t.part2Boxes.add(part(4, 1, 6, 2,   0.55, 0.38, 0.4, 0.57));
        t.part2Boxes.add(part(6, 1, 8, 2,   0.12, 0.38, 0.4, 0.57));
        t.part2Boxes.add(part(6, 1, 8, 2,   0.57, 0.38, 0.4, 0.57));

        t.p3ExpectedRows = 12;
        // Ví dụ: Bám vào Ô X số 0 và số 4, Ô Y số 0 và số 1
        t.part3Boxes.add(part(0, 2, 1, 3,   0.25, 0.22, 0.7, 0.75));
        t.part3Boxes.add(part(1, 2, 3, 3,   0.25, 0.22, 0.73, 0.73));
        t.part3Boxes.add(part(3, 2, 4, 3,   0.25, 0.22, 0.73, 0.73));
        t.part3Boxes.add(part(4, 2, 5, 3,   0.25, 0.22, 0.73, 0.73));
        t.part3Boxes.add(part(5, 2, 7, 3,   0.25, 0.22, 0.73, 0.73));
        t.part3Boxes.add(part(7, 2, 8, 3,   0.25, 0.22, 0.73, 0.73));

        return t;
    }
}