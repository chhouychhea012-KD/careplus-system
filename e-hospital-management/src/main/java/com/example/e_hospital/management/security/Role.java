package com.example.e_hospital.management.security;

public enum Role {
    ADMIN, DOCTOR, NURSE, RECEPTIONIST, PHARMACIST, LAB_TECHNICIAN,
    RADIOLOGIST, ACCOUNTANT, INVENTORY_MANAGER;

    public java.util.Set<Permission> permissions() {
        return this == ADMIN ? java.util.Set.of(Permission.ACCOUNT_SELF, Permission.USERS_MANAGE, Permission.AUDIT_VIEW)
            : java.util.Set.of(Permission.ACCOUNT_SELF);
    }
}
