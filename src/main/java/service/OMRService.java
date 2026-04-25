package service;

import model.ExamConfig;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OMRService {

    static {
        OpenCV.loadLocally();
    }

    /**
     * LUỒNG ĐIỀU PHỐI CHÍNH (PRODUCTION MODE)
     * Đã đồng bộ 100% với ExamConfig của Team N7
     */
    public static Map<String, String> processExam(String imagePath, ExamConfig config) {
        Mat src = Imgcodecs.imread(imagePath);
        if (src.empty()) return null;

        String warpedPath = imagePath.replace(".jpg", "_processed.jpg");
        List<Rect> cornerMarks = findCornerMarks(src);
        if (cornerMarks.size() < 4) return null;
        Mat warped = warpPerspective(src, cornerMarks);

        Mat gray = new Mat();
        Imgproc.cvtColor(warped, gray, Imgproc.COLOR_BGR2GRAY);
        Mat thresh = new Mat();
        Imgproc.threshold(gray, thresh, 155, 255, Imgproc.THRESH_BINARY_INV);

        Map<String, String> results = new LinkedHashMap<>();

        // ---------------------------------------------------------------------
        // LẤY SỐ LƯỢNG CÂU HỎI TỪ CẤU HÌNH (ExamConfig)
        // Nếu chạy Test truyền null thì mặc định max khung (40-8-6)
        // ---------------------------------------------------------------------
        int p1Count = (config != null) ? config.getNumPart1() : 40;
        int p2Count = (config != null) ? config.getNumPart2() : 8;
        int p3Count = (config != null) ? config.getNumPart3() : 6;

        // --- 1. SỐ BÁO DANH & MÃ ĐỀ (Luôn quét cố định) ---
        results.put("STUDENT_ID", autoColumnScan(thresh, warped, new Rect(810, 135, 205, 330), 8, 10, "SBD", new Scalar(0, 255, 255)));
        results.put("EXAM_CODE", autoColumnScan(thresh, warped, new Rect(1043, 135, 105, 325), 4, 10, "MaDe", new Scalar(200, 0, 200)));

        // --- 2. PHẦN I (Tính toán số lượng khung và số câu cần quét) ---
        int[] p1_X = {115, 375, 645, 915};
        int p1Boxes = (int) Math.ceil(p1Count / 10.0); // Ví dụ 25 câu -> Quét 3 khung
        for (int i = 0; i < p1Boxes; i++) {
            Rect colBox = new Rect(p1_X[i], 580, 195, 269);
            int questionsInThisBox = Math.min(10, p1Count - (i * 10)); // Khung cuối có thể < 10 câu
            results.putAll(autoPart1Scan(thresh, warped, colBox, i * 10, questionsInThisBox));
        }

        // --- 3. PHẦN II (1 Câu = 1 Khung -> Quét đúng số lượng câu) ---
        int[] p2_X = {110, 220, 370, 480, 640, 745, 910, 1005};
        int p2Boxes = Math.min(8, p2Count);
        for (int i = 0; i < p2Boxes; i++) {
            Rect tableBox = new Rect(p2_X[i], 951, 98, 115);
            results.putAll(autoPart2Scan(thresh, warped, tableBox, i));
        }

        // --- 4. PHẦN III (1 Câu = 1 Khung -> Quét đúng số lượng câu) ---
        int[] p3_X = {110, 282, 441, 610, 776, 950};
        int p3Boxes = Math.min(6, p3Count);
        for (int i = 0; i < p3Boxes; i++) {
            Rect qBox = new Rect(p3_X[i], 1175, 142, 325);
            String val = scanSmartInterpolationPart3(thresh, warped, qBox);
            results.put("P3_Câu_" + (i + 1), val);
        }

        Imgcodecs.imwrite(warpedPath, warped);
        return results;
    }

    /**
     * HÀM DÀNH CHO PHẦN 3: ĐỒNG BỘ CHÉO VÀ NỘI SUY TEAM N7
     */
    private static String scanSmartInterpolationPart3(Mat thresh, Mat warped, Rect box) {
        int expectedCols = 4;
        int expectedRows = 12;
        Imgproc.rectangle(warped, box, new Scalar(255, 0, 255), 2);

        Mat roi = new Mat(thresh, box);
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(roi, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Rect> bubbles = new ArrayList<>();
        for (MatOfPoint cnt : contours) {
            Rect r = Imgproc.boundingRect(cnt);
            double aspect = (double) r.width / r.height;
            if (r.width > 10 && r.width < 35 && aspect > 0.6 && aspect < 1.4) {
                bubbles.add(r);
            }
        }

        if (bubbles.isEmpty()) return "????????????";

        // GOM CỘT
        bubbles.sort(Comparator.comparingInt(r -> r.x));
        List<List<Rect>> colClusters = new ArrayList<>();
        List<Rect> currentCol = new ArrayList<>();
        currentCol.add(bubbles.get(0));

        for (int i = 1; i < bubbles.size(); i++) {
            if (bubbles.get(i).x - currentCol.get(0).x < 20) {
                currentCol.add(bubbles.get(i));
            } else {
                colClusters.add(new ArrayList<>(currentCol));
                currentCol.clear();
                currentCol.add(bubbles.get(i));
            }
        }
        colClusters.add(currentCol);

        List<Integer> finalXs = new ArrayList<>();
        for (List<Rect> col : colClusters) {
            int sumX = 0;
            for (Rect r : col) sumX += (r.x + r.width / 2);
            finalXs.add(sumX / col.size());
        }
        while (finalXs.size() < expectedCols) {
            int gap = finalXs.size() > 1 ? (finalXs.get(1) - finalXs.get(0)) : (box.width / expectedCols);
            finalXs.add(finalXs.get(finalXs.size() - 1) + gap);
        }

        // GOM HÀNG
        bubbles.sort(Comparator.comparingInt(r -> r.y));
        List<List<Rect>> rowClusters = new ArrayList<>();
        List<Rect> currentRow = new ArrayList<>();
        currentRow.add(bubbles.get(0));

        for (int i = 1; i < bubbles.size(); i++) {
            if (bubbles.get(i).y - currentRow.get(0).y < 15) {
                currentRow.add(bubbles.get(i));
            } else {
                rowClusters.add(new ArrayList<>(currentRow));
                currentRow.clear();
                currentRow.add(bubbles.get(i));
            }
        }
        rowClusters.add(currentRow);

        List<Integer> finalYs = new ArrayList<>();
        for (List<Rect> row : rowClusters) {
            int sumY = 0;
            for (Rect r : row) sumY += (r.y + r.height / 2);
            finalYs.add(sumY / row.size());
        }

        // NỘI SUY
        if (finalYs.size() < expectedRows && finalYs.size() >= 2) {
            List<Integer> gaps = new ArrayList<>();
            for (int i = 1; i < finalYs.size(); i++) gaps.add(finalYs.get(i) - finalYs.get(i - 1));
            gaps.sort(Integer::compareTo);
            int stepY = gaps.get(gaps.size() / 2);

            List<Integer> interpolatedYs = new ArrayList<>();
            interpolatedYs.add(finalYs.get(0));

            // Bù các hàng bị thiếu ở ĐOẠN GIỮA
            for (int i = 1; i < finalYs.size(); i++) {
                int actualY = finalYs.get(i);
                while (actualY - interpolatedYs.get(interpolatedYs.size() - 1) > 1.5 * stepY) {
                    interpolatedYs.add(interpolatedYs.get(interpolatedYs.size() - 1) + stepY);
                }
                interpolatedYs.add(actualY);
            }

            // Bù các hàng bị thiếu ở ĐẦU hoặc CUỐI (ĐÃ SỬA TẠI ĐÂY)
            while (interpolatedYs.size() < expectedRows) {
                // finalYs chứa tọa độ Y tương đối so với top của box, nên khoảng trống ở trên chính là Y đầu tiên.
                int spaceTop = interpolatedYs.get(0);
                // Khoảng trống ở dưới là chiều cao box trừ đi Y cuối cùng
                int spaceBottom = box.height - interpolatedYs.get(interpolatedYs.size() - 1);

                if (spaceTop > spaceBottom) {
                    // Khoảng trống ở trên lớn hơn -> Thiếu hàng ở trên -> Prepend vào đầu List
                    interpolatedYs.add(0, interpolatedYs.get(0) - stepY);
                } else {
                    // Khoảng trống ở dưới lớn hơn -> Thiếu hàng ở dưới -> Append vào cuối List
                    interpolatedYs.add(interpolatedYs.get(interpolatedYs.size() - 1) + stepY);
                }
            }
            finalYs = interpolatedYs;

        } else if (finalYs.size() < 2) {
            int stepY = box.height / expectedRows;
            while(finalYs.size() < expectedRows) finalYs.add(finalYs.get(finalYs.size() - 1) + stepY);
        }

        // QUÉT DỰA TRÊN LÕI 10x10
        int colsToScan = Math.min(expectedCols, finalXs.size());
        int rowsToScan = Math.min(expectedRows, finalYs.size());
        StringBuilder result = new StringBuilder();

        for (int c = 0; c < colsToScan; c++) {
            int maxPx = 0, bestRow = -1;
            for (int r = 0; r < rowsToScan; r++) {
                int cx = box.x + finalXs.get(c);
                int cy = box.y + finalYs.get(r);
                Rect coreRect = new Rect(cx - 5, cy - 5, 10, 10);

                if(coreRect.x >= 0 && coreRect.y >= 0 && coreRect.x + coreRect.width < warped.cols() && coreRect.y + coreRect.height < warped.rows()) {
                    Imgproc.rectangle(warped, coreRect, new Scalar(255, 255, 0), 1);
                    Imgproc.circle(warped, new Point(cx, cy), 1, new Scalar(0, 0, 255), -1);

                    int px = Core.countNonZero(new Mat(thresh, coreRect));
                    if (px > maxPx) { maxPx = px; bestRow = r; }
                }
            }
            if (maxPx > 15) result.append(mapP3Row(bestRow));
            else result.append("?");
        }
        return result.toString();
    }

    /**
     * HÀM LƯỚI ĐỒNG BỘ: Áp dụng cho Phần 1, 2, SBD, Mã Đề
     */
    private static List<List<Rect>> getGridByColumnsWithVisual(Mat thresh, Mat warped, Rect box, int expectedCols, int expectedRows) {
        box.x = Math.max(0, box.x);
        box.y = Math.max(0, box.y);
        box.width = Math.min(box.width, thresh.cols() - box.x);
        box.height = Math.min(box.height, thresh.rows() - box.y);

        Mat roi = new Mat(thresh, box);
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(roi, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Rect> bubbles = new ArrayList<>();
        for (MatOfPoint cnt : contours) {
            Rect r = Imgproc.boundingRect(cnt);
            double aspect = (double) r.width / r.height;
            if (r.width > 10 && r.width < 35 && aspect > 0.6 && aspect < 1.4) {
                bubbles.add(r);
            }
        }

        List<List<Rect>> perfectGrid = new ArrayList<>();

        if (bubbles.isEmpty()) {
            double stepX = (double) box.width / expectedCols;
            double stepY = (double) box.height / expectedRows;
            for (int c = 0; c < expectedCols; c++) {
                List<Rect> col = new ArrayList<>();
                for (int r = 0; r < expectedRows; r++) {
                    int cx = (int) (c * stepX + stepX / 2);
                    int cy = (int) (r * stepY + stepY / 2);
                    col.add(new Rect(cx - 10, cy - 10, 20, 20));
                }
                perfectGrid.add(col);
            }
            return perfectGrid;
        }

        bubbles.sort(Comparator.comparingInt(r -> r.x));
        List<List<Rect>> colClusters = new ArrayList<>();
        List<Rect> currentCol = new ArrayList<>();
        currentCol.add(bubbles.get(0));

        for (int i = 1; i < bubbles.size(); i++) {
            if (bubbles.get(i).x - currentCol.get(0).x < 20) {
                currentCol.add(bubbles.get(i));
            } else {
                colClusters.add(new ArrayList<>(currentCol));
                currentCol.clear();
                currentCol.add(bubbles.get(i));
            }
        }
        colClusters.add(currentCol);

        // --- 1. NỘI SUY CỘT THÔNG MINH (TRÁI/PHẢI) ---
        List<Integer> finalXs = new ArrayList<>();
        for (List<Rect> col : colClusters) {
            int sumX = 0;
            for (Rect r : col) sumX += (r.x + r.width / 2);
            finalXs.add(sumX / col.size());
        }
        while (finalXs.size() < expectedCols) {
            int gap = finalXs.size() > 1 ? (finalXs.get(1) - finalXs.get(0)) : (box.width / expectedCols);
            int spaceLeft = finalXs.get(0);
            int spaceRight = box.width - finalXs.get(finalXs.size() - 1);

            if (spaceLeft > spaceRight) {
                finalXs.add(0, finalXs.get(0) - gap); // Thiếu ở trái -> Bù vào trái
            } else {
                finalXs.add(finalXs.get(finalXs.size() - 1) + gap); // Thiếu ở phải -> Bù vào phải
            }
        }

        bubbles.sort(Comparator.comparingInt(r -> r.y));
        List<List<Rect>> rowClusters = new ArrayList<>();
        List<Rect> currentRow = new ArrayList<>();
        currentRow.add(bubbles.get(0));

        for (int i = 1; i < bubbles.size(); i++) {
            if (bubbles.get(i).y - currentRow.get(0).y < 15) {
                currentRow.add(bubbles.get(i));
            } else {
                rowClusters.add(new ArrayList<>(currentRow));
                currentRow.clear();
                currentRow.add(bubbles.get(i));
            }
        }
        rowClusters.add(currentRow);

        // --- 2. NỘI SUY HÀNG THÔNG MINH (TRÊN/DƯỚI) ---
        List<Integer> finalYs = new ArrayList<>();
        for (List<Rect> row : rowClusters) {
            int sumY = 0;
            for (Rect r : row) sumY += (r.y + r.height / 2);
            finalYs.add(sumY / row.size());
        }

        if (finalYs.size() < expectedRows && finalYs.size() >= 2) {
            List<Integer> gaps = new ArrayList<>();
            for (int i = 1; i < finalYs.size(); i++) gaps.add(finalYs.get(i) - finalYs.get(i - 1));
            gaps.sort(Integer::compareTo);
            int stepY = gaps.get(gaps.size() / 2);

            List<Integer> interpolatedYs = new ArrayList<>();
            interpolatedYs.add(finalYs.get(0));
            for (int i = 1; i < finalYs.size(); i++) {
                int actualY = finalYs.get(i);
                while (actualY - interpolatedYs.get(interpolatedYs.size() - 1) > 1.5 * stepY) {
                    interpolatedYs.add(interpolatedYs.get(interpolatedYs.size() - 1) + stepY);
                }
                interpolatedYs.add(actualY);
            }
            while (interpolatedYs.size() < expectedRows) {
                int spaceTop = interpolatedYs.get(0);
                int spaceBottom = box.height - interpolatedYs.get(interpolatedYs.size() - 1);

                if (spaceTop > spaceBottom) {
                    interpolatedYs.add(0, interpolatedYs.get(0) - stepY); // Bù lên trên
                } else {
                    interpolatedYs.add(interpolatedYs.get(interpolatedYs.size() - 1) + stepY); // Bù xuống dưới
                }
            }
            finalYs = interpolatedYs;
        } else if (finalYs.size() < 2) {
            int stepY = box.height / expectedRows;
            while(finalYs.size() < expectedRows) finalYs.add(finalYs.get(finalYs.size() - 1) + stepY);
        }

        // --- 3. KHÓA RANH GIỚI (CLAMP) CỰC KỲ QUAN TRỌNG ---
        // Đảm bảo không có tọa độ nào nhảy ra khỏi chiều rộng/chiều cao của box
        for (int i = 0; i < finalXs.size(); i++) {
            if (finalXs.get(i) < 10) finalXs.set(i, 10);
            if (finalXs.get(i) > box.width - 10) finalXs.set(i, box.width - 10);
        }
        for (int i = 0; i < finalYs.size(); i++) {
            if (finalYs.get(i) < 10) finalYs.set(i, 10);
            if (finalYs.get(i) > box.height - 10) finalYs.set(i, box.height - 10);
        }

        for (int c = 0; c < expectedCols; c++) {
            List<Rect> colRects = new ArrayList<>();
            for (int r = 0; r < expectedRows; r++) {
                int cx = finalXs.get(c);
                int cy = finalYs.get(r);
                colRects.add(new Rect(cx - 10, cy - 10, 20, 20));
            }
            perfectGrid.add(colRects);
        }

        // Vẽ ô bao quanh để dễ debug
        for (int c = 0; c < perfectGrid.size(); c++) {
            List<Rect> col = perfectGrid.get(c);
            if(col.isEmpty()) continue;
            Scalar colColor = new Scalar(255, 255, 0);
            Rect first = col.get(0);
            Rect last = col.get(col.size()-1);
            Point p1 = new Point(box.x + first.x - 2, box.y + first.y - 2);
            Point p2 = new Point(box.x + last.x + last.width + 2, box.y + last.y + last.height + 2);
            Imgproc.rectangle(warped, p1, p2, colColor, 1);
        }

        return perfectGrid;
    }

    /**
     * PHẦN I: Nhận tham số numQuestionsToScan
     */
    private static Map<String, String> autoPart1Scan(Mat thresh, Mat warped, Rect box, int startIdx, int numQuestionsToScan) {
        Map<String, String> map = new LinkedHashMap<>();
        Imgproc.rectangle(warped, box, new Scalar(0, 255, 0), 2);

        List<List<Rect>> columns = getGridByColumnsWithVisual(thresh, warped, box, 4, 10);
        char[] labels = {'A', 'B', 'C', 'D'};

        if (columns.size() < 4) {
            for(int i=0; i<numQuestionsToScan; i++) map.put("P1_Câu_" + (startIdx + i + 1), "?");
            return map;
        }

        for (int row = 0; row < numQuestionsToScan; row++) {
            int maxPx = 0, bestCol = -1;
            for (int col = 0; col < 4; col++) {
                Rect cell = columns.get(col).get(row);

                int coreW = cell.width / 2;
                int coreH = cell.height / 2;
                int coreX = box.x + cell.x + cell.width / 4;
                int coreY = box.y + cell.y + cell.height / 4;
                Rect coreRect = new Rect(coreX, coreY, coreW, coreH);

                Imgproc.rectangle(warped, coreRect, new Scalar(255, 255, 0), 1);
                Imgproc.circle(warped, new Point(coreX + coreW/2.0, coreY + coreH/2.0), 1, new Scalar(0, 0, 255), -1);

                int px = Core.countNonZero(new Mat(thresh, coreRect));
                if (px > maxPx) { maxPx = px; bestCol = col; }
            }
            map.put("P1_Câu_" + (startIdx + row + 1), (maxPx > 15) ? String.valueOf(labels[bestCol]) : "?");
        }
        return map;
    }

    /**
     * PHẦN II: 1 khung quét đúng 1 câu
     */
    private static Map<String, String> autoPart2Scan(Mat thresh, Mat warped, Rect box, int questionIdx) {
        Map<String, String> map = new LinkedHashMap<>();
        Imgproc.rectangle(warped, box, new Scalar(0, 165, 255), 2);

        List<List<Rect>> columns = getGridByColumnsWithVisual(thresh, warped, box, 2, 4);
        int cauNum = questionIdx + 1;

        for (int i = 0; i < 4; i++) {
            char yChu = (char) ('a' + i);
            int maxPx = 0, bestCol = -1;

            for (int col = 0; col < 2; col++) {
                Rect cell = columns.get(col).get(i);

                int coreW = cell.width / 2;
                int coreH = cell.height / 2;
                int coreX = box.x + cell.x + cell.width / 4;
                int coreY = box.y + cell.y + cell.height / 4;
                Rect coreRect = new Rect(coreX, coreY, coreW, coreH);

                Imgproc.rectangle(warped, coreRect, new Scalar(255, 255, 0), 1);
                Imgproc.circle(warped, new Point(coreX + coreW/2.0, coreY + coreH/2.0), 1, new Scalar(0, 0, 255), -1);

                int px = Core.countNonZero(new Mat(thresh, coreRect));
                if (px > maxPx) { maxPx = px; bestCol = col; }
            }
            map.put("P2_Câu_" + cauNum + "_" + yChu, (maxPx > 15) ? (bestCol == 0 ? "Đ" : "S") : "?");
        }
        return map;
    }

    private static String autoColumnScan(Mat thresh, Mat warped, Rect box, int numCols, int numRows, String type, Scalar color) {
        Imgproc.rectangle(warped, box, color, 2);
        StringBuilder sb = new StringBuilder();

        List<List<Rect>> columns = getGridByColumnsWithVisual(thresh, warped, box, numCols, numRows);

        for (int c = 0; c < numCols; c++) {
            List<Rect> colBubbles = columns.get(c);
            int maxPx = 0, bestRow = -1;
            for (int r = 0; r < numRows; r++) {
                Rect cell = colBubbles.get(r);

                int coreW = cell.width / 2;
                int coreH = cell.height / 2;
                int coreX = box.x + cell.x + cell.width / 4;
                int coreY = box.y + cell.y + cell.height / 4;
                Rect coreRect = new Rect(coreX, coreY, coreW, coreH);

                Imgproc.rectangle(warped, coreRect, new Scalar(255, 255, 0), 1);
                Imgproc.circle(warped, new Point(coreX + coreW/2.0, coreY + coreH/2.0), 1, new Scalar(0, 0, 255), -1);

                if (coreRect.x >= 0 && coreRect.y >= 0 &&
                        coreRect.x + coreRect.width < warped.cols() &&
                        coreRect.y + coreRect.height < warped.rows()) {

                    Imgproc.rectangle(warped, coreRect, new Scalar(255, 255, 0), 1);
                    Imgproc.circle(warped, new Point(coreX + coreW/2.0, coreY + coreH/2.0), 1, new Scalar(0, 0, 255), -1);

                    int px = Core.countNonZero(new Mat(thresh, coreRect));
                    if (px > maxPx) { maxPx = px; bestRow = r; }
                }
            }
            if (maxPx > 15) {
                if (type.equals("P3")) sb.append(mapP3Row(bestRow));
                else sb.append(bestRow);
            } else sb.append("?");
        }
        return sb.toString();
    }

    private static String mapP3Row(int row) {
        String[] chars = {"-", ",", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
        return (row >= 0 && row < chars.length) ? chars[row] : "?";
    }

    // --- GIỮ NGUYÊN CÁC HÀM WARP VÀ CORNER ---
    public static List<Rect> findCornerMarks(Mat src) {
        Mat gray = new Mat(), thresh = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.adaptiveThreshold(gray, thresh, 255, 1, 1, 51, 15);
        Imgproc.morphologyEx(thresh, thresh, 3, Imgproc.getStructuringElement(0, new Size(9, 9)));
        List<MatOfPoint> cnts = new ArrayList<>();
        Imgproc.findContours(thresh, cnts, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        List<Rect> res = new ArrayList<>();
        for (MatOfPoint c : cnts) {
            Rect r = Imgproc.boundingRect(c);
            if (Imgproc.contourArea(c) / (src.width() * src.height()) > 0.0003) res.add(r);
        }
        return res;
    }

    public static Mat warpPerspective(Mat src, List<Rect> timingMarks) {
        List<Point> centers = timingMarks.stream().map(r -> new Point(r.x + r.width/2.0, r.y + r.height/2.0)).collect(Collectors.toList());
        Point tl=centers.get(0), tr=tl, bl=tl, br=tl;
        double minS=1e9, maxS=-1e9, maxD1=-1e9, maxD2=-1e9;
        for (Point p : centers) {
            double s=p.x+p.y, d1=p.x-p.y, d2=p.y-p.x;
            if(s<minS){minS=s; tl=p;} if(s>maxS){maxS=s; br=p;}
            if(d1>maxD1){maxD1=d1; tr=p;} if(d2>maxD2){maxD2=d2; bl=p;}
        }

        // --- LOGIC MỚI: TẠO LỀ (PADDING) ---
        // Giả sử ảnh gốc của bạn có lề từ mép giấy đến tâm ô đen là khoảng 40 pixel
        double padX = 40;
        double padY = 40;
        double width = 1200;
        double height = 1600;

        MatOfPoint2f srcPoints = new MatOfPoint2f(tl, tr, br, bl);
        MatOfPoint2f dstPoints = new MatOfPoint2f(
                new Point(padX, padY),                         // Top-Left: Lùi vào trong
                new Point(width - padX, padY),                 // Top-Right
                new Point(width - padX, height - padY),        // Bottom-Right
                new Point(padX, height - padY)                 // Bottom-Left
        );

        Mat m = Imgproc.getPerspectiveTransform(srcPoints, dstPoints);
        Mat w = new Mat();
        Imgproc.warpPerspective(src, w, m, new Size(width, height));
        return w;
    }

    public static void main(String[] args) {
        String inputPath = "D:\\tailieuhoctap\\laptrinhnangcao\\th\\btl\\ChamTracNghiem\\phieumau.jpg";
        System.out.println("--- ĐANG CHẠY CHẾ ĐỘ PRODUCTION (TÍCH HỢP EXAM CONFIG) ---");
        // Giả lập một cấu hình mẫu (Ví dụ: Đề chỉ có 25 câu P1, 4 câu P2, 2 câu P3)
        // Để chạy full, bạn truyền: new ExamConfig(40, 8, 6)
        ExamConfig testConfig = new ExamConfig(40, 8, 6);
        Map<String, String> results = processExam(inputPath, testConfig);
        if (results != null) {
            System.out.println("\n[KẾT QUẢ]");
            results.forEach((key, value) -> System.out.println(key + " : " + value));
        }
    }
}