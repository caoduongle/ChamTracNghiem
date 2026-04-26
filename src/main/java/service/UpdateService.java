package service;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class UpdateService {
    // Phiên bản hiện tại của phần mềm
    public static final String CURRENT_VERSION = "1.5.0";

    private static final String VERSION_URL = "https://raw.githubusercontent.com/caoduongle/ChamTracNghiem/main/version.txt";
    private static final String DOWNLOAD_BASE_URL = "https://github.com/caoduongle/ChamTracNghiem/releases/download/v";

    // Truyền JFrame (thường là view) vào để hiển thị cửa sổ con lên giữa màn hình
    public static void checkForUpdates(JFrame parentView) {
        try {
            // 1. Đọc phiên bản trên mạng
            URL url = new URL(VERSION_URL);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String latestVersion = in.readLine().trim();
            in.close();

            // 2. So sánh phiên bản
            if (!CURRENT_VERSION.equals(latestVersion)) {
                int choice = JOptionPane.showConfirmDialog(parentView,
                        "Đã có phiên bản mới v" + latestVersion + "! Bạn có muốn cập nhật ngay không?\n(Phần mềm sẽ tự động khởi động lại sau khi tải xong)",
                        "Cập nhật phần mềm", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

                if (choice == JOptionPane.YES_OPTION) {
                    downloadAndApplyUpdate(parentView, latestVersion);
                }
            } else {
                // (Tùy chọn) Bật dòng này nếu muốn có nút "Kiểm tra cập nhật" thủ công trong cài đặt
                // JOptionPane.showMessageDialog(parentView, "Bạn đang sử dụng phiên bản mới nhất!");
            }
        } catch (Exception e) {
            System.out.println("Không thể kiểm tra bản cập nhật. Lỗi mạng hoặc Repo Private.");
        }
    }

    private static void downloadAndApplyUpdate(JFrame parentView, String latestVersion) {
        String downloadUrl = DOWNLOAD_BASE_URL + latestVersion + "/PhanMemChamThi_v" + latestVersion + ".exe";

        // Khởi tạo thanh tiến độ hiển thị %
        ProgressMonitor pm = new ProgressMonitor(parentView, "Đang tải bản cập nhật v" + latestVersion, "Đang kết nối máy chủ GitHub...", 0, 100);
        pm.setMillisToDecideToPopup(0);
        pm.setMillisToPopup(0);

        SwingWorker<Void, Object[]> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                HttpURLConnection httpConn = (HttpURLConnection) new URL(downloadUrl).openConnection();

                // BẮT BUỘC: Xử lý link chuyển hướng (Redirect) của GitHub
                int status = httpConn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_SEE_OTHER) {
                    String redirectUrl = httpConn.getHeaderField("Location");
                    httpConn = (HttpURLConnection) new URL(redirectUrl).openConnection();
                    status = httpConn.getResponseCode();
                }

                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Máy chủ trả về mã lỗi: " + status + ". File cập nhật có thể chưa được tải lên.");
                }

                long totalBytes = httpConn.getContentLengthLong(); // Lấy tổng dung lượng file

                try (InputStream in = new BufferedInputStream(httpConn.getInputStream());
                     FileOutputStream fos = new FileOutputStream("update_temp.exe")) {

                    byte[] data = new byte[65536]; // Tải từng cục 64KB
                    long downloadedBytes = 0;
                    int bytesRead;

                    while ((bytesRead = in.read(data)) != -1) {
                        // Cho phép người dùng bấm "Cancel" để hủy tải
                        if (isCancelled() || pm.isCanceled()) {
                            throw new InterruptedException("Người dùng đã hủy tiến trình tải.");
                        }

                        fos.write(data, 0, bytesRead);
                        downloadedBytes += bytesRead;

                        // Tính toán tiến độ % và số MB đã tải
                        if (totalBytes > 0) {
                            int progress = (int) ((downloadedBytes * 100) / totalBytes);
                            double downloadedMB = (double) downloadedBytes / (1024 * 1024);
                            double totalMB = (double) totalBytes / (1024 * 1024);
                            String note = String.format("Đã tải: %.1f MB / %.1f MB", downloadedMB, totalMB);

                            // Gửi tiến độ ra giao diện
                            publish(new Object[]{progress, note});
                        }
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                // Cập nhật thanh UI liên tục
                if (pm.isCanceled()) return;
                Object[] last = chunks.get(chunks.size() - 1);
                pm.setProgress((int) last[0]);
                pm.setNote((String) last[1]);
            }

            @Override
            protected void done() {
                pm.close();
                try {
                    get(); // Nếu luồng chạy có lỗi, get() sẽ ném ra Exception để catch ở dưới

                    // 1. THÔNG BÁO THÀNH CÔNG
                    JOptionPane.showMessageDialog(parentView,
                            "Tải bản cập nhật hoàn tất!\nPhần mềm sẽ tự động tắt và khởi động lại ngay bây giờ.",
                            "Cập nhật thành công", JOptionPane.INFORMATION_MESSAGE);

                    executeUpdaterScript(latestVersion);

                } catch (Exception ex) {
                    // 2. THÔNG BÁO THẤT BẠI / HỦY
                    if (ex.getMessage() != null && ex.getMessage().contains("hủy")) {
                        JOptionPane.showMessageDialog(parentView, "Đã hủy tải bản cập nhật.", "Thông báo", JOptionPane.WARNING_MESSAGE);
                        new File("update_temp.exe").delete(); // Xóa file tải dở
                    } else {
                        JOptionPane.showMessageDialog(parentView, "Lỗi khi cập nhật:\n" + ex.getMessage(), "Cập nhật thất bại", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        };
        worker.execute();
    }

    private static void executeUpdaterScript(String latestVersion) {
        try {
            String oldExeName = "PhanMemChamThi_v" + CURRENT_VERSION + ".exe";
            String newExeName = "PhanMemChamThi_v" + latestVersion + ".exe";

            String batCode =
                    "@echo off\r\n" +
                            "echo Dang cap nhat phan mem...\r\n" +
                            "timeout /t 5 /nobreak > NUL\r\n" +
                            "del \"" + oldExeName + "\"\r\n" +
                            "ren update_temp.exe \"" + newExeName + "\"\r\n" +
                            "start \"\" \"" + newExeName + "\"\r\n" +
                            "del \"%~f0\"";

            File batFile = new File("update.bat");
            FileWriter fw = new FileWriter(batFile);
            fw.write(batCode);
            fw.close();

            Runtime.getRuntime().exec("cmd /c start update.bat");
            System.exit(0); // Tắt phần mềm cũ đi

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}