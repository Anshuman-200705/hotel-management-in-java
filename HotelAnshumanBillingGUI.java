import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import com.formdev.flatlaf.FlatLightLaf;

public class HotelAnshumanBillingGUI extends JFrame {

    // ---------- Database Info ----------
    static final String URL = "jdbc:mysql://localhost:3306/hotelanshuman";
    static final String USER = "root";
    static final String PASSWORD = "Anshuman05@27";
    static final double GST_RATE = 0.05; // 5%
    static final DecimalFormat df = new DecimalFormat("0.00");

    // ---------- GUI Components ----------
    private JComboBox<String> categoryComboBox, itemComboBox;
    private JTextField qtyField, customerNameField, addressField;
    private JTable billTable;
    private DefaultTableModel tableModel;
    private JLabel subtotalLabel, gstLabel, totalLabel;

    // ---------- Data Storage ----------
    private final Map<String, Map<String, Double>> menuData = new HashMap<>();
    private int lastOrderId = -1; // To track the last saved order ID
    private JDialog pastOrdersDialog; // Keep a reference to the orders dialog
    
    // Payment Options
    private static final String[] PAYMENT_OPTIONS = {"Cash", "UPI", "Card"};


    // --- UI Theme ---
    private static class UITheme {
        static final Color PRIMARY_COLOR = new Color(70, 130, 180);
        static final Color ACCENT_COLOR_GREEN = new Color(34, 139, 34);
        static final Color ACCENT_COLOR_ORANGE = new Color(255, 140, 0);
        static final Color ACCENT_COLOR_RED = new Color(178, 34, 34);
        static final Color BACKGROUND_COLOR = new Color(248, 249, 250);
        static final Color PANEL_BACKGROUND = Color.WHITE;
        static final Color TEXT_COLOR = new Color(33, 37, 41);
        static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
        static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD, 16);
        static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 14);
        static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 14);
        static final Font FONT_LABEL_BOLD = new Font("Segoe UI", Font.BOLD, 14);
        static final EmptyBorder MAIN_PADDING = new EmptyBorder(15, 15, 15, 15);
    }

    public HotelAnshumanBillingGUI() {
        setTitle("🏨 Hotel Anshuman Billing System");
        setSize(1000, 750); // Increased width slightly
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        
        // Ensure FlatLaf is initialized (optional but good practice with FlatLaf import)
        try {
            FlatLightLaf.setup();
        } catch (Exception e) {
            System.err.println("Failed to initialize FlatLaf: " + e.getMessage());
        }

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(UITheme.BACKGROUND_COLOR);
        mainPanel.setBorder(UITheme.MAIN_PADDING);
        setContentPane(mainPanel);

        loadMenuData();

        // Header
        JLabel title = new JLabel("HOTEL ANSHUMAN BILLING SYSTEM", JLabel.CENTER);
        title.setFont(UITheme.FONT_TITLE);
        title.setOpaque(true);
        title.setBackground(UITheme.PRIMARY_COLOR);
        title.setForeground(Color.WHITE);
        title.setBorder(new EmptyBorder(15, 10, 15, 10));
        add(title, BorderLayout.NORTH);

        // Top Panel (Customer + Item Selection)
        JPanel topPanel = new JPanel(new BorderLayout(15, 15));
        topPanel.setOpaque(false);

        // Customer Panel
        JPanel customerPanel = createStyledPanel("Customer Info (Optional)");
        customerPanel.setLayout(new GridLayout(2, 2, 10, 10));
        customerPanel.add(new JLabel("Customer Name:"));
        customerNameField = new JTextField();
        customerPanel.add(customerNameField);
        customerPanel.add(new JLabel("Address:"));
        addressField = new JTextField();
        customerPanel.add(addressField);

        // Item Panel
        JPanel itemPanel = createStyledPanel("Select Items");
        itemPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        itemPanel.add(new JLabel("Category:"));
        categoryComboBox = new JComboBox<>(menuData.keySet().toArray(new String[0]));
        categoryComboBox.addActionListener(e -> updateItemDropdown());
        itemPanel.add(categoryComboBox);
        itemPanel.add(new JLabel("Item:"));
        itemComboBox = new JComboBox<>();
        updateItemDropdown();
        itemPanel.add(itemComboBox);
        itemPanel.add(new JLabel("Quantity:"));
        qtyField = new JTextField("1", 5);
        itemPanel.add(qtyField);
        JButton addButton = new JButton("➕ Add Item");
        styleButton(addButton, UITheme.ACCENT_COLOR_GREEN);
        addButton.addActionListener(e -> addItemToBill());
        itemPanel.add(addButton);

        topPanel.add(customerPanel, BorderLayout.WEST);
        topPanel.add(itemPanel, BorderLayout.CENTER);

        // Table Panel
        JPanel tablePanel = createStyledPanel("Current Order Details");
        tablePanel.setLayout(new BorderLayout());
        // Note: The Price column stores the *unit* price, not total price.
        String[] columns = {"Item Name", "Quantity", "Unit Price", "Total"}; 
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1) return Integer.class;
                return super.getColumnClass(columnIndex);
            }
        };
        billTable = new JTable(tableModel);
        billTable.setFont(UITheme.FONT_BODY);
        billTable.setRowHeight(28);
        billTable.getTableHeader().setFont(UITheme.FONT_HEADING);
        billTable.getTableHeader().setBackground(UITheme.PRIMARY_COLOR);
        billTable.getTableHeader().setForeground(Color.WHITE);
        JScrollPane tableScroll = new JScrollPane(billTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        
        // --- Bill Modification Panel (For current bill) ---
        JPanel tableActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        tableActionsPanel.setOpaque(false);
        JButton updateQtyButton = new JButton("🔄 Update Selected Qty");
        styleButton(updateQtyButton, UITheme.ACCENT_COLOR_ORANGE);
        updateQtyButton.addActionListener(e -> updateSelectedItemQuantity());
        JButton removeItemButton = new JButton("❌ Remove Selected Item");
        styleButton(removeItemButton, UITheme.ACCENT_COLOR_RED);
        removeItemButton.addActionListener(e -> removeSelectedItem());
        tableActionsPanel.add(updateQtyButton);
        tableActionsPanel.add(removeItemButton);
        
        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.add(tableScroll, BorderLayout.CENTER);
        tableContainer.add(tableActionsPanel, BorderLayout.SOUTH);
        tablePanel.add(tableContainer, BorderLayout.CENTER);

        // Bottom Panel
        JPanel bottomPanel = new JPanel(new BorderLayout(15, 15));
        bottomPanel.setOpaque(false);
        JPanel summaryPanel = createStyledPanel("Bill Summary");
        summaryPanel.setLayout(new GridLayout(3, 1, 5, 5));
        subtotalLabel = new JLabel("Subtotal: ₹0.00");
        gstLabel = new JLabel("GST (5%): ₹0.00");
        totalLabel = new JLabel("Total: ₹0.00");
        subtotalLabel.setFont(UITheme.FONT_LABEL_BOLD);
        gstLabel.setFont(UITheme.FONT_LABEL_BOLD);
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        summaryPanel.add(subtotalLabel);
        summaryPanel.add(gstLabel);
        summaryPanel.add(totalLabel);
        
        // Actions Panel - Save, View Orders, Exit
        JPanel actionsPanel = createStyledPanel("Actions");
        actionsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        
        // 1. Process Payment & Save Button
        JButton saveOrderButton = new JButton("💳 Process Payment & Save Order");
        styleButton(saveOrderButton, UITheme.ACCENT_COLOR_GREEN);
        saveOrderButton.addActionListener(e -> saveCurrentOrder()); // This will now trigger the payment dialog
        
        JButton viewOrdersButton = new JButton("📜 View Past Orders");
        styleButton(viewOrdersButton, UITheme.PRIMARY_COLOR);
        viewOrdersButton.addActionListener(e -> showPastOrders());
        
        JButton exitButton = new JButton("🚪 Exit");
        styleButton(exitButton, UITheme.ACCENT_COLOR_RED);
        exitButton.addActionListener(e -> System.exit(0));

        actionsPanel.add(saveOrderButton);
        actionsPanel.add(viewOrdersButton);
        actionsPanel.add(exitButton);
        
        bottomPanel.add(summaryPanel, BorderLayout.CENTER);
        bottomPanel.add(actionsPanel, BorderLayout.EAST); 

        // Combining Panels
        JPanel centerContentPanel = new JPanel(new BorderLayout(15, 15));
        centerContentPanel.setOpaque(false);
        centerContentPanel.add(topPanel, BorderLayout.NORTH);
        centerContentPanel.add(tablePanel, BorderLayout.CENTER);
        add(centerContentPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // --- Utility Methods ---

    private JPanel createStyledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(UITheme.PANEL_BACKGROUND);
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1), title,
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            UITheme.FONT_HEADING, UITheme.TEXT_COLOR
        ));
        return panel;
    }

    private void styleButton(JButton btn, Color bgColor) {
        btn.setFont(UITheme.FONT_BUTTON);
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                btn.setBackground(bgColor.brighter());
            }
            public void mouseExited(MouseEvent evt) {
                btn.setBackground(bgColor);
            }
        });
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void showWarning(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.WARNING_MESSAGE);
    }

    // Helper to make text safe for PDF when no Unicode font is embedded.
    // If unicodeAvailable is false, replace the rupee symbol with "Rs." to avoid PDFBox encoding errors.
    private static String pdfSafe(String text, boolean unicodeAvailable) {
        if (text == null) return "";
        if (unicodeAvailable) return text;
        return text.replace("₹", "Rs.");
    }

    private void loadMenuData() {
        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT category, name, price FROM menu_items ORDER BY category, name")) {
            while (rs.next()) {
                menuData.computeIfAbsent(rs.getString("category"), k -> new HashMap<>())
                    .put(rs.getString("name"), rs.getDouble("price"));
            }
        } catch (SQLException e) {
            showError("Database Error", "Error loading menu: " + e.getMessage());
        }
    }
    
    // --- Current Bill Management ---

    private void updateItemDropdown() {
        String selectedCategory = (String) categoryComboBox.getSelectedItem();
        itemComboBox.removeAllItems();
        if (selectedCategory != null && menuData.containsKey(selectedCategory)) {
            menuData.get(selectedCategory).keySet().stream().sorted().forEach(itemComboBox::addItem);
        }
    }

    private void addItemToBill() {
        String category = (String) categoryComboBox.getSelectedItem();
        String item = (String) itemComboBox.getSelectedItem();
        if (category == null || item == null) {
            showWarning("Selection Error", "Please select an item.");
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(qtyField.getText().trim());
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("Input Error", "Please enter a valid positive quantity!");
            return;
        }
        
        double price = menuData.get(category).get(item);
        
        // Check if item is already in the bill and update quantity if it is
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getValueAt(i, 0).equals(item)) {
                int currentQty = (int) tableModel.getValueAt(i, 1);
                int newQty = currentQty + qty;
                updateTableRow(i, newQty, price);
                qtyField.setText("1");
                return;
            }
        }
        
        // Add new item if not found
        tableModel.addRow(new Object[]{item, qty, df.format(price), df.format(price * qty)});
        updateTotals();
        qtyField.setText("1");
    }

    private void updateTableRow(int row, int newQty, double unitPrice) {
        if (newQty <= 0) {
            tableModel.removeRow(row);
        } else {
            tableModel.setValueAt(newQty, row, 1);
            tableModel.setValueAt(df.format(unitPrice * newQty), row, 3);
        }
        updateTotals();
    }
    
    private void updateSelectedItemQuantity() {
        int selectedRow = billTable.getSelectedRow();
        if (selectedRow == -1) {
            showWarning("Selection Error", "Please select an item from the bill table to update.");
            return;
        }
        
        String item = tableModel.getValueAt(selectedRow, 0).toString();
        // Unit price is a formatted string, need to parse it back
        double unitPrice = Double.parseDouble(tableModel.getValueAt(selectedRow, 2).toString());

        String currentQtyStr = tableModel.getValueAt(selectedRow, 1).toString();
        String newQtyStr = JOptionPane.showInputDialog(this, 
                                                    "Enter new quantity for " + item + ":", 
                                                    currentQtyStr);

        if (newQtyStr != null) {
            try {
                int newQty = Integer.parseInt(newQtyStr.trim());
                if (newQty <= 0) {
                    int confirm = JOptionPane.showConfirmDialog(this, 
                                                                "Quantity must be greater than zero. Do you want to remove the item instead?", 
                                                                "Remove Item?", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        tableModel.removeRow(selectedRow);
                        updateTotals();
                    }
                } else {
                    updateTableRow(selectedRow, newQty, unitPrice);
                }
            } catch (NumberFormatException e) {
                showError("Input Error", "Invalid quantity entered.");
            }
        }
    }
    
    private void removeSelectedItem() {
        int selectedRow = billTable.getSelectedRow();
        if (selectedRow == -1) {
            showWarning("Selection Error", "Please select an item from the bill table to remove.");
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this, 
                                                    "Are you sure you want to remove " + tableModel.getValueAt(selectedRow, 0) + " from the bill?", 
                                                    "Confirm Removal", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            tableModel.removeRow(selectedRow);
            updateTotals();
        }
    }


    private void updateTotals() {
        double subtotal = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            // Note: Total column is a String (df.format), so we must parse it back to double
            subtotal += Double.parseDouble(tableModel.getValueAt(i, 3).toString()); 
        }
        double gst = subtotal * GST_RATE;
        double total = subtotal + gst;
        subtotalLabel.setText("Subtotal: ₹" + df.format(subtotal));
        gstLabel.setText("GST (5%): ₹" + df.format(gst));
        totalLabel.setText("Total: ₹" + df.format(total));
    }

    private void resetForm() {
        tableModel.setRowCount(0);
        customerNameField.setText("");
        addressField.setText("");
        updateTotals();
    }
    
    // --- Database Save/Load (Orders) ---

    private void saveCurrentOrder() {
        if (tableModel.getRowCount() == 0) {
            showWarning("Billing Error", "The bill is empty. Please add at least one item!");
            return;
        }

        // --- Payment Method Selection Dialog ---
        JComboBox<String> paymentMethodBox = new JComboBox<>(PAYMENT_OPTIONS);
        paymentMethodBox.setSelectedItem("Cash"); // Default to Cash
        
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Select Payment Method:"));
        panel.add(paymentMethodBox);
        
        int result = JOptionPane.showConfirmDialog(this, panel, 
                "Finalize Payment for Grand Total: " + totalLabel.getText().replace("Total: ", ""), 
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result != JOptionPane.OK_OPTION) {
            return; // User cancelled payment
        }
        
        String paymentMethod = (String) paymentMethodBox.getSelectedItem();
        
        // --- Calculation and Saving ---
        String name = customerNameField.getText().trim();
        String address = addressField.getText().trim();
        
        double subtotal = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            subtotal += Double.parseDouble(tableModel.getValueAt(i, 3).toString());
        }
        double gst = subtotal * GST_RATE;
        double total = subtotal + gst;

        int orderId = saveOrder(name.isEmpty() ? "N/A" : name, address.isEmpty() ? "N/A" : address, subtotal, gst, total, paymentMethod);
        
        if (orderId != -1) {
            saveOrderItems(orderId);
            lastOrderId = orderId; 
            
            int option = JOptionPane.showConfirmDialog(this, 
                "✅ Order #" + orderId + " successfully saved!\nPayment Method: " + paymentMethod + "\nDo you want to generate a PDF bill now?", 
                "Success", JOptionPane.YES_NO_OPTION);
            
            if (option == JOptionPane.YES_OPTION) {
                generatePdfBill(orderId); // Call the PDF generation
            }
            
            resetForm();
        }
    }

    private int saveOrder(String name, String address, double subtotal, double gst, double total, String paymentMethod) {
        // MODIFIED SQL to include payment_method
        String sql = "INSERT INTO orders (customer_name, address, total_amount, gst, grand_total, payment_method, order_date) VALUES (?, ?, ?, ?, ?, ?, NOW())";
        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, address);
            ps.setDouble(3, subtotal);
            ps.setDouble(4, gst);
            ps.setDouble(5, total);
            ps.setString(6, paymentMethod); // Set payment method
            ps.executeUpdate();
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (SQLException e) {
            showError("Database Error", "Error saving order: " + e.getMessage());
        }
        return -1;
    }

    private void saveOrderItems(int orderId) {
        String sql = "INSERT INTO order_items (order_id, item_name, quantity, price) VALUES (?, ?, ?, ?)";
        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                ps.setInt(1, orderId);
                ps.setString(2, tableModel.getValueAt(i, 0).toString()); // name
                ps.setInt(3, (int) tableModel.getValueAt(i, 1)); // qty is now an Integer
                // Unit price is at index 2, Total is at index 3
                ps.setDouble(4, Double.parseDouble(tableModel.getValueAt(i, 2).toString())); // unit price
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            showError("Database Error", "Error saving order items: " + e.getMessage());
        }
    }

    private Map<String, Object> getOrderDetails(int orderId) {
        Map<String, Object> data = new HashMap<>();
        // MODIFIED SQL to select payment_method
        String orderSql = "SELECT *, payment_method FROM orders WHERE id = ?";
        String itemsSql = "SELECT id, item_name, quantity, price FROM order_items WHERE order_id = ?";

        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement orderPs = con.prepareStatement(orderSql);
             PreparedStatement itemsPs = con.prepareStatement(itemsSql)) {
            
            // Get Order Info
            orderPs.setInt(1, orderId);
            ResultSet orderRs = orderPs.executeQuery();
            if (orderRs.next()) {
                data.put("customer_name", orderRs.getString("customer_name"));
                data.put("address", orderRs.getString("address"));
                data.put("total_amount", orderRs.getDouble("total_amount"));
                data.put("gst", orderRs.getDouble("gst"));
                data.put("grand_total", orderRs.getDouble("grand_total"));
                data.put("payment_method", orderRs.getString("payment_method")); // Retrieve payment method
                Timestamp ts = orderRs.getTimestamp("order_date");
                data.put("order_date", ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss")));
            } else {
                return data; 
            }

            // Get Items Info - Includes item_id (PK) for deletion/update
            itemsPs.setInt(1, orderId);
            ResultSet itemsRs = itemsPs.executeQuery();
            // Columns: Item ID (Hidden), Item, Qty, Price, Total
            DefaultTableModel itemsModel = new DefaultTableModel(new String[]{"Item ID", "Item", "Qty", "Price", "Total"}, 0);
            while (itemsRs.next()) {
                double unitPrice = itemsRs.getDouble("price");
                int qty = itemsRs.getInt("quantity");
                itemsModel.addRow(new Object[]{
                    itemsRs.getInt("id"), // Item ID (PK of order_items)
                    itemsRs.getString("item_name"),
                    qty,
                    df.format(unitPrice),
                    df.format(unitPrice * qty)
                });
            }
            data.put("items", itemsModel);

        } catch (SQLException e) {
            showError("Database Error", "Error fetching order details: " + e.getMessage());
            data.clear();
        }
        return data;
    }
    
    // NEW HELPER METHOD for updating totals after item deletion/update
    private void updateOrderTotalsInDB(int orderId, double subtotal, double gst, double total) {
        String sql = "UPDATE orders SET total_amount = ?, gst = ?, grand_total = ? WHERE id = ?";
        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, subtotal);
            ps.setDouble(2, gst);
            ps.setDouble(3, total);
            ps.setInt(4, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("Database Error", "Error updating order totals: " + e.getMessage());
        }
    }


    // --- Past Order Management (With Delete Button Logic) ---
    
    private void showPastOrders() {
        pastOrdersDialog = new JDialog(this, "📜 Past Orders - Double Click to Edit", true);
        pastOrdersDialog.setSize(900, 500); // Increased width to fit Payment Method
        pastOrdersDialog.setLocationRelativeTo(this);

        // MODIFIED: Added Payment Method column
        String[] cols = {"Order ID", "Customer", "Payment Method", "Total", "Date/Time"}; 
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable orderTable = new JTable(model);
        orderTable.setFont(UITheme.FONT_BODY);
        orderTable.setRowHeight(28);
        orderTable.getColumnModel().getColumn(2).setPreferredWidth(120); // Payment Method width

        JTableHeader header = orderTable.getTableHeader();
        header.setFont(UITheme.FONT_HEADING);
        header.setBackground(UITheme.PRIMARY_COLOR);
        header.setForeground(Color.WHITE);

        orderTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = orderTable.getSelectedRow();
                    if (row != -1) {
                        int orderId = (Integer) orderTable.getValueAt(row, 0);
                        showOrderEditDialog(orderId, model); // Pass the model to allow refresh
                    }
                }
            }
        });

        loadPastOrdersToTable(model);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // --- Action Panel for Delete Button ---
        JPanel bottomActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton deleteButton = new JButton("❌ Delete Selected Order Permanently");
        styleButton(deleteButton, UITheme.ACCENT_COLOR_RED);
        deleteButton.addActionListener(e -> {
            int selectedRow = orderTable.getSelectedRow();
            if (selectedRow == -1) {
                showWarning("Selection Error", "Please select an order from the list to delete.");
                return;
            }
            
            int orderId = (Integer) orderTable.getValueAt(selectedRow, 0);
            String customerName = orderTable.getValueAt(selectedRow, 1).toString();
            
            int confirm = JOptionPane.showConfirmDialog(pastOrdersDialog, 
                "WARNING: Are you sure you want to permanently delete Order #" + orderId + " (Customer: " + customerName + ")?\nThis action cannot be undone.", 
                "Confirm Permanent Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                
            if (confirm == JOptionPane.YES_OPTION) {
                deleteOrderCompletely(orderId);
                loadPastOrdersToTable(model); // Refresh the table after deletion
            }
        });
        
        bottomActionPanel.add(new JLabel("Double-click to view/edit."));
        bottomActionPanel.add(deleteButton);


        mainPanel.add(new JScrollPane(orderTable), BorderLayout.CENTER);
        mainPanel.add(bottomActionPanel, BorderLayout.SOUTH); // ADDED: Button panel

        pastOrdersDialog.add(mainPanel);
        pastOrdersDialog.setVisible(true);
    }
    
    private void loadPastOrdersToTable(DefaultTableModel model) {
        model.setRowCount(0); // Clear existing rows
        // MODIFIED: Selecting payment_method from the database
        String sql = "SELECT id, customer_name, grand_total, payment_method, order_date FROM orders ORDER BY order_date DESC";
        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("customer_name"),
                    rs.getString("payment_method"), // Added Payment Method
                    "₹" + df.format(rs.getDouble("grand_total")),
                    rs.getTimestamp("order_date").toString(),
                });
            }
        } catch (SQLException e) {
            showError("Database Error", "Error loading orders: " + e.getMessage());
        }
    }
    
    private void deleteOrderCompletely(int orderId) {
        // Delete items first due to foreign key constraint
        String deleteItemsSql = "DELETE FROM order_items WHERE order_id = ?";
        String deleteOrderSql = "DELETE FROM orders WHERE id = ?";
        
        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement psItems = con.prepareStatement(deleteItemsSql);
             PreparedStatement psOrder = con.prepareStatement(deleteOrderSql)) {
            
            // Delete items
            psItems.setInt(1, orderId);
            psItems.executeUpdate();
            
            // Delete order
            psOrder.setInt(1, orderId);
            psOrder.executeUpdate();
            
            JOptionPane.showMessageDialog(pastOrdersDialog, 
                "Order #" + orderId + " and its items have been permanently deleted.", 
                "Deletion Success", JOptionPane.INFORMATION_MESSAGE);
            
        } catch (SQLException e) {
            showError("Database Error", "Failed to delete order #" + orderId + ": " + e.getMessage());
        }
    }


    private void showOrderEditDialog(int orderId, DefaultTableModel parentModel) {
        Map<String, Object> orderData = getOrderDetails(orderId);
        if (orderData.isEmpty()) {
            showError("Error", "Could not retrieve items for Order ID: " + orderId);
            return;
        }

        JDialog itemsDialog = new JDialog(this, "Edit Items for Order #" + orderId, true);
        itemsDialog.setSize(600, 450);
        itemsDialog.setLocationRelativeTo(this);
        itemsDialog.setLayout(new BorderLayout(10, 10));
        
        DefaultTableModel itemsModel = (DefaultTableModel) orderData.get("items");
        JTable itemsTable = new JTable(itemsModel);
        itemsTable.removeColumn(itemsTable.getColumnModel().getColumn(0)); // Hide Item ID column (index 0)
        itemsTable.setFont(UITheme.FONT_BODY);
        itemsTable.setRowHeight(25);
        
        // --- Summary Labels ---
        // These labels need to be recalculated and updated every time an item is changed/deleted
        JLabel editSubtotalLabel = new JLabel("Subtotal: ₹" + df.format(orderData.get("total_amount")));
        JLabel editGstLabel = new JLabel("GST (5%): ₹" + df.format(orderData.get("gst")));
        JLabel editTotalLabel = new JLabel("Grand Total: ₹" + df.format(orderData.get("grand_total")));
        JLabel editPaymentLabel = new JLabel("Payment Method: " + orderData.get("payment_method")); // Display payment method
        
        editSubtotalLabel.setFont(UITheme.FONT_LABEL_BOLD);
        editGstLabel.setFont(UITheme.FONT_LABEL_BOLD);
        editTotalLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        editPaymentLabel.setFont(UITheme.FONT_LABEL_BOLD);

        JPanel summaryPanel = new JPanel(new GridLayout(4, 1, 5, 5)); // Increased grid size
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        summaryPanel.add(editSubtotalLabel);
        summaryPanel.add(editGstLabel);
        summaryPanel.add(editTotalLabel);
        summaryPanel.add(editPaymentLabel); // Added payment label

        // --- Action Buttons ---
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        
        JButton updateQtyButton = new JButton("🔄 Update Qty");
        styleButton(updateQtyButton, UITheme.ACCENT_COLOR_ORANGE);
        updateQtyButton.addActionListener(e -> updatePastOrderItemQuantity(itemsTable, editSubtotalLabel, editGstLabel, editTotalLabel));

        JButton deleteItemButton = new JButton("❌ Delete Item");
        styleButton(deleteItemButton, UITheme.ACCENT_COLOR_RED);
        deleteItemButton.addActionListener(e -> deletePastOrderItem(itemsTable, editSubtotalLabel, editGstLabel, editTotalLabel));
        
        JButton printButton = new JButton("🖨️ Generate PDF Bill");
        styleButton(printButton, UITheme.PRIMARY_COLOR.darker()); 
        
        // *************************************************************
        // *** ACTION LISTENER FOR PDF GENERATION (Using PDFBox logic) ***
        // *************************************************************
        printButton.addActionListener(e -> generatePdfBill(orderId));
        // *************************************************************

        actionPanel.add(updateQtyButton);
        actionPanel.add(deleteItemButton);
        actionPanel.add(printButton); // Print button added
        
        // Layout the dialog
        JPanel topInfoPanel = new JPanel(new BorderLayout());
        topInfoPanel.add(summaryPanel, BorderLayout.NORTH);
        topInfoPanel.add(actionPanel, BorderLayout.SOUTH);
        
        itemsDialog.add(new JScrollPane(itemsTable), BorderLayout.CENTER);
        itemsDialog.add(topInfoPanel, BorderLayout.SOUTH);
        
        // This listener ensures the main order list is refreshed if any changes were made
        itemsDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent windowEvent) {
                loadPastOrdersToTable(parentModel); 
            }
        });
        
        itemsDialog.setVisible(true);
    }
    
