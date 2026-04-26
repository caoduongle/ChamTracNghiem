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

        int p1Count = (config != null) ? config.getNumPart1() : 40;
        int p2Count = (config != null) ? config.getNumPart2() : 8;
        int p3Count = (config != null) ? config.getNumPart3() : 6;

        int[] p1_X = {115, 375, 645, 915};
        int p1Boxes = (int) Math.ceil(p1Count / 10.0);
        for (int i = 0; i < p1Boxes; i++) {
            Rect colBox = new Rect(p1_X[i], 580, 195, 269);
            int questionsInThisBox = Math.min(10, p1Count - (i * 10));
            results.putAll(autoPart1Scan(thresh, warped, colBox, i * 10, questionsInThisBox));
        }

        int[] p2_X = {110, 220, 370, 480, 640, 745, 910, 1005};
        int p2Boxes = Math.min(8, p2Count);
        for (int i = 0; i < p2Boxes; i++) {
            Rect tableBox = new Rect(p2_X[i], 951, 98, 115);
            results.putAll(autoPart2Scan(thresh, warped, tableBox, i));
        }

        int[] p3_X = {110, 282, 441, 610, 776, 950};
        int p3Boxes = Math.min(6, p3Count);
        for (int i = 0; i < p3Boxes; i++) {
            Rect qBox = new Rect(p3_X[i], 1175, 142, 325);
            String val = scanSmartInterpolationPart3(thresh, warped, qBox);
            val = validatePart3Format(val);
            results.put("P3_Câu_" + (i + 1), val);
        }

        Imgcodecs.imwrite(warpedPath, warped);
        return results;
    }

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
                    interpolatedYs.add(0, interpolatedYs.get(0) - stepY);
                } else {
                    interpolatedYs.add(interpolatedYs.get(interpolatedYs.size() - 1) + stepY);
                }
            }
            finalYs = interpolatedYs;

        } else if (finalYs.size() < 2) {
            int stepY = box.height / expectedRows;
            while(finalYs.size() < expectedRows) finalYs.add(finalYs.get(finalYs.size() - 1) + stepY);
        }

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

            if (spaceLeft > spaceRight) finalXs.add(0, finalXs.get(0) - gap);
            else finalXs.add(finalXs.get(finalXs.size() - 1) + gap);
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

                if (spaceTop > spaceBottom) interpolatedYs.add(0, interpolatedYs.get(0) - stepY);
                else interpolatedYs.add(interpolatedYs.get(interpolatedYs.size() - 1) + stepY);
            }
            finalYs = interpolatedYs;
        } else if (finalYs.size() < 2) {
            int stepY = box.height / expectedRows;
            while(finalYs.size() < expectedRows) finalYs.add(finalYs.get(finalYs.size() - 1) + stepY);
        }

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

    // --- ĐÃ NÂNG CẤP: LOGIC BẮT TÔ KÉP & NÉT MỜ PHẦN 1 ---
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
            int maxPx = 0, secondMaxPx = 0;
            int bestCol = -1, secondBestCol = -1;

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
                if (px > maxPx) {
                    secondMaxPx = maxPx;
                    secondBestCol = bestCol;
                    maxPx = px;
                    bestCol = col;
                } else if (px > secondMaxPx) {
                    secondMaxPx = px;
                    secondBestCol = col;
                }
            }

            // Logic tính độ tin cậy
            String ans = "?";
            if (maxPx < 15) {
                ans = "?"; // Bỏ trống
            } else if (secondMaxPx > (maxPx * 0.7)) {
                ans = "ERR_" + labels[bestCol] + "" + labels[secondBestCol]; // Lỗi tô kép
            } else if (maxPx < 25) {
                ans = "WARN_" + labels[bestCol]; // Cảnh báo nét mờ
            } else {
                ans = String.valueOf(labels[bestCol]); // Bình thường
            }
            map.put("P1_Câu_" + (startIdx + row + 1), ans);
        }
        return map;
    }

    // --- ĐÃ NÂNG CẤP: LOGIC BẮT TÔ KÉP & NÉT MỜ PHẦN 2 ---
    private static Map<String, String> autoPart2Scan(Mat thresh, Mat warped, Rect box, int questionIdx) {
        Map<String, String> map = new LinkedHashMap<>();
        Imgproc.rectangle(warped, box, new Scalar(0, 165, 255), 2);

        List<List<Rect>> columns = getGridByColumnsWithVisual(thresh, warped, box, 2, 4);
        int cauNum = questionIdx + 1;

        for (int i = 0; i < 4; i++) {
            char yChu = (char) ('a' + i);
            int maxPx = 0, secondMaxPx = 0;
            int bestCol = -1, secondBestCol = -1;

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
                if (px > maxPx) {
                    secondMaxPx = maxPx;
                    secondBestCol = bestCol;
                    maxPx = px;
                    bestCol = col;
                } else if (px > secondMaxPx) {
                    secondMaxPx = px;
                    secondBestCol = col;
                }
            }

            String ans = "?";
            if (maxPx < 15) {
                ans = "?";
            } else if (secondMaxPx > (maxPx * 0.7)) {
                ans = "ERR_TK"; // Tô kép
            } else if (maxPx < 25) {
                ans = "WARN_" + (bestCol == 0 ? "Đ" : "S");
            } else {
                ans = (bestCol == 0 ? "Đ" : "S");
            }
            map.put("P2_Câu_" + cauNum + "_" + yChu, ans);
        }
        return map;
    }

    private static String mapP3Row(int row) {
        String[] chars = {"-", ",", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
        return (row >= 0 && row < chars.length) ? chars[row] : "?";
    }

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

        double padX = 40;
        double padY = 40;
        double width = 1200;
        double height = 1600;

        MatOfPoint2f srcPoints = new MatOfPoint2f(tl, tr, br, bl);
        MatOfPoint2f dstPoints = new MatOfPoint2f(
                new Point(padX, padY),
                new Point(width - padX, padY),
                new Point(width - padX, height - padY),
                new Point(padX, height - padY)
        );

        Mat m = Imgproc.getPerspectiveTransform(srcPoints, dstPoints);
        Mat w = new Mat();
        Imgproc.warpPerspective(src, w, m, new Size(width, height));
        return w;
    }
    // --- HÀM MỚI: KIỂM TRA LOGIC ĐỊNH DẠNG TOÁN HỌC PHẦN 3 ---
    private static String validatePart3Format(String val) {
        // Xóa tạm các dấu '?' (ô trống) để kiểm tra logic các ký tự đã tô
        String cleanVal = val.replace("?", "");
        if (cleanVal.isEmpty()) return val;

        boolean isValid = true;
        int minusCount = 0;
        int commaCount = 0;

        for (int i = 0; i < cleanVal.length(); i++) {
            char c = cleanVal.charAt(i);
            if (c == '-') {
                minusCount++;
                if (i > 0) isValid = false; // Dấu trừ KHÔNG nằm ở đầu (VD: 8-)
            } else if (c == ',') {
                commaCount++;
                if (i == 0) isValid = false; // Dấu phẩy nằm ngay đầu (VD: ,8)
                if (i > 0 && cleanVal.charAt(i - 1) == '-') isValid = false; // Dấu phẩy ngay sau dấu trừ (VD: -,5)
            }
        }

        if (minusCount > 1 || commaCount > 1) isValid = false; // Có từ 2 dấu trừ hoặc 2 dấu phẩy trở lên

        // Nếu phát hiện sai định dạng, gắn cờ WARN_FMT_ ở phía trước
        if (!isValid) {
            return "WARN_FMT_" + val;
        }
        return val;
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