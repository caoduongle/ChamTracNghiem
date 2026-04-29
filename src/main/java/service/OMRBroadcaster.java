package service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class OMRBroadcaster {

    private static boolean isBroadcasting = false;
    private static final int UDP_PORT = 8888;
    private static final int TCP_PORT = 8080;

    // Header chuẩn để App nhận diện đây là tín hiệu của hệ thống mình
    private static final String HEADER = "OMR_DISCOVERY";

    public static void startBroadcasting() {
        if (isBroadcasting) return;
        isBroadcasting = true;

        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);

                // Tự động lấy tên máy tính PC
                String pcName = InetAddress.getLocalHost().getHostName();
                // Bạn có thể custom thêm: String pcName = "Máy của Lễ - " + InetAddress.getLocalHost().getHostName();

                System.out.println("[BROADCAST] Đang phát tín hiệu tìm kiếm cho máy: " + pcName);

                while (isBroadcasting) {
                    String localIp = getLocalIPv4Address();
                    if (localIp != null) {
                        // Gói tin chuẩn hóa phân cách bằng dấu gạch đứng (|)
                        // Định dạng: OMR_DISCOVERY|Tên_Máy|IP:Port
                        String message = HEADER + "|" + pcName + "|" + localIp + ":" + TCP_PORT;
                        byte[] buffer = message.getBytes();

                        DatagramPacket packet = new DatagramPacket(
                                buffer, buffer.length,
                                InetAddress.getByName("255.255.255.255"), UDP_PORT);

                        socket.send(packet);
                    }
                    Thread.sleep(1500); // Rút ngắn thời gian nghỉ để App quét nhanh hơn
                }
            } catch (Exception e) {
                System.err.println("[BROADCAST LỖI] " + e.getMessage());
            }
        }).start();
    }

    public static void stopBroadcasting() {
        isBroadcasting = false;
    }

    private static String getLocalIPv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.getDisplayName().contains("Virtual")) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.getHostAddress().contains(".")) return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}