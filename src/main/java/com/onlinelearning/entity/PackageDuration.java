package com.onlinelearning.entity;

public enum PackageDuration {
    ONE_MONTH("1 tháng", 1), ONE_YEAR("1 năm", 12), LIFETIME("Vĩnh viễn", null);
    private final String label;
    private final Integer months;
    PackageDuration(String label, Integer months) { this.label = label; this.months = months; }
    public String label() { return label; }
    public Integer months() { return months; }
}
