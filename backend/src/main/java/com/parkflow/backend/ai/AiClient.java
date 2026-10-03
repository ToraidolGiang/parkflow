package com.parkflow.backend.ai;

/** Một nhà cung cấp mô hình ngôn ngữ (Groq, Gemini). Trả về văn bản JSON thô; kiểm tra schema do {@link AiService} làm. */
public interface AiClient {
    /** Tên nguồn ghi trong câu trả lời: "groq" hoặc "gemini". */
    String source();

    /** Có khóa API và dùng được không (không có khóa thì bị bỏ qua, không báo lỗi). */
    boolean enabled();

    /** Gọi mô hình với lời nhắc hệ thống và nội dung người dùng; ném {@link AiUnavailableException} khi lỗi mạng, quá hạn chờ, hạn mức. */
    String complete(String system, String user);

    /** Nhà cung cấp tạm thời không dùng được (hết hạn mức, quá tải, quá hạn chờ): chuyển sang nhà cung cấp kế tiếp. */
    class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message) { super(message); }
    }
}
