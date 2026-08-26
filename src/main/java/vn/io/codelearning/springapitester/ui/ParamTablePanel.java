package vn.io.codelearning.springapitester.ui;

import com.intellij.ui.table.JBTable;
import vn.io.codelearning.springapitester.model.ParameterModel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ParamTablePanel extends JPanel {
    private final JBTable table;
    private final ParamTableModel tableModel;

    public ParamTablePanel(java.util.List<vn.io.codelearning.springapitester.model.ParamTypeEnum> allowedTypes) {
        setLayout(new BorderLayout());
        tableModel = new ParamTableModel(allowedTypes);
        table = new JBTable(tableModel);
        
        // Cột 0: Tên tham số (chỉ đọc)
        // Cột 1: Giá trị nhập vào (cho phép sửa)
        // Cột 2: Loại (Path/Query - chỉ đọc)
        
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        
        // ComboBox cho cột Type
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Text", "File"});
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(typeCombo));

        // Renderer cho cột Value
        table.getColumnModel().getColumn(1).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String typeStr = (String) table.getValueAt(row, 2);
                if ("File".equals(typeStr)) {
                    String text = (value != null && !value.toString().isBlank()) ? value.toString() : "Select files...";
                    return super.getTableCellRendererComponent(table, "📁 " + text, isSelected, hasFocus, row, column);
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        });

        // Editor cho cột Value
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JTextField()) {
            private com.intellij.openapi.ui.TextFieldWithBrowseButton filePicker;
            private JTextField textField;
            private boolean isFile = false;

            {
                textField = new JTextField();
                filePicker = new com.intellij.openapi.ui.TextFieldWithBrowseButton();
                filePicker.addBrowseFolderListener(
                    "Select File", "Choose a file to upload", null,
                    com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                );
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                String typeStr = (String) table.getValueAt(row, 2);
                isFile = "File".equals(typeStr);
                
                if (isFile) {
                    filePicker.setText(value != null ? value.toString() : "");
                    return filePicker;
                } else {
                    textField.setText(value != null ? value.toString() : "");
                    return textField;
                }
            }

            @Override
            public Object getCellEditorValue() {
                return isFile ? filePicker.getText() : textField.getText();
            }
        });

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        com.intellij.ui.ToolbarDecorator decorator = com.intellij.ui.ToolbarDecorator.createDecorator(table);
        decorator.setAddAction(button -> {
            ParameterModel newParam = new ParameterModel();
            newParam.setName("new_param");
            if (allowedTypes != null && !allowedTypes.isEmpty()) {
                newParam.setParamType(allowedTypes.get(0));
            } else {
                newParam.setParamType(vn.io.codelearning.springapitester.model.ParamTypeEnum.QUERY_PARAM);
            }
            newParam.setRequired(false);
            tableModel.params.add(newParam);
            tableModel.fireTableRowsInserted(tableModel.params.size() - 1, tableModel.params.size() - 1);
        });
        
        decorator.setRemoveAction(button -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < tableModel.params.size()) {
                tableModel.params.remove(selectedRow);
                tableModel.fireTableRowsDeleted(selectedRow, selectedRow);
            }
        });
        
        add(decorator.createPanel(), BorderLayout.CENTER);
    }

    public void setParameters(List<ParameterModel> params) {
        tableModel.setParams(params);
    }

    public List<ParameterModel> getParameters() {
        return tableModel.getParams();
    }

    private static class ParamTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Key", "Value", "Type"};
        private List<ParameterModel> params = new ArrayList<>();
        private final List<vn.io.codelearning.springapitester.model.ParamTypeEnum> allowedTypes;

        public ParamTableModel(List<vn.io.codelearning.springapitester.model.ParamTypeEnum> allowedTypes) {
            this.allowedTypes = allowedTypes;
        }

        public void setParams(List<ParameterModel> newParams) {
            this.params = new ArrayList<>();
            if (newParams != null) {
                for (ParameterModel p : newParams) {
                    if (allowedTypes != null && allowedTypes.contains(p.getParamType())) {
                        this.params.add(p);
                    } else if (allowedTypes == null && p.getParamType().isUserEditable()) {
                        this.params.add(p);
                    }
                }
            }
            fireTableDataChanged();
        }

        public List<ParameterModel> getParams() {
            return params;
        }

        @Override
        public int getRowCount() {
            return params.size();
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
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // Cho phép sửa Tên (0), Giá trị (1) và Loại (2) 
            if (columnIndex == 0) return true;
            if (columnIndex == 1) return true;
            if (columnIndex == 2) {
                ParameterModel p = params.get(rowIndex);
                return p.getParamType() == vn.io.codelearning.springapitester.model.ParamTypeEnum.FORM_DATA 
                    || p.getParamType() == vn.io.codelearning.springapitester.model.ParamTypeEnum.MULTIPART_FILE;
            }
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ParameterModel p = params.get(rowIndex);
            switch (columnIndex) {
                case 0: return p.getName() + (p.isRequired() ? " *" : "");
                case 1: return p.getCurrentValue() != null ? p.getCurrentValue() : "";
                case 2: 
                    if (p.getParamType() == vn.io.codelearning.springapitester.model.ParamTypeEnum.FORM_DATA) return "Text";
                    if (p.getParamType() == vn.io.codelearning.springapitester.model.ParamTypeEnum.MULTIPART_FILE) return "File";
                    return p.getParamType().name();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                String val = aValue.toString();
                if (val.endsWith(" *")) val = val.substring(0, val.length() - 2);
                params.get(rowIndex).setName(val);
                fireTableCellUpdated(rowIndex, columnIndex);
            } else if (columnIndex == 1) {
                params.get(rowIndex).setCurrentValue(aValue.toString());
                fireTableCellUpdated(rowIndex, columnIndex);
            } else if (columnIndex == 2) {
                String val = aValue.toString();
                if ("Text".equals(val)) {
                    params.get(rowIndex).setParamType(vn.io.codelearning.springapitester.model.ParamTypeEnum.FORM_DATA);
                } else if ("File".equals(val)) {
                    params.get(rowIndex).setParamType(vn.io.codelearning.springapitester.model.ParamTypeEnum.MULTIPART_FILE);
                    // Clear the current value (which might be text) when switching to File
                    params.get(rowIndex).setCurrentValue("");
                }
                fireTableCellUpdated(rowIndex, columnIndex);
                // Cần update lại cột Value để hiển thị file picker hoặc text field tương ứng
                fireTableCellUpdated(rowIndex, 1);
            }
        }
    }
}
