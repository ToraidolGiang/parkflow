package com.parkflow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Điểm vào của ứng dụng. Chỉ khung: mọi logic nghiệp vụ được viết trong giờ thi (xem docs/ke-hoach-ngay-thi.md). */
@SpringBootApplication
@EnableScheduling
public class ParkFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ParkFlowApplication.class, args);
    }
}
