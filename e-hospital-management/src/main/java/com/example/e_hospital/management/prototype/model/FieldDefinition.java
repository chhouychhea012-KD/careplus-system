package com.example.e_hospital.management.prototype.model;

import java.util.List;

public record FieldDefinition(String key, String label, String type, boolean required, List<String> options, String source) {}
