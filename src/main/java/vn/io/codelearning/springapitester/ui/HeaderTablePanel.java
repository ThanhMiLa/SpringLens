package vn.io.codelearning.springapitester.ui;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import vn.io.codelearning.springapitester.model.HeaderItem;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class HeaderTablePanel extends JPanel {
    private final JBTable table;
    private final HeaderTableModel tableModel;

    public HeaderTablePanel() {
        setLayout(new BorderLayout());

        tableModel = new HeaderTableModel();
        table = new JBTable(tableModel);

        table.getColumnModel().getColumn(0).setMaxWidth(40); // Checkbox
        table.getColumnModel().getColumn(1).setPreferredWidth(150); // Key
        table.getColumnModel().getColumn(2).setPreferredWidth(250); // Value

        add(new JBScrollPane(table), BorderLayout.CENTER);

        // Toolbar (+, - buttons)
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("+");
        JButton removeBtn = new JButton("-");

        addBtn.addActionListener(e -> {
            tableModel.addRow(new HeaderItem("New-Header", ""));
        });

        removeBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                tableModel.removeRow(selectedRow);
            }
        });

        toolbar.add(addBtn);
        toolbar.add(removeBtn);
        add(toolbar, BorderLayout.SOUTH);
    }

    public void setHeaders(List<HeaderItem> headers) {
        tableModel.setHeaders(headers);
    }

    public List<HeaderItem> getHeaders() {
        return tableModel.getHeaders();
    }

    private static class HeaderTableModel extends AbstractTableModel {
        private final String[] columnNames = {"", "Key", "Value"};
        private List<HeaderItem> headers = new ArrayList<>();

        public void setHeaders(List<HeaderItem> newHeaders) {
            this.headers = new ArrayList<>();
            if (newHeaders != null) {
                this.headers.addAll(newHeaders);
            }
            // Mặc định luôn có vài dòng trống nếu rỗng
            if (this.headers.isEmpty()) {
                this.headers.add(new HeaderItem("Content-Type", "application/json", true));
                this.headers.add(new HeaderItem("Accept", "*/*", true));
            }
            fireTableDataChanged();
        }

        public List<HeaderItem> getHeaders() {
            return headers;
        }

        public void addRow(HeaderItem item) {
            headers.add(item);
            fireTableRowsInserted(headers.size() - 1, headers.size() - 1);
        }

        public void removeRow(int row) {
            headers.remove(row);
            fireTableRowsDeleted(row, row);
        }

        @Override
        public int getRowCount() {
            return headers.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Boolean.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true; // Tất cả đều sửa được
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HeaderItem h = headers.get(rowIndex);
            switch (columnIndex) {
                case 0: return h.isEnabled();
                case 1: return h.getKey();
                case 2: return h.getValue();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            HeaderItem h = headers.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    h.setEnabled((Boolean) aValue);
                    break;
                case 1:
                    h.setKey(aValue.toString());
                    break;
                case 2:
                    h.setValue(aValue.toString());
                    break;
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
