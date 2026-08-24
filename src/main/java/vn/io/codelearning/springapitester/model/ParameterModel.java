package vn.io.codelearning.springapitester.model;

import java.util.Objects;

/**
 * Đại diện cho một tham số trích xuất từ Controller Method.
 */
public class ParameterModel {
    private String name;
    private ParamTypeEnum paramType;
    private String dataType;
    private String defaultValue;
    private boolean required;
    private String currentValue;
    private String description;

    public ParameterModel() {
    }

    public ParameterModel(String name, ParamTypeEnum paramType, String dataType) {
        this(name, paramType, dataType, "", false, "", "");
    }

    public ParameterModel(String name, ParamTypeEnum paramType, String dataType, String defaultValue, boolean required, String currentValue, String description) {
        this.name = name;
        this.paramType = paramType;
        this.dataType = dataType;
        this.defaultValue = defaultValue;
        this.required = required;
        this.currentValue = (currentValue != null && !currentValue.isEmpty()) ? currentValue : defaultValue;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ParamTypeEnum getParamType() {
        return paramType;
    }

    public void setParamType(ParamTypeEnum paramType) {
        this.paramType = paramType;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterModel that = (ParameterModel) o;
        return Objects.equals(name, that.name) && paramType == that.paramType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, paramType);
    }

    @Override
    public String toString() {
        return "ParameterModel{" +
                "name='" + name + '\'' +
                ", paramType=" + paramType +
                ", dataType='" + dataType + '\'' +
                ", currentValue='" + currentValue + '\'' +
                '}';
    }
}
