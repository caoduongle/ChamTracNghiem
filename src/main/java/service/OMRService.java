package service;

import model.AnchorZone;
import model.ExamConfig;
import model.OMRTemplate;
import model.RelativePart;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

public class OMRService {

    static { OpenCV.loadLocally(); }

    private static final int TARGET_WIDTH = 1200;
    private static final int TARGET_HEIGHT = 1600;
    private static final int THRESH_EMPTY = 50;
    private static final double DOUBLE_MARK_RATIO = 0.75;

    public static Map<String, String> processExam(String imagePath, ExamConfig config) {
        return processExam(imagePath, config, "BGD4");
    }

    public static Map<String, String> processExam(String imagePath, ExamConfig config, String templateId) {
        Mat src = Imgcodecs.imread(imagePath);
        if (src.empty()) return null;

        String warpedPath = imagePath.replace(".jpg", "_processed.jpg");
        String debugPath = imagePath.replace(".jpg", "_debug_corners.jpg");

        List<Rect> cornerMarks = findCornerMarks(src);

        // --- XUẤT ẢNH DEBUG 4 GÓC ---
     /*   Mat debugImg = src.clone();
        for (int i = 0; i < cornerMarks.size(); i++) {
            Rect r = cornerMarks.get(i);
            Imgproc.rectangle(debugImg, r, new Scalar(0, 0, 255), 3);
            Imgproc.putText(debugImg, "Goc " + i, new Point(r.x, r.y - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0, 255, 255), 2);
        }
        Imgcodecs.imwrite(debugPath, debugImg);
        System.out.println("[DEBUG] Số góc nhận diện được: " + cornerMarks.size() + " (Đã lưu tại: " + debugPath + ")");
*/
        if (cornerMarks.size() < 4) return null;

        Mat warped = warpPerspective(src, cornerMarks, TARGET_WIDTH, TARGET_HEIGHT);
        OMRTemplate template = TemplateFactory.getTemplate(templateId);

        Mat gray = new Mat();
        Imgproc.cvtColor(warped, gray, Imgproc.COLOR_BGR2GRAY);
        Mat thresh = new Mat();
        int thresholdValue = DataManager.getOmrThreshold();
        Imgproc.threshold(gray, thresh, thresholdValue, 255, Imgproc.THRESH_BINARY_INV);

        List<Integer> rawXAnchors = new ArrayList<>();
        List<Integer> rawYAnchors = new ArrayList<>();

        for (AnchorZone zone : template.anchorZones) {
            List<Integer> found = scanAnchorsInZone(thresh, warped, zone.region, zone.isX);
            if (zone.isX) rawXAnchors.addAll(found); else rawYAnchors.addAll(found);
        }

        List<Integer> xAnchors = clusterLines(rawXAnchors);
        List<Integer> yAnchors = clusterLines(rawYAnchors);
        Map<String, String> results = new LinkedHashMap<>();

        int p1Count = (config != null) ? config.getNumPart1() : 40;
        int p2Count = (config != null) ? config.getNumPart2() : 8;
        int p3Count = (config != null) ? config.getNumPart3() : 6;

        // --- PHẦN 1 ---
        int qCount = 0;
        List<Rect> p1Boxes = new ArrayList<>();
        for (RelativePart scheme : template.part1Boxes) p1Boxes.add(resolveAnchoredRect(scheme, xAnchors, yAnchors));

        List<Integer> globalY1 = resolveGlobalYCoords(thresh, p1Boxes, template.p1ExpectedRows);
        for (int i = 0; i < p1Boxes.size(); i++) {
            if (qCount >= p1Count) break;
            int questionsInThisBox = Math.min(template.p1ExpectedRows, p1Count - qCount);
            results.putAll(autoPart1Scan(thresh, warped, p1Boxes.get(i), qCount, questionsInThisBox, template.p1ExpectedRows, globalY1));
            qCount += template.p1ExpectedRows;
        }

        // --- PHẦN 2 ---
        List<Rect> p2Boxes = new ArrayList<>();
        for (int i = 0; i < Math.min(template.part2Boxes.size(), p2Count); i++) p2Boxes.add(resolveAnchoredRect(template.part2Boxes.get(i), xAnchors, yAnchors));

        List<Integer> globalY2 = resolveGlobalYCoords(thresh, p2Boxes, template.p2ExpectedRows);
        for (int i = 0; i < p2Boxes.size(); i++) {
            results.putAll(autoPart2Scan(thresh, warped, p2Boxes.get(i), i, template.p2ExpectedRows, globalY2));
        }

        // --- PHẦN 3 ---
        List<Rect> p3Boxes = new ArrayList<>();
        for (int i = 0; i < Math.min(template.part3Boxes.size(), p3Count); i++) p3Boxes.add(resolveAnchoredRect(template.part3Boxes.get(i), xAnchors, yAnchors));

        List<Integer> globalY3 = resolveGlobalYCoords(thresh, p3Boxes, template.p3ExpectedRows);
        for (int i = 0; i < p3Boxes.size(); i++) {
            String val = scanPart3(thresh, warped, p3Boxes.get(i), template.p3ExpectedRows, globalY3);
            results.put("P3_Câu_" + (i + 1), validatePart3Format(val));
        }

        drawCoordinateRuler(warped);
        drawAnchorIndices(warped, xAnchors, yAnchors);
        Imgcodecs.imwrite(warpedPath, warped);

        return results;
    }

