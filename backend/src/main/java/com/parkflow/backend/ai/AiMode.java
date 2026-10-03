package com.parkflow.backend.ai;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Công tắc dữ liệu mẫu (Mock): bật thì trợ lý trả lời bằng bộ dựng sẵn từ số liệu thật, không gọi mô hình; để demo không gián đoạn. */
@Component
public class AiMode {
    private final AtomicBoolean mock;

    public AiMode(@Value("${parkflow.ai.mock:false}") boolean initial) {
        this.mock = new AtomicBoolean(initial);
    }

    public boolean mock() { return mock.get(); }

    public void setMock(boolean value) { mock.set(value); }
}
