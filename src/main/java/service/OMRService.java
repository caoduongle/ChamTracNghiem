package service;

import model.ExamConfig;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
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

        // =================================================================================
        // ĐÃ NÂNG CẤP: TỰ ĐỘNG NHẬN DIỆN MỌI ĐUÔI FILE ẢNH (.png, .jpeg, .jpg...)
        // =================================================================================
        int dotIndex = imagePath.lastIndexOf('.');
        String warpedPath = imagePath;
        if (dotIndex > 0) {
            String ext = imagePath.substring(dotIndex); // Lấy đuôi (vd: .png)
            String baseName = imagePath.substring(0, dotIndex); // Lấy tên bỏ đuôi
            warpedPath = baseName + "_processed" + ext; // Ghép lại: tenfile_processed.png
        } else {
            warpedPath += "_processed.jpg"; // Backup nếu file không có đuôi
        }

        List<Rect> cornerMarks = findCornerMarks(src);
        if (cornerMarks.size() < 4) return null;
        Mat warped = warpPerspective(src, cornerMarks);

        Mat gray = new Mat();
        Imgproc.cvtColor(warped, gray, Imgproc.COLOR_BGR2GRAY);
        Mat thresh = new Mat();

        // [FIX]: Lấy chỉ số Threshold tự động từ Setting của người dùng
        int thresholdValue = DataManager.getOmrThreshold();
        Imgproc.threshold(gray, thresh, thresholdValue, 255, Imgproc.THRESH_BINARY_INV);

        Map<String, String> results = new LinkedHashMap<>();

        int p1Count = (config != null) ? config.getNumPart1() : 40;
        int p2Count = (config != null) ? config.getNumPart2() : 8;
        int p3Count = (config != null) ? config.getNumPart3() : 6;

        // LẤY MẪU KIỂM TRA CHÉO TỪ CÁC CỘT KHÁC (GLOBAL ROW SYNCHRONIZATION)
        List<Integer> p1_RefYs = getGlobalReferenceYs(thresh, new Rect(100, 560, 1050, 290), 10);
        List<Integer> p2_RefYs = getGlobalReferenceYs(thresh, new Rect(100, 920, 1050, 150), 4);
        List<Integer> p3_RefYs = getGlobalReferenceYs(thresh, new Rect(100, 1160, 1050, 345), 12);

        // --- QUÉT PHẦN 1 ---
        int[] p1_X = {115, 375, 645, 915};
        int p1Boxes = (int) Math.ceil(p1Count / 10.0);
        for (int i = 0; i < p1Boxes; i++) {
            Rect colBox = new Rect(p1_X[i], 560, 195, 290);
            int questionsInThisBox = Math.min(10, p1Count - (i * 10));
            results.putAll(autoPart1Scan(thresh, warped, colBox, i * 10, questionsInThisBox, p1_RefYs));
        }

        // --- QUÉT PHẦN 2 ---
        int[] p2_X = {110, 220, 370, 480, 640, 745, 910, 1005};
        int p2Boxes = Math.min(8, p2Count);
        for (int i = 0; i < p2Boxes; i++) {
            Rect tableBox = new Rect(p2_X[i], 920, 98, 150);
            results.putAll(autoPart2Scan(thresh, warped, tableBox, i, p2_RefYs));
        }

        // --- QUÉT PHẦN 3 ---
        int[] p3_X = {110, 282, 441, 610, 776, 950};
        int p3Boxes = Math.min(6, p3Count);
        for (int i = 0; i < p3Boxes; i++) {
            Rect qBox = new Rect(p3_X[i], 1160, 142, 345);
            String val = scanSmartInterpolationPart3(thresh, warped, qBox, p3_RefYs);
            val = validatePart3Format(val);
            results.put("P3_Câu_" + (i + 1), val);
        }

        Imgcodecs.imwrite(warpedPath, warped);
        return results;
    }

    private static List<Integer> getGlobalReferenceYs(Mat thresh, Rect partArea, int expectedRows) {
        List<Integer> globalYs = new ArrayList<>();
        partArea.x = Math.max(0, partArea.x); partArea.y = Math.max(0, partArea.y);
        partArea.width = Math.min(partArea.width, thresh.cols() - partArea.x);
        partArea.height = Math.min(partArea.height, thresh.rows() - partArea.y);

        Mat roi = new Mat(thresh, partArea);
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

        if (!bubbles.isEmpty()) {
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

            rowClusters.sort((c1, c2) -> Integer.compare(c2.size(), c1.size()));
            int numRows = Math.min(expectedRows, rowClusters.size());
            List<List<Rect>> topRows = rowClusters.subList(0, numRows);

            for (List<Rect> row : topRows) {
                row.sort(Comparator.comparingInt(r -> r.y));
                int medianY = row.get(row.size() / 2).y + row.get(row.size() / 2).height / 2;
                globalYs.add(medianY + partArea.y);
            }
            Collections.sort(globalYs);
        }

        if (globalYs.size() >= 2 && globalYs.size() < expectedRows) {
            int gap = globalYs.get(1) - globalYs.get(0);
            while (globalYs.size() < expectedRows) globalYs.add(globalYs.get(globalYs.size() - 1) + gap);
        }
        return globalYs.size() == expectedRows ? globalYs : null;
    }

    private static List<List<Rect>> getGridByColumnsWithVisual(Mat thresh, Mat warped, Rect box, int expectedCols, int expectedRows, List<Integer> globalRefYs) {
        box.x = Math.max(0, box.x); box.y = Math.max(0, box.y);
        box.width = Math.min(box.width, thresh.cols() - box.x);
        box.height = Math.min(box.height, thresh.rows() - box.y);

        Mat roi = new Mat(thresh, box);
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(roi, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Rect> bubbles = new ArrayList<>();
        for (MatOfPoint cnt : contours) {
            Rect r = Imgproc.boundingRect(cnt);
            double aspect = (double) r.width / r.height;
            if (r.width > 10 && r.width < 35 && aspect > 0.6 && aspect < 1.4) bubbles.add(r);
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

        int standardGapX = box.width / expectedCols;

        List<Integer> cleanXs = new ArrayList<>();
        if (!finalXs.isEmpty()) {
            cleanXs.add(finalXs.get(0));
            for (int i = 1; i < finalXs.size(); i++) {
                if (finalXs.get(i) - cleanXs.get(cleanXs.size() - 1) >= standardGapX * 0.6) {
                    cleanXs.add(finalXs.get(i));
                }
            }
        }

        if (cleanXs.isEmpty()) {
            cleanXs.add(box.width / (expectedCols * 2));
        }
        finalXs = cleanXs;

        while (finalXs.size() < expectedCols) {
            int spaceLeft = finalXs.get(0);
            int spaceRight = box.width - finalXs.get(finalXs.size() - 1);
            if (spaceLeft > spaceRight) finalXs.add(0, finalXs.get(0) - standardGapX);
            else finalXs.add(finalXs.get(finalXs.size() - 1) + standardGapX);
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

        if (globalRefYs != null && globalRefYs.size() == expectedRows) {
            List<Integer> localRefYs = new ArrayList<>();
            for (int gy : globalRefYs) localRefYs.add(gy - box.y);

            List<Integer> crossCheckedYs = new ArrayList<>();
            for (int refY : localRefYs) {
                boolean foundMatch = false;
                for (int fy : finalYs) {
                    if (Math.abs(fy - refY) < 15) {
                        crossCheckedYs.add(fy);
                        foundMatch = true;
                        break;
                    }
                }
                if (!foundMatch) crossCheckedYs.add(refY);
            }
            finalYs = crossCheckedYs;
        } else {
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

    private static String scanSmartInterpolationPart3(Mat thresh, Mat warped, Rect box, List<Integer> globalRefYs) {
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
            if (r.width > 10 && r.width < 35 && aspect > 0.6 && aspect < 1.4) bubbles.add(r);
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

        int standardGapX = box.width / expectedCols;

        List<Integer> cleanXs = new ArrayList<>();
        if (!finalXs.isEmpty()) {
            cleanXs.add(finalXs.get(0));
            for (int i = 1; i < finalXs.size(); i++) {
                if (finalXs.get(i) - cleanXs.get(cleanXs.size() - 1) >= standardGapX * 0.6) {
                    cleanXs.add(finalXs.get(i));
                }
            }
        }

        if (cleanXs.isEmpty()) {
            cleanXs.add(box.width / (expectedCols * 2));
        }
        finalXs = cleanXs;

        while (finalXs.size() < expectedCols) {
            int spaceLeft = finalXs.get(0);
            int spaceRight = box.width - finalXs.get(finalXs.size() - 1);
            if (spaceLeft > spaceRight) finalXs.add(0, finalXs.get(0) - standardGapX);
            else finalXs.add(finalXs.get(finalXs.size() - 1) + standardGapX);
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

        if (globalRefYs != null && globalRefYs.size() == expectedRows) {
            List<Integer> localRefYs = new ArrayList<>();
            for (int gy : globalRefYs) localRefYs.add(gy - box.y);

            List<Integer> crossCheckedYs = new ArrayList<>();
            for (int refY : localRefYs) {
                boolean foundMatch = false;
                for (int fy : finalYs) {
                    if (Math.abs(fy - refY) < 15) {
                        crossCheckedYs.add(fy);
                        foundMatch = true;
                        break;
                    }
                }
                if (!foundMatch) crossCheckedYs.add(refY);
            }
            finalYs = crossCheckedYs;
        } else {
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
        }

        for (int i = 0; i < finalYs.size(); i++) {
            if (finalYs.get(i) < 10) finalYs.set(i, 10);
            if (finalYs.get(i) > box.height - 10) finalYs.set(i, box.height - 10);
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

    private static Map<String, String> autoPart1Scan(Mat thresh, Mat warped, Rect box, int startIdx, int numQuestionsToScan, List<Integer> globalRefYs) {
        Map<String, String> map = new LinkedHashMap<>();
        Imgproc.rectangle(warped, box, new Scalar(0, 255, 0), 2);

        List<List<Rect>> columns = getGridByColumnsWithVisual(thresh, warped, box, 4, 10, globalRefYs);
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

            String ans = "?";
            if (maxPx < 15) {
                ans = "?";
            } else if (secondMaxPx > (maxPx * 0.7)) {
                ans = "ERR_" + labels[bestCol] + "" + labels[secondBestCol];
            } else if (maxPx < 25) {
                ans = "WARN_" + labels[bestCol];
            } else {
                ans = String.valueOf(labels[bestCol]);
            }
            map.put("P1_Câu_" + (startIdx + row + 1), ans);
        }
        return map;
    }

    private static Map<String, String> autoPart2Scan(Mat thresh, Mat warped, Rect box, int questionIdx, List<Integer> globalRefYs) {
        Map<String, String> map = new LinkedHashMap<>();
        Imgproc.rectangle(warped, box, new Scalar(0, 165, 255), 2);

        List<List<Rect>> columns = getGridByColumnsWithVisual(thresh, warped, box, 2, 4, globalRefYs);
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
                ans = "ERR_TK";
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

        double padX = 40, padY = 40, width = 1200, height = 1600;

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

    private static String validatePart3Format(String val) {
        String cleanVal = val.replace("?", "");
        if (cleanVal.isEmpty()) return val;

        boolean isValid = true;
        int minusCount = 0, commaCount = 0;

        for (int i = 0; i < cleanVal.length(); i++) {
            char c = cleanVal.charAt(i);
            if (c == '-') {
                minusCount++;
                if (i > 0) isValid = false;
            } else if (c == ',') {
                commaCount++;
                if (i == 0) isValid = false;
                if (i > 0 && cleanVal.charAt(i - 1) == '-') isValid = false;
            }
        }

        if (minusCount > 1 || commaCount > 1) isValid = false;
        if (!isValid) return "WARN_FMT_" + val;
        return val;
    }

    public static void main(String[] args) {
        String inputPath = "D:\\tailieuhoctap\\laptrinhnangcao\\th\\btl\\ChamTracNghiem\\phieumau.jpg";
        System.out.println("--- ĐANG CHẠY CHẾ ĐỘ PRODUCTION (TÍCH HỢP KIỂM TRA CHÉO) ---");
        ExamConfig testConfig = new ExamConfig(40, 8, 6);
        Map<String, String> results = processExam(inputPath, testConfig);
        if (results != null) {
            System.out.println("\n[KẾT QUẢ]");
            results.forEach((key, value) -> System.out.println(key + " : " + value));
        }
    }
}