    private static List<Integer> resolveGlobalYCoords(Mat thresh, List<Rect> boxes, int expectedRows) {
        if (boxes.isEmpty()) return new ArrayList<>();
        List<Integer> allRawYs = new ArrayList<>();
        int minY = Integer.MAX_VALUE;
        int maxY = 0;

        for (Rect box : boxes) {
            List<Rect> bubbles = extractValidBubbles(thresh, box);
            for (Rect r : bubbles) allRawYs.add(r.y + r.height / 2);
            minY = Math.min(minY, box.y);
            maxY = Math.max(maxY, box.y + box.height);
        }
        return reconstructSequence(allRawYs, expectedRows, minY, maxY - minY);
    }

    private static Rect resolveAnchoredRect(RelativePart scheme, List<Integer> xA, List<Integer> yA) {
        int realOriginX = getSafeAnchor(xA, scheme.originXIndex, 0);
        int realOriginY = getSafeAnchor(yA, scheme.originYIndex, 0);
        int realDestX   = getSafeAnchor(xA, scheme.destXIndex, TARGET_WIDTH);
        int realDestY   = getSafeAnchor(yA, scheme.destYIndex, TARGET_HEIGHT);

        int realSpanX = realDestX - realOriginX;
        int realSpanY = realDestY - realOriginY;

        if (realSpanX <= 50) realSpanX = TARGET_WIDTH;
        if (realSpanY <= 50) realSpanY = TARGET_HEIGHT;

        int finalX = realOriginX + (int) (scheme.offsetXRatio * realSpanX);
        int finalY = realOriginY + (int) (scheme.offsetYRatio * realSpanY);
        int finalW = (int) (scheme.widthRatio * realSpanX);
        int finalH = (int) (scheme.heightRatio * realSpanY);

        return new Rect(finalX, finalY, finalW, finalH);
    }

    private static int getSafeAnchor(List<Integer> anchors, int index, int fallback) {
        if (anchors == null || anchors.isEmpty()) return fallback;
        if (index < 0) return anchors.get(0);
        if (index >= anchors.size()) return anchors.get(anchors.size() - 1);
        return anchors.get(index);
    }

    private static List<List<Rect>> getDynamicBubbleGrid(Mat thresh, Mat warped, Rect box, int expectedCols, int expectedRows, List<Integer> globalYCoords) {
        Imgproc.rectangle(warped, box, new Scalar(255, 0, 255), 1);
        List<Rect> bubbles = extractValidBubbles(thresh, box);

        List<Integer> rawXs = new ArrayList<>();
        List<Integer> rawYs = new ArrayList<>();
        for (Rect r : bubbles) {
            rawXs.add(r.x + r.width / 2);
            rawYs.add(r.y + r.height / 2);
        }

        List<Integer> xCoords = reconstructSequence(rawXs, expectedCols, box.x, box.width);
        List<Integer> yCoords = (globalYCoords != null && globalYCoords.size() == expectedRows)
                ? globalYCoords
                : reconstructSequence(rawYs, expectedRows, box.y, box.height);

        List<List<Rect>> grid = new ArrayList<>();
        for (int c = 0; c < expectedCols; c++) {
            List<Rect> colRects = new ArrayList<>();
            for (int r = 0; r < expectedRows; r++) {
                int cx = xCoords.get(c);
                int cy = yCoords.get(r);

                cx = Math.max(box.x + 10, Math.min(cx, box.x + box.width - 10));
                cy = Math.max(box.y + 10, Math.min(cy, box.y + box.height - 10));

                Rect coreRect = new Rect(cx - 12, cy - 12, 24, 24);
                colRects.add(coreRect);
                Imgproc.rectangle(warped, coreRect, new Scalar(255, 255, 0), 1);
            }
            grid.add(colRects);
        }
        return grid;
    }

