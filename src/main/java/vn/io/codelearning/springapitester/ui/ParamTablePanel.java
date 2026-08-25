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

        add(new JScrollPane(table), BorderLayout.CENTER);
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
            return columnIndex == 1; // Chỉ cột Value mới được sửa
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ParameterModel p = params.get(rowIndex);
            switch (columnIndex) {
                case 0: return p.getName() + (p.isRequired() ? " *" : "");
                case 1: return p.getCurrentValue() != null ? p.getCurrentValue() : "";
                case 2: return p.getParamType().name();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 1) {
                params.get(rowIndex).setCurrentValue(aValue.toString());
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}
