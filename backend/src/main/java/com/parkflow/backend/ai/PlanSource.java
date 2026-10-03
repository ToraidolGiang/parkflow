package com.parkflow.backend.ai;

import java.util.Optional;
import tools.jackson.databind.node.ObjectNode;

/** Kế hoạch phân luồng hiện hành (phiên bản, trạng thái, di chuyển, mở cổng sớm, phần thiếu); rỗng khi chưa có kế hoạch. */
public interface PlanSource {
    Optional<ObjectNode> current();
}