    // =================================================================================
    // THUẬT TOÁN HYBRID: RĂNG LƯỢC + ĐIỂM NEO + ĐỒNG BỘ CHÉO (ULTIMATE VERSION)
    // =================================================================================
    private static List<Integer> reconstructSequence(List<Integer> rawValues, int expectedCount, int offset, int span) {
        List<Integer> result = new ArrayList<>();
        double theoreticalGap = (double) span / expectedCount;

        // TÌNH HUỐNG ZERO: Không có giọt mực nào -> Dùng toán học chia đều
        if (rawValues.isEmpty() || rawValues.size() < expectedCount / 3) {
            double start = offset + theoreticalGap / 2.0;
            for (int i = 0; i < expectedCount; i++) result.add((int) Math.round(start + i * theoreticalGap));
            return result;
        }

        // BƯỚC 1: RĂNG LƯỢC RÀ SOÁT - Gom các vết mực gần nhau thành các Cụm (Clusters)
        Collections.sort(rawValues);
        List<Integer> clusters = new ArrayList<>();
        int currentSum = rawValues.get(0), count = 1;
        for (int i = 1; i < rawValues.size(); i++) {
            if (rawValues.get(i) - rawValues.get(i - 1) < 15) {
                currentSum += rawValues.get(i); count++;
            } else {
                clusters.add(currentSum / count);
                currentSum = rawValues.get(i); count = 1;
            }
        }
        clusters.add(currentSum / count);

        // BƯỚC 2: TÌM KHOẢNG CÁCH CHUẨN (Median Gap) - Đo khoảng cách xuất hiện nhiều nhất
        List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < clusters.size(); i++) gaps.add(clusters.get(i) - clusters.get(i - 1));
        Collections.sort(gaps);

        double standardGap = gaps.isEmpty() ? theoreticalGap : gaps.get(gaps.size() / 2);
        double tolerance = (clusters.size() == expectedCount) ? 0.35 : 0.20;

        // [Kiểm tra chéo]: Nếu khoảng cách chuẩn sai lệch quá 20% do giấy bị nhăn nhúm nặng, ép về lý thuyết
        if (standardGap < theoreticalGap * 0.8 || standardGap > theoreticalGap * 1.2) {
            standardGap = theoreticalGap;
        }

        // BƯỚC 3: KIỂM TRA SỐ LƯỢNG VÀ ĐỘ DÍNH/LỆCH HÀNG
        boolean needCorrection = false;
        if (clusters.size() != expectedCount) {
            needCorrection = true; // Báo động: Thiếu hoặc thừa hàng/cột
        } else {
            // Rà soát lại xem có hàng nào dính vào nhau hoặc dãn cách bất thường không (Sai số > 15%)
            for (int gap : gaps) {
                if (gap < standardGap * 0.85 || gap > standardGap * 1.15) {
                    needCorrection = true; // Báo động: Bị dính dít hoặc đứt gãy
                    break;
                }
            }
        }

