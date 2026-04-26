package service;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;
import javax.swing.*;

public class WindowPersistenceManager {
    private static final Preferences prefs = Preferences.userRoot().node("ChamTracNghiem_N7_WindowSettings");

    // ========================================================
    // 1. DÀNH CHO JFRAME (Cửa sổ chính - MainView)
    // ========================================================
    public static void restoreWindow(JFrame frame, String windowName, int defaultWidth, int defaultHeight) {
        int x = prefs.getInt(windowName + "_x", -1);
        int y = prefs.getInt(windowName + "_y", -1);
        int w = prefs.getInt(windowName + "_w", defaultWidth);
        int h = prefs.getInt(windowName + "_h", defaultHeight);
        boolean maximized = prefs.getBoolean(windowName + "_max", false);

        if (x != -1 && y != -1) {
            frame.setBounds(x, y, w, h);
            if (!isWindowVisible(frame)) frame.setLocationRelativeTo(null);
        } else {
            frame.setSize(defaultWidth, defaultHeight);
            frame.setLocationRelativeTo(null);
        }
        if (maximized) frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    public static void attachSaver(JFrame frame, String windowName) {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) { saveFrameSettings(frame, windowName); }
            @Override
            public void windowClosing(WindowEvent e) { saveFrameSettings(frame, windowName); }
        });
    }

    private static void saveFrameSettings(JFrame frame, String windowName) {
        if (!service.DataManager.isAutoSavePosition()) return;
        int state = frame.getExtendedState();
        boolean isMaximized = (state & JFrame.MAXIMIZED_BOTH) != 0;
        prefs.putBoolean(windowName + "_max", isMaximized);

        if (!isMaximized) {
            Rectangle bounds = frame.getBounds();
            prefs.putInt(windowName + "_x", bounds.x);
            prefs.putInt(windowName + "_y", bounds.y);
            prefs.putInt(windowName + "_w", bounds.width);
            prefs.putInt(windowName + "_h", bounds.height);
        }
    }

    // ========================================================
    // 2. DÀNH CHO JDIALOG (Các cửa sổ phụ)
    // ========================================================
    public static void restoreWindow(JDialog dialog, String windowName, int defaultWidth, int defaultHeight) {
        int x = prefs.getInt(windowName + "_x", -1);
        int y = prefs.getInt(windowName + "_y", -1);
        int w = prefs.getInt(windowName + "_w", defaultWidth);
        int h = prefs.getInt(windowName + "_h", defaultHeight);

        if (x != -1 && y != -1) {
            dialog.setBounds(x, y, w, h);
            // Căn giữa dựa trên cửa sổ cha nếu bị trôi ra khỏi màn hình
            if (!isWindowVisible(dialog)) dialog.setLocationRelativeTo(dialog.getParent());
        } else {
            dialog.setSize(defaultWidth, defaultHeight);
            dialog.setLocationRelativeTo(dialog.getParent());
        }
    }

    public static void attachSaver(JDialog dialog, String windowName) {
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) { saveDialogSettings(dialog, windowName); }
            @Override
            public void windowClosing(WindowEvent e) { saveDialogSettings(dialog, windowName); }
        });
    }

    private static void saveDialogSettings(JDialog dialog, String windowName) {
        if (!service.DataManager.isAutoSavePosition()) return;
        Rectangle bounds = dialog.getBounds();
        prefs.putInt(windowName + "_x", bounds.x);
        prefs.putInt(windowName + "_y", bounds.y);
        prefs.putInt(windowName + "_w", bounds.width);
        prefs.putInt(windowName + "_h", bounds.height);
    }

    // --- HÀM KIỂM TRA AN TOÀN (Tránh cửa sổ bị lọt ra ngoài màn hình) ---
    private static boolean isWindowVisible(Window window) {
        GraphicsConfiguration config = window.getGraphicsConfiguration();
        if (config == null) return false;
        Rectangle screenBounds = config.getBounds();
        return screenBounds.intersects(window.getBounds());
    }
}