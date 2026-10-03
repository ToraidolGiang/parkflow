package com.parkflow.backend.sim;

import com.parkflow.backend.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SimulationControlGuard {
    private final String key;
    public SimulationControlGuard(@Value("${parkflow.sim.control-key:}") String key) { this.key = key; }
    public void check(String supplied) {
        if (key.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SIM_DISABLED", "Chưa cấu hình khóa điều khiển giả lập.");
        if (supplied == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Thiếu hoặc sai khóa X-Api-Key.");
    }
}
