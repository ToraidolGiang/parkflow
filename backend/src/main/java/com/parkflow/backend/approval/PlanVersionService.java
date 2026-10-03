package com.parkflow.backend.approval;

import com.parkflow.backend.common.ApiException;
import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.plan.PlanHysteresis;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PlanVersionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ParkFlowClock clock;

    public PlanVersionService(JdbcTemplate jdbc, ObjectMapper mapper, ParkFlowClock clock) {
        this.jdbc=jdbc; this.mapper=mapper; this.clock=clock;
    }

    public enum Status { draft, pending_approval, approved, auto_applied }
    public record Version(long version, LocalDate date, Status status, ObjectNode body,
            PlanHysteresis.Snapshot snapshot, OffsetDateTime createdAt) {}

    @Transactional
    public Version create(LocalDate date, ObjectNode body, PlanHysteresis.Snapshot snapshot) {
        Objects.requireNonNull(date); Objects.requireNonNull(body); Objects.requireNonNull(snapshot);
        lock(date);
        var old=latest(date);
        if(old.isPresent() && !PlanHysteresis.shouldReplace(old.get().snapshot(),snapshot)) return old.get();
        Long id=jdbc.queryForObject("""
                INSERT INTO plan_versions(target_date,status,body,snapshot,created_at)
                VALUES (?,'draft',?::jsonb,?::jsonb,?) RETURNING version
                """,Long.class,date,mapper.writeValueAsString(body),mapper.writeValueAsString(snapshot),clock.read().now());
        jdbc.update("UPDATE plan_state SET latest_version=? WHERE target_date=?",id,date);
        return find(id).orElseThrow();
    }

    public Optional<Version> find(long version) {
        return jdbc.query("SELECT * FROM plan_versions WHERE version=?",(rs,n)->new Version(
                rs.getLong("version"),rs.getObject("target_date",LocalDate.class),Status.valueOf(rs.getString("status")),
                (ObjectNode)mapper.readTree(rs.getString("body")),
                mapper.readValue(rs.getString("snapshot"),PlanHysteresis.Snapshot.class),
                rs.getObject("created_at",OffsetDateTime.class).atZoneSameInstant(ParkFlowClock.ZONE).toOffsetDateTime()),version)
                .stream().findFirst();
    }

    public Optional<Version> latest(LocalDate date) { return pointed(date,"latest_version"); }
    public Optional<Version> applied(LocalDate date) { return pointed(date,"applied_version"); }

    private Optional<Version> pointed(LocalDate date,String column) {
        var ids=jdbc.query("SELECT " + column + " FROM plan_state WHERE target_date=?",
                (rs,n)->rs.getObject(1,Long.class),date);
        return ids.isEmpty() || ids.get(0)==null ? Optional.empty() : find(ids.get(0));
    }

    private void lock(LocalDate date) {
        jdbc.update("INSERT INTO plan_state(target_date) VALUES (?) ON CONFLICT DO NOTHING",date);
        jdbc.queryForObject("SELECT target_date FROM plan_state WHERE target_date=? FOR UPDATE",LocalDate.class,date);
    }

    /** Chuyển trạng thái nội bộ; lớp duyệt phải kiểm tra phương án/khung trước khi áp dụng. */
    @Transactional
    public Version transition(long version, Status next, String approvedBy) {
        var initial=find(version).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"NO_PLAN","Không có kế hoạch"));
        lock(initial.date());
        var current=find(version).orElseThrow();
        if(latest(current.date()).orElseThrow().version()!=version) throw stale();
        boolean pending=current.status()==Status.draft && next==Status.pending_approval;
        boolean apply=(current.status()==Status.draft || current.status()==Status.pending_approval)
                && (next==Status.approved || next==Status.auto_applied);
        if(!pending && !apply) throw stale();
        if(apply && (approvedBy==null || approvedBy.isBlank())) throw new IllegalArgumentException("Approver required");
        jdbc.update("UPDATE plan_versions SET status=?, approved_by=?, approved_at=? WHERE version=?",
                next.name(),apply?approvedBy:null,apply?clock.read().now():null,version);
        if(apply) jdbc.update("UPDATE plan_state SET applied_version=? WHERE target_date=?",version,current.date());
        return find(version).orElseThrow();
    }

    private ApiException stale() { return new ApiException(HttpStatus.CONFLICT,"PLAN_STALE","Kế hoạch đã đổi hoặc đã được duyệt"); }
}