        // BƯỚC 4: KÍCH HOẠT ĐIỂM NEO KẾT HỢP RĂNG LƯỢC ĐỂ SỬA LỖI
        if (needCorrection) {
            // A. Dùng Điểm Neo: Chốt 1 cụm mực vững chắc nhất ở GẦN GIỮA KHUNG TÍM
            int centerOfBox = offset + (span / 2);
            int bestAnchor = clusters.get(0);
            int minDiff = Math.abs(bestAnchor - centerOfBox);
            for (int c : clusters) {
                int diff = Math.abs(c - centerOfBox);
                if (diff < minDiff) { minDiff = diff; bestAnchor = c; }
            }

            // B. Xác định Điểm Neo này là hàng/cột thứ mấy
            int anchorIndex = (int) Math.round((bestAnchor - (offset + standardGap / 2.0)) / standardGap);
            anchorIndex = Math.max(0, Math.min(anchorIndex, expectedCount - 1));

            // C. Răng lược trượt vi chỉnh: Lấy Điểm Neo làm mốc, trượt nhẹ sang trái/phải
            // để bộ khung bắt dính tối đa vào TẤT CẢ các cụm mực thật
            double bestStart = bestAnchor - (anchorIndex * standardGap);
            double minError = Double.MAX_VALUE;

            for (double s = bestStart - standardGap/2; s <= bestStart + standardGap/2; s += 1.0) {
                double error = 0;
                for (int c : clusters) {
                    double minErrorToTooth = Double.MAX_VALUE;
                    for (int i = 0; i < expectedCount; i++) {
                        double tooth = s + i * standardGap;
                        double dist = Math.abs(c - tooth);
                        if (dist < minErrorToTooth) minErrorToTooth = dist;
                    }
                    error += Math.min(minErrorToTooth, standardGap * 0.3); // Giới hạn lỗi chống nhiễu
                }
                if (error < minError) { minError = error; bestStart = s; }
            }

            // Tái tạo lại toàn bộ mảng tọa độ hoàn hảo
            for (int i = 0; i < expectedCount; i++) result.add((int) Math.round(bestStart + i * standardGap));
        } else {
            // Tình huống Lý tưởng: Đủ số lượng và khoảng cách hoàn hảo chuẩn mực -> Giữ nguyên
            result = new ArrayList<>(clusters);
        }

        // BƯỚC 5: BẢO VỆ BIÊN (Kiểm tra lệch ra khỏi khung tím)
        for (int i = 0; i < result.size(); i++) {
            int val = result.get(i);
            // Nếu bị văng ra khỏi mép trên/trái của khung -> Ép thụt vào trong
            if (val < offset + 5) {
                result.set(i, offset + 5 + (int)(standardGap * 0.3));
            }
            // Nếu bị văng ra khỏi mép dưới/phải của khung -> Ép thụt vào trong
            if (val > offset + span - 5) {
                result.set(i, offset + span - 5 - (int)(standardGap * 0.3));
            }
        }

