package com.example.e_hospital.management.prototype.model;

import java.util.List;

public record ModuleDefinition(String key, String title, String singular, String description, String group,
        String icon, String prefix, List<FieldDefinition> fields, List<String> statuses, List<String> actions,
        List<String> tabs, List<String> names, String parent) {}
