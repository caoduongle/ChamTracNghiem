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

                System.out.println("[BROADCAST] Đang phát tín hiệu tìm kiếm cho máy: " + pcName);

                while (isBroadcasting) {
                    String localIp = getLocalIPv4Address();
                    if (localIp != null) {
                        // Gói tin chuẩn hóa phân cách bằng dấu gạch đứng (|)
                        // Định dạng: OMR_DISCOVERY|Tên_Máy|IP:Port
                        String message = HEADER + "|" + pcName + "|" + localIp + ":" + TCP_PORT;
                        byte[] buffer = message.getBytes();

                        // 1. Gửi qua địa chỉ quảng bá toàn cục 255.255.255.255
                        try {
                            DatagramPacket globalPacket = new DatagramPacket(
                                    buffer, buffer.length,
                                    InetAddress.getByName("255.255.255.255"), UDP_PORT);
                            socket.send(globalPacket);
                        } catch (Exception ignored) {}

                        // 2. Gửi qua địa chỉ quảng bá riêng của từng card mạng để chọc thủng định tuyến hệ điều hành
                        try {
                            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                            while (interfaces.hasMoreElements()) {
                                NetworkInterface iface = interfaces.nextElement();
                                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                                String displayName = iface.getDisplayName().toLowerCase();
                                String ifaceName = iface.getName().toLowerCase();

                                if (displayName.contains("virtual") || displayName.contains("vmware") || 
                                    displayName.contains("virtualbox") || displayName.contains("wsl") || 
                                    displayName.contains("vpn") || displayName.contains("tap") || 
                                    displayName.contains("tun") || displayName.contains("zerotier") || 
                                    displayName.contains("radmin") || displayName.contains("hamachi") ||
                                    ifaceName.contains("vbox") || ifaceName.contains("wsl") || 
                                    ifaceName.contains("tap") || ifaceName.contains("tun")) {
                                    continue;
                                }

                                for (java.net.InterfaceAddress interfaceAddress : iface.getInterfaceAddresses()) {
                                    InetAddress broadcast = interfaceAddress.getBroadcast();
                                    if (broadcast != null) {
                                        DatagramPacket packet = new DatagramPacket(
                                                buffer, buffer.length,
                                                broadcast, UDP_PORT);
                                        socket.send(packet);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
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
            String bestIp = null;
            int bestScore = -9999;

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                String name = iface.getDisplayName().toLowerCase();
                String ifaceName = iface.getName().toLowerCase();

                if (iface.isLoopback() || !iface.isUp()) continue;

                int score = 0;

                // Ưu tiên cực cao Wi-Fi/Wireless
                if (name.contains("wi-fi") || name.contains("wifi") || name.contains("wireless") || name.contains("wlan") || name.contains("802.11")) {
                    score += 1000;
                } else if (name.contains("ethernet")) {
                    score += 100;
                }

                // Loại bỏ hoàn toàn card ảo/VPN/WSL
                if (name.contains("virtual") || name.contains("vmware") || name.contains("vethernet") ||
                    name.contains("wsl") || name.contains("virtualbox") || name.contains("vbox") ||
                    name.contains("vpn") || name.contains("tap") || name.contains("tun") ||
                    name.contains("zerotier") || name.contains("radmin") || name.contains("hamachi") ||
                    name.contains("host-only") || name.contains("pseudo") ||
                    ifaceName.contains("vbox") || ifaceName.contains("wsl") || ifaceName.contains("tap") || ifaceName.contains("tun")) {
                    score -= 5000;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        int ipScore = score;

                        if (ip.startsWith("192.168.56.")) {
                            ipScore -= 500; // Trừ điểm card ảo VirtualBox mặc định
                        }
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            ipScore += 50;
                        }

                        if (ipScore > bestScore) {
                            bestScore = ipScore;
                            bestIp = ip;
                        }
                    }
                }
            }
            if (bestIp != null) return bestIp;
        } catch (Exception ignored) {}
        return null;
    }
}