        return result;
    }

    private static List<Rect> extractValidBubbles(Mat thresh, Rect box) {
        Rect safeBox = new Rect(box.x, box.y, box.width, box.height);
        safeBox.x = Math.max(0, safeBox.x); safeBox.y = Math.max(0, safeBox.y);
        if (safeBox.x + safeBox.width > thresh.cols()) safeBox.width = thresh.cols() - safeBox.x;
        if (safeBox.y + safeBox.height > thresh.rows()) safeBox.height = thresh.rows() - safeBox.y;

        Mat roi = new Mat(thresh, safeBox);
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(roi, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Rect> bubbles = new ArrayList<>();
        for (MatOfPoint c : contours) {
            Rect r = Imgproc.boundingRect(c);
            double aspect = (double) r.width / r.height;
            if (r.width >= 12 && r.width <= 30 && aspect >= 0.7 && aspect <= 1.3) {
                r.x += safeBox.x;
                r.y += safeBox.y;
                bubbles.add(r);
            }
        }
        return bubbles;
    }

    private static Map<String, String> autoPart1Scan(Mat thresh, Mat warped, Rect hintBox, int startIdx, int numQuestions, int expectedRows, List<Integer> globalYCoords) {
        Map<String, String> map = new LinkedHashMap<>();
        List<List<Rect>> columns = getDynamicBubbleGrid(thresh, warped, hintBox, 4, expectedRows, globalYCoords);
        char[] labels = {'A', 'B', 'C', 'D'};

        for (int row = 0; row < numQuestions; row++) {
            int maxPx = 0, secondMaxPx = 0, bestCol = -1, secondBestCol = -1;
            for (int col = 0; col < 4; col++) {
                Rect outerBox = columns.get(col).get(row);
                Rect innerBox = new Rect(outerBox.x + 5, outerBox.y + 5, 14, 14);
                int px = Core.countNonZero(new Mat(thresh, innerBox));
                Imgproc.rectangle(warped, innerBox, new Scalar(0, 255, 0), 1);

                if (px > maxPx) { secondMaxPx = maxPx; secondBestCol = bestCol; maxPx = px; bestCol = col; }
                else if (px > secondMaxPx) { secondMaxPx = px; secondBestCol = col; }
            }
            String ans1 = bestCol != -1 ? String.valueOf(labels[bestCol]) : "";
            String ans2 = secondBestCol != -1 ? String.valueOf(labels[secondBestCol]) : "";
            map.put("P1_Câu_" + (startIdx + row + 1), evaluateBubbleChoice(maxPx, secondMaxPx, ans1, ans2, false));
        }
        return map;
    }

    private static Map<String, String> autoPart2Scan(Mat thresh, Mat warped, Rect hintBox, int questionIdx, int expectedRows, List<Integer> globalYCoords) {
        Map<String, String> map = new LinkedHashMap<>();
        List<List<Rect>> columns = getDynamicBubbleGrid(thresh, warped, hintBox, 2, expectedRows, globalYCoords);
        int cauNum = questionIdx + 1;

        for (int row = 0; row < 4; row++) {
            int maxPx = 0, secondMaxPx = 0, bestCol = -1, secondBestCol = -1;
            for (int col = 0; col < 2; col++) {
                Rect outerBox = columns.get(col).get(row);
                Rect innerBox = new Rect(outerBox.x + 5, outerBox.y + 5, 14, 14);
                int px = Core.countNonZero(new Mat(thresh, innerBox));
                Imgproc.rectangle(warped, innerBox, new Scalar(0, 255, 0), 1);

                if (px > maxPx) { secondMaxPx = maxPx; secondBestCol = bestCol; maxPx = px; bestCol = col; }
                else if (px > secondMaxPx) { secondMaxPx = px; secondBestCol = col; }
            }
            String ans1 = bestCol == 0 ? "Đ" : (bestCol == 1 ? "S" : "");
            String ans2 = secondBestCol == 0 ? "Đ" : (secondBestCol == 1 ? "S" : "");
            map.put("P2_Câu_" + cauNum + "_" + (char)('a' + row), evaluateBubbleChoice(maxPx, secondMaxPx, ans1, ans2, true));
        }
        return map;
    }

    private static String scanPart3(Mat thresh, Mat warped, Rect hintBox, int expectedRows, List<Integer> globalYCoords) {
        List<List<Rect>> columns = getDynamicBubbleGrid(thresh, warped, hintBox, 4, expectedRows, globalYCoords);
        StringBuilder result = new StringBuilder();
        String[] chars = {"-", ",", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

        for (int c = 0; c < 4; c++) {
            int maxPx = 0, bestRow = -1;
            for (int r = 0; r < expectedRows; r++) {
                Rect outerBox = columns.get(c).get(r);
                Rect innerBox = new Rect(outerBox.x + 5, outerBox.y + 5, 14, 14);
                int px = Core.countNonZero(new Mat(thresh, innerBox));
                Imgproc.rectangle(warped, innerBox, new Scalar(0, 255, 0), 1);

                if (px > maxPx) { maxPx = px; bestRow = r; }
            }
            result.append(maxPx >= THRESH_EMPTY && bestRow >= 0 && bestRow < chars.length ? chars[bestRow] : "?");
        }
        return result.toString();
    }

    private static String evaluateBubbleChoice(int maxPx, int secondMaxPx, String bestLabel, String secondBestLabel, boolean isPart2) {
        if (maxPx < THRESH_EMPTY) return "?";
        if (secondMaxPx > (maxPx * DOUBLE_MARK_RATIO)) return isPart2 ? "ERR_TK" : ("ERR_" + bestLabel + secondBestLabel);
        return bestLabel;
    }

    private static List<Integer> scanAnchorsInZone(Mat thresh, Mat warped, Rect zone, boolean isXAnchor) {
        List<Integer> anchors = new ArrayList<>();
        zone.x = Math.max(0, zone.x); zone.y = Math.max(0, zone.y);
        if (zone.x + zone.width > thresh.cols()) zone.width = thresh.cols() - zone.x;
        if (zone.y + zone.height > thresh.rows()) zone.height = thresh.rows() - zone.y;

        Imgproc.rectangle(warped, zone, new Scalar(255, 0, 0), 1);
        Mat roi = new Mat(thresh, zone);
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(roi, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        for (MatOfPoint c : contours) {
            Rect r = Imgproc.boundingRect(c);
            double area = Imgproc.contourArea(c), extent = area / (r.width * r.height), aspect = (double) r.width / r.height;
            if (r.width >= 7 && r.width <= 40 && aspect >= 0.6 && aspect <= 1.4 && extent >= 0.60) {
                int globalX = zone.x + r.x + r.width / 2;
                int globalY = zone.y + r.y + r.height / 2;
                Imgproc.rectangle(warped, new Rect(zone.x + r.x, zone.y + r.y, r.width, r.height), new Scalar(0, 0, 255), 2);
                anchors.add(isXAnchor ? globalX : globalY);
            }
        }
        return anchors;
    }

    private static List<Integer> clusterLines(List<Integer> values) {
        if (values.isEmpty()) return new ArrayList<>();
        Collections.sort(values);
        List<Integer> clusters = new ArrayList<>();
        int currentSum = values.get(0), count = 1;

        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) - values.get(i-1) < 25) {
                currentSum += values.get(i); count++;
            } else {
                clusters.add(currentSum / count);
                currentSum = values.get(i); count = 1;
            }
        }
        clusters.add(currentSum / count);
        return clusters;
    }

    public static List<Rect> findCornerMarks(Mat src) {
        Mat gray = new Mat(), thresh = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(gray, thresh, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);

        List<MatOfPoint> cnts = new ArrayList<>();
        Imgproc.findContours(thresh, cnts, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Rect> res = new ArrayList<>();
        double imgArea = src.width() * src.height();

        for (MatOfPoint c : cnts) {
            Rect r = Imgproc.boundingRect(c);
            double area = Imgproc.contourArea(c);
            double aspect = (double) r.width / r.height;
            double extent = area / (r.width * r.height);

            boolean isScannerBorder = (r.x < 2 || r.y < 2 || r.x + r.width > src.cols() - 2 || r.y + r.height > src.rows() - 2);

            if (!isScannerBorder && area / imgArea > 0.0002 && area / imgArea < 0.02 && aspect > 0.6 && aspect < 1.5) {
                if (extent > 0.75) {
                    res.add(r);
                }
            }
        }

        if (res.size() > 4) {
            return filterTop4Corners(res, src.width(), src.height());
        }

        return res;
    }

    private static List<Rect> filterTop4Corners(List<Rect> rects, int width, int height) {
        Rect tl = rects.get(0), tr = rects.get(0), bl = rects.get(0), br = rects.get(0);
        double minDistTl = Double.MAX_VALUE, minDistTr = Double.MAX_VALUE;
        double minDistBl = Double.MAX_VALUE, minDistBr = Double.MAX_VALUE;

        for (Rect r : rects) {
            Point p = new Point(r.x + r.width / 2.0, r.y + r.height / 2.0);
            double dTl = Math.pow(p.x, 2) + Math.pow(p.y, 2);
            double dTr = Math.pow(width - p.x, 2) + Math.pow(p.y, 2);
            double dBl = Math.pow(p.x, 2) + Math.pow(height - p.y, 2);
            double dBr = Math.pow(width - p.x, 2) + Math.pow(height - p.y, 2);

            if (dTl < minDistTl) { minDistTl = dTl; tl = r; }
            if (dTr < minDistTr) { minDistTr = dTr; tr = r; }
            if (dBl < minDistBl) { minDistBl = dBl; bl = r; }
            if (dBr < minDistBr) { minDistBr = dBr; br = r; }
        }

        List<Rect> corners = new ArrayList<>();
        if (tl != null) corners.add(tl);
        if (tr != null && !corners.contains(tr)) corners.add(tr);
        if (br != null && !corners.contains(br)) corners.add(br);
        if (bl != null && !corners.contains(bl)) corners.add(bl);

        return corners;
    }

    public static Mat warpPerspective(Mat src, List<Rect> timingMarks, int targetW, int targetH) {
        List<Point> centers = timingMarks.stream().map(r -> new Point(r.x + r.width/2.0, r.y + r.height/2.0)).collect(Collectors.toList());
        Point tl=centers.get(0), tr=tl, bl=tl, br=tl;
        double minS=1e9, maxS=-1e9, maxD1=-1e9, maxD2=-1e9;
        for (Point p : centers) {
            double s=p.x+p.y, d1=p.x-p.y, d2=p.y-p.x;
            if(s<minS){minS=s; tl=p;} if(s>maxS){maxS=s; br=p;}
            if(d1>maxD1){maxD1=d1; tr=p;} if(d2>maxD2){maxD2=d2; bl=p;}
        }

        double padX = 40, padY = 40;
        MatOfPoint2f srcPoints = new MatOfPoint2f(tl, tr, br, bl);
        MatOfPoint2f dstPoints = new MatOfPoint2f(new Point(padX, padY), new Point(targetW - padX, padY), new Point(targetW - padX, targetH - padY), new Point(padX, targetH - padY));
        Mat m = Imgproc.getPerspectiveTransform(srcPoints, dstPoints);
        Mat w = new Mat();
        Imgproc.warpPerspective(src, w, m, new Size(targetW, targetH));
        return w;
    }

    private static String validatePart3Format(String val) {
        String cleanVal = val.replace("?", "");
        if (cleanVal.isEmpty()) return val;
        boolean isValid = true;
        int minusCount = 0, commaCount = 0;
        for (int i = 0; i < cleanVal.length(); i++) {
            char c = cleanVal.charAt(i);
            if (c == '-') { minusCount++; if (i > 0) isValid = false; }
            else if (c == ',') { commaCount++; if (i == 0 || (i > 0 && cleanVal.charAt(i - 1) == '-')) isValid = false; }
        }
        return (minusCount > 1 || commaCount > 1 || !isValid) ? "WARN_FMT_" + val : val;
    }

    private static void drawCoordinateRuler(Mat img) {
        Scalar colorRuler = new Scalar(200, 200, 200);
        Scalar colorText = new Scalar(0, 0, 255);
        for (int i = 0; i <= 100; i += 5) {
            int x = (int) (i * TARGET_WIDTH / 100.0);
            Imgproc.line(img, new Point(x, 0), new Point(x, 15), colorRuler, 1);
            Imgproc.line(img, new Point(x, TARGET_HEIGHT), new Point(x, TARGET_HEIGHT - 15), colorRuler, 1);
            if (i % 10 == 0) {
                Imgproc.line(img, new Point(x, 0), new Point(x, TARGET_HEIGHT), new Scalar(230, 230, 230), 1);
                Imgproc.putText(img, i + "%", new Point(x + 2, 30), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, colorText, 1);
            }
        }
        for (int j = 0; j <= 100; j += 5) {
            int y = (int) (j * TARGET_HEIGHT / 100.0);
            Imgproc.line(img, new Point(0, y), new Point(15, y), colorRuler, 1);
            Imgproc.line(img, new Point(TARGET_WIDTH, y), new Point(TARGET_WIDTH - 15, y), colorRuler, 1);
            if (j % 10 == 0) {
                Imgproc.line(img, new Point(0, y), new Point(TARGET_WIDTH, y), new Scalar(230, 230, 230), 1);
                Imgproc.putText(img, j + "%", new Point(20, y - 5), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, colorText, 1);
            }
        }
    }

    private static void drawAnchorIndices(Mat img, List<Integer> xAnchors, List<Integer> yAnchors) {
        Scalar colorX = new Scalar(0, 165, 255);
        Scalar colorY = new Scalar(255, 0, 255);

        for (int i = 0; i < xAnchors.size(); i++) {
            int x = xAnchors.get(i);
            Imgproc.line(img, new Point(x, 0), new Point(x, TARGET_HEIGHT), colorX, 2);
            Imgproc.putText(img, "X" + i, new Point(x + 5, 80), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, colorX, 3);
        }

        for (int i = 0; i < yAnchors.size(); i++) {
            int y = yAnchors.get(i);
            Imgproc.line(img, new Point(0, y), new Point(TARGET_WIDTH, y), colorY, 2);
            Imgproc.putText(img, "Y" + i, new Point(30, y - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, colorY, 3);
        }
    }
}