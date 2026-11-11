package com.example.e_hospital.management.prototype.model;
import java.util.List;
import java.util.Map;
public record RecordPage(List<Map<String,String>> items, long total, int page, int pages, Map<String,Long> counts) {}
