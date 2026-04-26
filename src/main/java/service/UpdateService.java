package service;

import javax.swing.*;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class UpdateService {
    // Phiên bản hiện tại của phần mềm
    public static final String CURRENT_VERSION = "1.2.0";

    // ĐÃ FIX 1: Dùng link Raw gốc, không có token (Yêu cầu repo GitHub phải là Public)
    private static final String VERSION_URL = "https://raw.githubusercontent.com/caoduongle/ChamTracNghiem/main/version.txt";

    // ĐÃ FIX 2: Bỏ đi phần đuôi cố định để tự động ghép version mới vào
    private static final String DOWNLOAD_BASE_URL = "https://github.com/caoduongle/ChamTracNghiem/releases/download/v";

    public static void checkForUpdates() {
        try {
            // 1. Đọc phiên bản trên mạng
            URL url = new URL(VERSION_URL);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String latestVersion = in.readLine().trim();
            in.close();

            // 2. So sánh phiên bản
            if (!CURRENT_VERSION.equals(latestVersion)) {
                int choice = JOptionPane.showConfirmDialog(null,
                        "Đã có phiên bản mới v" + latestVersion + "! Bạn có muốn cập nhật ngay không?\n(Phần mềm sẽ tự động khởi động lại sau khi tải xong)",
                        "Cập nhật phần mềm Team N7", JOptionPane.YES_NO_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    downloadAndApplyUpdate(latestVersion); // Truyền version mới vào hàm
                }
            }
        } catch (Exception e) {
            System.out.println("Không thể kiểm tra bản cập nhật. Vui lòng kiểm tra kết nối mạng hoặc Repo đang ở chế độ Private.");
        }
    }

    private static void downloadAndApplyUpdate(String latestVersion) {
        try {
            JOptionPane.showMessageDialog(null, "Đang tải bản cập nhật v" + latestVersion + ", vui lòng đợi trong giây lát...", "Đang tải", JOptionPane.INFORMATION_MESSAGE);

            // ĐÃ FIX 2: Tự động ghép link tải chính xác dựa vào latestVersion
            String downloadUrl = DOWNLOAD_BASE_URL + latestVersion + "/PhanMemChamThi_v" + latestVersion + ".exe";

            URL website = new URL(downloadUrl);
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream("update_temp.exe");
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();

            // 4. Gọi script tráo đổi file và truyền version mới vào
            executeUpdaterScript(latestVersion);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Lỗi khi tải bản cập nhật: " + e.getMessage());
        }
    }

    // ĐÃ FIX 3: Dùng biến động cho file .bat để xài được cho mọi lần update (1.3, 1.4, 2.0...)
    private static void executeUpdaterScript(String latestVersion) {
        try {
            String oldExeName = "PhanMemChamThi_v" + CURRENT_VERSION + ".exe";
            String newExeName = "PhanMemChamThi_v" + latestVersion + ".exe";

            String batCode =
                    "@echo off\r\n" +
                            "echo Dang cap nhat phan mem...\r\n" +
                            "timeout /t 3 /nobreak > NUL\r\n" +
                            "del \"" + oldExeName + "\"\r\n" +
                            "ren update_temp.exe \"" + newExeName + "\"\r\n" +
                            "start \"\" \"" + newExeName + "\"\r\n" +
                            "del \"%~f0\"";

            // Tạo file update.bat
            File batFile = new File("update.bat");
            FileWriter fw = new FileWriter(batFile);
            fw.write(batCode);
            fw.close();

            // Chạy file bat
            Runtime.getRuntime().exec("cmd /c start update.bat");

            // TẮT PHẦN MỀM HIỆN TẠI NGAY LẬP TỨC
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}