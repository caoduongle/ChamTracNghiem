package view;

import javax.swing.*;
import java.awt.*;

public class HelpDialog extends JDialog {

    public HelpDialog(Window owner, String title, String htmlContent) {
        super(owner, "Hướng dẫn: " + title, ModalityType.APPLICATION_MODAL);
        setSize(480, 360);
        setLayout(new BorderLayout());
        setLocationRelativeTo(owner);

        // --- HEADER PANEL ---
        JPanel pnlHeader = new JPanel(new BorderLayout());
        pnlHeader.setBackground(new Color(0, 123, 255)); // Màu chủ đạo (Xanh dương)
        pnlHeader.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(Color.WHITE);
        pnlHeader.add(lblTitle, BorderLayout.CENTER);

        add(pnlHeader, BorderLayout.NORTH);

        // --- BODY PANEL ---
        JEditorPane editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setEditable(false);
        
        // Sử dụng phong cách thiết kế hiện đại, typography thoáng đãng
        editorPane.setText("<html><body style='font-family: \"Segoe UI\", Arial, sans-serif; font-size: 11pt; margin: 15px; color: #333333; line-height: 1.5;'>"
                + htmlContent + "</body></html>");
        editorPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // --- FOOTER PANEL ---
        JPanel pnlFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlFooter.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JButton btnClose = new JButton("Đã hiểu");
        btnClose.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnClose.setBackground(new Color(40, 167, 69)); // Nút thành công (Xanh lá)
        btnClose.setForeground(Color.WHITE);
        btnClose.setFocusPainted(false);
        btnClose.setPreferredSize(new Dimension(100, 32));
        btnClose.addActionListener(e -> dispose());
        pnlFooter.add(btnClose);

        add(pnlFooter, BorderLayout.SOUTH);
    }

    public static void showHelp(Component parent, String title, String htmlContent) {
        Window window = SwingUtilities.getWindowAncestor(parent);
        HelpDialog dialog = new HelpDialog(window, title, htmlContent);
        dialog.setVisible(true);
    }
}
