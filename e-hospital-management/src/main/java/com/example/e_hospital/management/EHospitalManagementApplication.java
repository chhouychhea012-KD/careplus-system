package com.example.e_hospital.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.e_hospital.management.prototype")
public class EHospitalManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(EHospitalManagementApplication.class, args);
	}
}