// --- PDFBox Generation Method ---
private void generatePdfBill(int orderId) {
    Map<String, Object> orderData = getOrderDetails(orderId);
    if (orderData.isEmpty()) {
        showError("PDF Error", "Could not retrieve data for Order ID: " + orderId);
        return;
    }

    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Save Invoice PDF");
    fileChooser.setSelectedFile(new File("Invoice_#" + orderId + ".pdf"));

    int userSelection = fileChooser.showSaveDialog(this);

    if (userSelection == JFileChooser.APPROVE_OPTION) {
        File fileToSave = fileChooser.getSelectedFile();

        try (PDDocument document = new PDDocument()) {
            // Try to load a Unicode TrueType font from common Windows font paths
            PDType0Font embeddedFont = null;
            String[] classpathFonts = new String[] {"/fonts/segoeui.ttf", "/fonts/DejaVuSans.ttf", "/fonts/arialuni.ttf"};
            for (String resourcePath : classpathFonts) {
                try (java.io.InputStream is = HotelAnshumanBillingGUI.class.getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        embeddedFont = PDType0Font.load(document, is);
                        break;
                    }
                } catch (IOException ex) {
                    // try next
                }
            }

            if (embeddedFont == null) {
                String[] possibleFontPaths = new String[] {
                    "C:\\Windows\\Fonts\\segoeui.ttf",
                    "C:\\Windows\\Fonts\\arial.ttf",
                    "C:\\Windows\\Fonts\\ARIALUNI.TTF",
                    "C:\\Windows\\Fonts\\DejaVuSans.ttf"
                };
                for (String fp : possibleFontPaths) {
                    try {
                        java.io.File f = new java.io.File(fp);
                        if (f.exists()) {
                            embeddedFont = PDType0Font.load(document, f);
                            break;
                        }
                    } catch (IOException ex) {
                        // try next font
                    }
                }
            }

            if (embeddedFont == null) {
                try {
                    java.io.File winFonts = new java.io.File("C:\\Windows\\Fonts");
                    if (winFonts.exists() && winFonts.isDirectory()) {
                        java.io.File[] candidates = winFonts.listFiles((d, name) -> {
                            String ln = name.toLowerCase();
                            return ln.endsWith(".ttf") || ln.endsWith(".otf");
                        });
                        if (candidates != null) {
                            for (java.io.File cf : candidates) {
                                try {
                                    PDType0Font testFont = PDType0Font.load(document, cf);
                                    try {
                                        testFont.encode("\u20B9");
                                        embeddedFont = testFont;
                                        break;
                                    } catch (IllegalArgumentException ia) {
                                        // font does not support glyph; continue
                                    }
                                } catch (IOException ioe) {
                                    // ignore and continue
                                }
                            }
                        }
                    }
                } catch (SecurityException se) {
                    // cannot access Windows fonts folder — ignore
                }
            }

            PDPage page = new PDPage();
            document.addPage(page);

            int y = 750; // Starting Y coordinate
            final int margin = 50;
            final int tableX = margin;

            // --- Title and Header Text ---
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                org.apache.pdfbox.pdmodel.font.PDFont pdfFontToUse = embeddedFont;
                if (pdfFontToUse == null) {
                    String[] tryFonts = new String[] {"C:\\Windows\\Fonts\\segoeui.ttf", "C:\\Windows\\Fonts\\arial.ttf", "C:\\Windows\\Fonts\\DejaVuSans.ttf"};
                    for (String fp : tryFonts) {
                        try {
                            java.io.File f = new java.io.File(fp);
                            if (f.exists()) {
                                pdfFontToUse = PDType0Font.load(document, f);
                                break;
                            }
                        } catch (IOException ex) {
                            // ignore
                        }
                    }
                }
                float titleFontSize = 22;
                if (pdfFontToUse != null) contentStream.setFont(pdfFontToUse, titleFontSize);
                contentStream.newLineAtOffset(margin, y);
                String titleText = "HOTEL ANSHUMAN - INVOICE";
                if (embeddedFont == null) titleText = titleText.replace('₹', ' ');
                contentStream.showText(pdfSafe(titleText, embeddedFont != null));

                y -= 40;
                org.apache.pdfbox.pdmodel.font.PDFont bodyFont = pdfFontToUse;
                if (bodyFont != null) contentStream.setFont(bodyFont, 12);
                contentStream.newLineAtOffset(0, -40);
                String invoiceIdText = "Invoice ID: #" + orderId;
                if (embeddedFont == null) invoiceIdText = invoiceIdText.replace('₹', ' ');
                contentStream.showText(pdfSafe(invoiceIdText, embeddedFont != null));

                y -= 15;
                contentStream.newLineAtOffset(0, -15);
                String dateText = "Date: " + orderData.get("order_date");
                if (embeddedFont == null) dateText = dateText.replace('₹', ' ');
                contentStream.showText(pdfSafe(dateText, embeddedFont != null));

                y -= 15;
                contentStream.newLineAtOffset(0, -15);
                String custText = "Customer: " + orderData.get("customer_name");
                if (embeddedFont == null) custText = custText.replace('₹', ' ');
                contentStream.showText(pdfSafe(custText, embeddedFont != null));

                y -= 15;
                contentStream.newLineAtOffset(0, -15);
                String addrText = "Address: " + orderData.get("address");
                if (embeddedFont == null) addrText = addrText.replace('₹', ' ');
                contentStream.showText(pdfSafe(addrText, embeddedFont != null));
                contentStream.endText();

                // --- Table Header ---
                float[] colWidths = {250, 60, 80, 80};
                float tableWidth = colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3];
                int tableY = y - 40;

                // Draw header background
                System.out.println("Setting header background color: R=" + (70/255f) + ", G=" + (130/255f) + ", B=" + (180/255f));
                contentStream.setNonStrokingColor(70/255f, 130/255f, 180/255f);
                contentStream.addRect(tableX, tableY - 15, tableWidth, 15);
                contentStream.fill();

                // --- Header Text ---
                contentStream.beginText();
                System.out.println("Setting header text color: WHITE");
                contentStream.setNonStrokingColor(Color.WHITE);
                contentStream.setFont(bodyFont, 10);
                contentStream.newLineAtOffset(tableX + 5, tableY - 12);
                contentStream.showText(pdfSafe("ITEM", embeddedFont != null));
                contentStream.newLineAtOffset(colWidths[0], 0);
                contentStream.showText(pdfSafe("QTY", embeddedFont != null));
                contentStream.newLineAtOffset(colWidths[1], 0);
                contentStream.showText(pdfSafe("PRICE", embeddedFont != null));
                contentStream.newLineAtOffset(colWidths[2], 0);
                contentStream.showText(pdfSafe("TOTAL", embeddedFont != null));
                contentStream.endText();

                // --- Table Rows ---
                DefaultTableModel itemsModel = (DefaultTableModel) orderData.get("items");
                tableY -= 15;
                contentStream.beginText();
                contentStream.setFont(bodyFont, 10);
                System.out.println("Setting table row text color: BLACK");
                contentStream.setNonStrokingColor(Color.BLACK);

                for (int i = 0; i < itemsModel.getRowCount(); i++) {
                    String itemName = itemsModel.getValueAt(i, 1).toString();
                    String qty = itemsModel.getValueAt(i, 2).toString();
                    String price = itemsModel.getValueAt(i, 3).toString();
                    String totalItem = itemsModel.getValueAt(i, 4).toString();

                    tableY -= 15;
                    contentStream.newLineAtOffset(tableX + 5, tableY);
                    String displayItem = itemName;
                    String displayQty = qty;
                    String displayPrice = ((embeddedFont != null) ? "₹" : "Rs.") + price;
                    String displayTotal = ((embeddedFont != null) ? "₹" : "Rs.") + totalItem;

                    contentStream.showText(pdfSafe(displayItem, embeddedFont != null));
                    contentStream.newLineAtOffset(colWidths[0] - 5, 0);
                    contentStream.showText(pdfSafe(displayQty, embeddedFont != null));
                    contentStream.newLineAtOffset(colWidths[1], 0);
                    contentStream.showText(pdfSafe(displayPrice, embeddedFont != null));
                    contentStream.newLineAtOffset(colWidths[2], 0);
                    contentStream.showText(pdfSafe(displayTotal, embeddedFont != null));
                }
                contentStream.endText();

                // --- Summary Totals ---
                y = tableY - 40;
                contentStream.beginText();
                if (bodyFont != null) contentStream.setFont(bodyFont, 12);
                contentStream.newLineAtOffset(tableWidth - 100, y);
                String subtotalStr = ((embeddedFont != null) ? "₹" : "Rs.") + df.format(orderData.get("total_amount"));
                contentStream.showText(pdfSafe("Subtotal: " + subtotalStr, embeddedFont != null));

                y -= 15;
                contentStream.setFont(bodyFont, 12);
                contentStream.newLineAtOffset(0, -15);
                String gstStr = ((embeddedFont != null) ? "₹" : "Rs.") + df.format(orderData.get("gst"));
                contentStream.showText(pdfSafe("GST (5%): " + gstStr, embeddedFont != null));

                y -= 25;
                if (bodyFont != null) contentStream.setFont(bodyFont, 14);
                contentStream.newLineAtOffset(0, -25);
                String grandStr = ((embeddedFont != null) ? "₹" : "Rs.") + df.format(orderData.get("grand_total"));
                contentStream.showText(pdfSafe("GRAND TOTAL: " + grandStr, embeddedFont != null));

                y -= 20;
                contentStream.setFont(bodyFont, 10);
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText(pdfSafe("Payment Method: " + orderData.get("payment_method"), embeddedFont != null));

                y -= 40;
                contentStream.setFont(bodyFont, 10);
                contentStream.newLineAtOffset(0, -40);
                contentStream.showText(pdfSafe("Thank you for choosing Hotel Anshuman!", embeddedFont != null));
                contentStream.endText();
            }
            document.save(fileToSave);

            JOptionPane.showMessageDialog(this,
                "PDF Invoice for Order #" + orderId + " saved to:\n" + fileToSave.getAbsolutePath(),
                "PDF Generated", JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException e) {
            showError("PDF Error", "Failed to generate PDF: " + e.getMessage());
        }
    }
}   
    // --- Past Order Item Editing Logic ---
    
    private void updatePastOrderItemQuantity(JTable itemsTable, JLabel subtotalLabel, JLabel gstLabel, JLabel totalLabel) {
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow == -1) {
            showWarning("Selection Error", "Please select an item from the order details table.");
            return;
        }
        
        DefaultTableModel itemsModel = (DefaultTableModel) itemsTable.getModel();
        // Item ID is hidden but at index 0 of the model
        int itemId = (Integer) itemsModel.getValueAt(selectedRow, 0); 
        String itemName = itemsModel.getValueAt(selectedRow, 1).toString();
        double unitPrice = Double.parseDouble(itemsModel.getValueAt(selectedRow, 3).toString());

        String newQtyStr = JOptionPane.showInputDialog(itemsTable.getParent(), 
                                                    "Enter new quantity for " + itemName + ":", 
                                                    itemsModel.getValueAt(selectedRow, 2).toString());

        if (newQtyStr != null) {
            try {
                int newQty = Integer.parseInt(newQtyStr.trim());
                if (newQty < 0) throw new NumberFormatException();
                
                if (newQty == 0) {
                    // Treat as deletion if quantity is set to 0
                    int confirm = JOptionPane.showConfirmDialog(itemsTable.getParent(), "Setting quantity to zero will remove the item. Continue?", "Confirm Removal", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        deletePastOrderItemFromDB(itemId);
                        itemsModel.removeRow(selectedRow);
                    }
                } else {
                    // Update in database
                    updatePastOrderItemInDB(itemId, newQty);
                    // Update model
                    itemsModel.setValueAt(newQty, selectedRow, 2);
                    itemsModel.setValueAt(df.format(unitPrice * newQty), selectedRow, 4);
                }
                
                // Recalculate and update labels/parent order totals
                recalculateAndDisplayOrderTotals(itemsModel, subtotalLabel, gstLabel, totalLabel);
            } catch (NumberFormatException e) {
                showError("Input Error", "Invalid quantity entered. Please enter a positive whole number.");
            }
        }
    }

    private void deletePastOrderItem(JTable itemsTable, JLabel subtotalLabel, JLabel gstLabel, JLabel totalLabel) {
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow == -1) {
            showWarning("Selection Error", "Please select an item from the order details table to delete.");
            return;
        }
        
        DefaultTableModel itemsModel = (DefaultTableModel) itemsTable.getModel();
        int itemId = (Integer) itemsModel.getValueAt(selectedRow, 0);
        
        int confirm = JOptionPane.showConfirmDialog(itemsTable.getParent(), 
                                                    "Are you sure you want to delete " + itemsModel.getValueAt(selectedRow, 1) + "?", 
                                                    "Confirm Deletion", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            deletePastOrderItemFromDB(itemId);
            itemsModel.removeRow(selectedRow);
            recalculateAndDisplayOrderTotals(itemsModel, subtotalLabel, gstLabel, totalLabel);
        }
    }
    
    private void updatePastOrderItemInDB(int itemId, int newQty) {
        String sql = "UPDATE order_items SET quantity = ? WHERE id = ?";
        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, newQty);
            ps.setInt(2, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("Database Error", "Error updating item quantity: " + e.getMessage());
        }
    }
    
    private void deletePastOrderItemFromDB(int itemId) {
        String sql = "DELETE FROM order_items WHERE id = ?";
        try (Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("Database Error", "Error deleting order item: " + e.getMessage());
        }
    }
    
    private void recalculateAndDisplayOrderTotals(DefaultTableModel itemsModel, JLabel subtotalLabel, JLabel gstLabel, JLabel totalLabel) {
        double subtotal = 0;
        int orderId = -1;
        
        for (int i = 0; i < itemsModel.getRowCount(); i++) {
            // Recalculate subtotal from the updated table model
            subtotal += Double.parseDouble(itemsModel.getValueAt(i, 4).toString());
            // Item ID is a primary key of order_items, but we need the order_id (foreign key)
            // Since we don't have order_id in the table model, we must retrieve it if needed,
            // but for simplicity, we assume the dialog context is fixed to one orderId.
            // (Note: To be fully robust, you'd fetch the order_id associated with any item ID, 
            // but we rely on the context of the showOrderEditDialog).
        }
        
        // Retrieve the orderId from the dialog's title or context if needed, but for now:
        // Assume the dialog is active, so we rely on the DB update at the end of this method.

        double gst = subtotal * GST_RATE;
        double total = subtotal + gst;

        // Update dialog labels
        subtotalLabel.setText("Subtotal: ₹" + df.format(subtotal));
        gstLabel.setText("GST (5%): ₹" + df.format(gst));
        totalLabel.setText("Grand Total: ₹" + df.format(total));
        
        // Update totals in the main orders table in the database
        // Need to get the current order ID from the dialog title or a variable
        JDialog dialog = (JDialog) SwingUtilities.getWindowAncestor(subtotalLabel);
        String title = dialog.getTitle();
        if (title.contains("#")) {
            orderId = Integer.parseInt(title.substring(title.indexOf('#') + 1));
            updateOrderTotalsInDB(orderId, subtotal, gst, total);
        }
    }
    
    // --- Main Method ---

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new HotelAnshumanBillingGUI().setVisible(true);
        });
    }
}
